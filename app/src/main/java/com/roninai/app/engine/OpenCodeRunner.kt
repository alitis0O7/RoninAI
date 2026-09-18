package com.roninai.app.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

enum class RunnerState {
    IDLE,
    EXTRACTING,
    STARTING,
    HEALTH_CHECKING,
    RUNNING,
    STOPPED,
    ERROR
}

data class RunnerInfo(
    val state: RunnerState = RunnerState.IDLE,
    val pid: Int? = null,
    val port: Int = 8080,
    val host: String = "127.0.0.1",
    val errorMessage: String? = null,
    val workspacePath: String? = null
) {
    val baseUrl: String get() = "http://$host:$port"
    val isReady: Boolean get() = state == RunnerState.RUNNING
}

class OpenCodeRunner(private val context: Context) {

    private val _state = MutableStateFlow(RunnerInfo())
    val state: StateFlow<RunnerInfo> = _state.asStateFlow()

    private var process: Process? = null
    private val processScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val binaryDir: File by lazy {
        File(context.filesDir, "bin").also { it.mkdirs() }
    }

    private val homeDir: File by lazy {
        File(context.filesDir, "opencode_home").also { it.mkdirs() }
    }

    private val binaryFile: File by lazy {
        File(binaryDir, "opencode")
    }

    suspend fun start(workDir: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            if (_state.value.state == RunnerState.RUNNING) {
                return@withContext true
            }

            _state.value = _state.value.copy(state = RunnerState.EXTRACTING)

            val extracted = extractBinary()
            if (!extracted) {
                _state.value = _state.value.copy(
                    state = RunnerState.ERROR,
                    errorMessage = "Failed to extract OpenCode binary"
                )
                return@withContext false
            }

            _state.value = _state.value.copy(state = RunnerState.STARTING)

            val workPath = workDir ?: context.filesDir.absolutePath
            val workingDir = File(workPath).also { it.mkdirs() }

            val pb = ProcessBuilder(
                binaryFile.absolutePath,
                "serve",
                "--port", _state.value.port.toString(),
                "--host", _state.value.host
            )
            pb.directory(workingDir)
            pb.environment()["HOME"] = homeDir.absolutePath
            pb.environment()["PATH"] = System.getenv("PATH") ?: ""
            pb.redirectErrorStream(true)

            val logFile = File(context.filesDir, "opencode.log")
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))

            process = pb.start()

            _state.value = _state.value.copy(
                pid = process?.let { getPid(it) }
            )

            val healthOk = performHealthCheck()

            if (healthOk) {
                _state.value = _state.value.copy(
                    state = RunnerState.RUNNING,
                    workspacePath = workPath
                )
                startProcessMonitor()
                return@withContext true
            } else {
                stop()
                _state.value = _state.value.copy(
                    state = RunnerState.ERROR,
                    errorMessage = "Health check failed after startup"
                )
                return@withContext false
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                state = RunnerState.ERROR,
                errorMessage = e.message ?: "Unknown error"
            )
            return@withContext false
        }
    }

    private fun extractBinary(): Boolean {
        return try {
            val assetManager = context.assets
            val assetFiles = assetManager.list("binaries") ?: emptyArray()

            if (!assetFiles.contains("opencode-arm64")) {
                android.util.Log.w(TAG, "No opencode-arm64 binary found in assets, using fallback mode")
                createFallbackScript()
                return true
            }

            val inputStream = assetManager.open("binaries/opencode-arm64")
            binaryFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()

            binaryFile.setExecutable(true, false)
            android.util.Log.i(TAG, "Binary extracted to ${binaryFile.absolutePath}")
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Binary extraction failed", e)
            createFallbackScript()
        }
    }

    private fun createFallbackScript(): Boolean {
        return try {
            binaryFile.writeText(
                """
                #!/system/bin/sh
                echo '{"error": "OpenCode binary not available for this architecture"}'
                exit 1
                """.trimIndent()
            )
            binaryFile.setExecutable(true, false)
            android.util.Log.w(TAG, "Created fallback script - no native binary available")
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun performHealthCheck(): Boolean {
        _state.value = _state.value.copy(state = RunnerState.HEALTH_CHECKING)

        val maxRetries = 30
        val retryDelayMs = 500L

        for (attempt in 1..maxRetries) {
            try {
                val request = Request.Builder()
                    .url("${_state.value.baseUrl}/health")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                val code = response.code
                response.close()

                if (code == 200) {
                    android.util.Log.i(TAG, "Health check passed on attempt $attempt")
                    return true
                }

                android.util.Log.d(TAG, "Health check attempt $attempt returned $code")
            } catch (e: Exception) {
                android.util.Log.d(TAG, "Health check attempt $attempt failed: ${e.message}")
            }

            Thread.sleep(retryDelayMs)
        }

        return false
    }

    private fun startProcessMonitor() {
        Thread {
            try {
                process?.waitFor()
                android.util.Log.w(TAG, "Process exited unexpectedly")
                _state.value = _state.value.copy(
                    state = RunnerState.STOPPED,
                    pid = null
                )
            } catch (e: InterruptedException) {
                android.util.Log.d(TAG, "Process monitor interrupted")
            }
        }.start()
    }

    fun stop() {
        try {
            process?.destroyForcibly()
            process = null
            _state.value = _state.value.copy(
                state = RunnerState.STOPPED,
                pid = null
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error stopping process", e)
        }
    }

    fun getBaseUrl(): String = _state.value.baseUrl

    fun isRunning(): Boolean = _state.value.state == RunnerState.RUNNING

    private fun getPid(process: Process): Int {
        return try {
            val pidField = process.javaClass.getDeclaredField("pid")
            pidField.isAccessible = true
            pidField.getInt(process)
        } catch (e: Exception) {
            -1
        }
    }

    fun cleanup() {
        stop()
        processScope.cancel()
    }

    companion object {
        private const val TAG = "OpenCodeRunner"

        @Volatile
        private var INSTANCE: OpenCodeRunner? = null

        fun getInstance(context: Context): OpenCodeRunner {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OpenCodeRunner(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private fun kotlinx.coroutines.CoroutineScope.cancel() {
        // Extension to cancel
    }
}
