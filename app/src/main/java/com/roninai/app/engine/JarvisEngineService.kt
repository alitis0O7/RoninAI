package com.roninai.app.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.roninai.app.MainActivity
import com.roninai.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class EngineState {
    IDLE,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}

data class EngineInfo(
    val state: EngineState = EngineState.IDLE,
    val runnerReady: Boolean = false,
    val activeSessions: Int = 0,
    val uptime: Long = 0L,
    val errorMessage: String? = null
)

class JarvisEngineService : Service() {

    private val binder = EngineBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var wakeLock: PowerManager.WakeLock
    private var startTime: Long = 0L

    private val _engineState = MutableStateFlow(EngineInfo())
    val engineState: StateFlow<EngineInfo> = _engineState.asStateFlow()

    private var monitorJob: Job? = null

    inner class EngineBinder : Binder() {
        fun getService(): JarvisEngineService = this@JarvisEngineService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        _engineState.value = _engineState.value.copy(state = EngineState.STARTING)
        startForeground(NOTIFICATION_ID, createNotification("Starting engine…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val workDir = intent.getStringExtra(EXTRA_WORK_DIR)
                startEngine(workDir)
            }
            ACTION_STOP -> stopEngine()
            ACTION_UPDATE_NOTIFICATION -> {
                val msg = intent.getStringExtra(EXTRA_NOTIFICATION_MSG) ?: "Engine Active"
                updateNotification(msg)
            }
        }
        return START_STICKY
    }

    fun startEngine(workDir: String? = null) {
        serviceScope.launch {
            try {
                startTime = System.currentTimeMillis()
                _engineState.value = _engineState.value.copy(state = EngineState.STARTING)

                val runner = OpenCodeRunner.getInstance(this@JarvisEngineService)
                val success = runner.start(workDir)

                if (success) {
                    _engineState.value = _engineState.value.copy(
                        state = EngineState.RUNNING,
                        runnerReady = true,
                        errorMessage = null
                    )
                    updateNotification("Engine Active — Port ${runner.state.value.port}")
                    startUptimeMonitor()
                } else {
                    _engineState.value = _engineState.value.copy(
                        state = EngineState.ERROR,
                        errorMessage = runner.state.value.errorMessage ?: "Unknown error"
                    )
                    updateNotification("Engine Error — ${runner.state.value.errorMessage}")
                }
            } catch (e: Exception) {
                _engineState.value = _engineState.value.copy(
                    state = EngineState.ERROR,
                    errorMessage = e.message
                )
            }
        }
    }

    fun stopEngine() {
        serviceScope.launch {
            _engineState.value = _engineState.value.copy(state = EngineState.STOPPING)
            monitorJob?.cancel()

            OpenCodeRunner.getInstance(this@JarvisEngineService).stop()

                _engineState.value = EngineInfo(state = EngineState.IDLE)
            updateNotification("Engine Stopped")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    fun getRunner(): OpenCodeRunner = OpenCodeRunner.getInstance(this)

    fun getApiBase(): String = getRunner().getBaseUrl()

    private fun startUptimeMonitor() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (true) {
                delay(5000L)
                val runner = OpenCodeRunner.getInstance(this@JarvisEngineService)
                _engineState.value = _engineState.value.copy(
                    uptime = System.currentTimeMillis() - startTime,
                    runnerReady = runner.isRunning()
                )

                if (!runner.isRunning() && _engineState.value.state == EngineState.RUNNING) {
                    _engineState.value = _engineState.value.copy(
                        state = EngineState.ERROR,
                        errorMessage = "Runner process died"
                    )
                    updateNotification("Engine Process Lost")
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.engine_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "RoninAI background engine status"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RoninAI")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "roninai:engine_wakelock"
        ).apply {
            acquire(24 * 60 * 60 * 1000L) // 24 hours max
        }
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        OpenCodeRunner.getInstance(this).stop()
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "JarvisEngineService"
        private const val CHANNEL_ID = "roninai_engine"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.roninai.app.engine.START"
        const val ACTION_STOP = "com.roninai.app.engine.STOP"
        const val ACTION_UPDATE_NOTIFICATION = "com.roninai.app.engine.UPDATE_NOTIFICATION"
        const val EXTRA_WORK_DIR = "work_dir"
        const val EXTRA_NOTIFICATION_MSG = "notification_msg"

        fun start(context: Context, workDir: String? = null) {
            val intent = Intent(context, JarvisEngineService::class.java).apply {
                action = ACTION_START
                workDir?.let { putExtra(EXTRA_WORK_DIR, it) }
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, JarvisEngineService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun updateNotification(context: Context, message: String) {
            val intent = Intent(context, JarvisEngineService::class.java).apply {
                action = ACTION_UPDATE_NOTIFICATION
                putExtra(EXTRA_NOTIFICATION_MSG, message)
            }
            context.startService(intent)
        }
    }
}
