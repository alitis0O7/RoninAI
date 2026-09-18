package com.roninai.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import com.roninai.app.data.entity.MessageEntity
import com.roninai.app.data.entity.MessageRole
import com.roninai.app.engine.JarvisApiClient
import com.roninai.app.engine.JarvisEngineService
import com.roninai.app.engine.OpenCodeRunner
import com.roninai.app.ui.navigation.RoninNavGraph
import com.roninai.app.ui.screens.DiffViewer
import com.roninai.app.ui.screens.SettingsScreen
import com.roninai.app.ui.screens.TerminalEntry
import com.roninai.app.ui.screens.TerminalInspector
import com.roninai.app.ui.theme.RoninTheme
import com.roninai.app.voice.VoiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var engineService: JarvisEngineService? = null
    private var serviceBound = false
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentSessionId = mutableLongStateOf(1L)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as JarvisEngineService.EngineBinder
            engineService = binder.getService()
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            engineService = null
            serviceBound = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "Some permissions denied - limited functionality", Toast.LENGTH_LONG).show()
        }
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { handleFolderPicked(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()

        startEngineService()

        setContent {
            RoninTheme {
                val app = remember { RoninApplication.get() }
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()

                val engineState by app.engineStateFlow.collectAsState()
                val sessionState by app.sessionManager.state.collectAsState()
                val voiceState by app.voiceManager.state.collectAsState()
                val sessions by app.sessionRepository.getAllSessions().collectAsState(initial = emptyList())

                var messages by remember { mutableStateOf<List<MessageEntity>>(emptyList()) }
                val terminalEntries = remember { mutableStateListOf<TerminalEntry>() }
                val sessionId by currentSessionId

                // Initialize
                LaunchedEffect(Unit) {
                    app.voiceManager.initialize()

                    val runner = OpenCodeRunner.getInstance(applicationContext)
                    val success = runner.start()
                    if (success) {
                        val client = JarvisApiClient(runner.getBaseUrl())
                        app.sessionManager.setApiClient(client)
                    }

                    val sessionCount = app.sessionRepository.getSessionCount()
                    if (sessionCount == 0) {
                        val id = app.sessionRepository.createSession(title = "Welcome to RoninAI")
                        currentSessionId.longValue = id
                    }
                }

                // Load messages when session changes
                LaunchedEffect(sessionId) {
                    withContext(Dispatchers.IO) {
                        messages = app.sessionManager.getMessages(sessionId)
                    }
                }

                RoninNavGraph(
                    navController = navController,
                    chatScreenContent = { sid ->
                        var localMessages by remember { mutableStateOf<List<MessageEntity>>(emptyList()) }
                        val localSessionId = sid

                        LaunchedEffect(localSessionId) {
                            currentSessionId.longValue = localSessionId
                            withContext(Dispatchers.IO) {
                                localMessages = app.sessionManager.getMessages(localSessionId)
                            }
                        }

                        val currentWorkspace = remember(localSessionId) {
                            app.workspaceManager.getWorkspacePath(localSessionId)
                        }

                        com.roninai.app.ui.screens.ChatScreen(
                            sessionId = localSessionId,
                            messages = localMessages,
                            isProcessing = sessionState.isProcessing,
                            streamingContent = sessionState.streamingContent,
                            voiceAmplitude = voiceState.amplitude,
                            isVoiceListening = voiceState.state == VoiceState.LISTENING,
                            voiceState = voiceState.state.name,
                            workspacePath = currentWorkspace,
                            onSendMessage = { text ->
                                scope.launch {
                                    val result = app.sessionManager.sendMessage(
                                        content = text,
                                        sessionId = localSessionId,
                                        workDir = currentWorkspace
                                    )
                                    result.onSuccess {
                                        withContext(Dispatchers.IO) {
                                            localMessages = app.sessionManager.getMessages(localSessionId)
                                        }
                                    }.onFailure { e ->
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Error: ${e.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            onSendImage = { uri ->
                                scope.launch {
                                    try {
                                        val inputStream = contentResolver.openInputStream(uri)
                                        val bytes = inputStream?.readBytes()
                                        inputStream?.close()

                                        if (bytes != null) {
                                            val base64 = android.util.Base64.encodeToString(
                                                bytes,
                                                android.util.Base64.NO_WRAP
                                            )
                                            val result = app.sessionManager.sendMessage(
                                                content = "Please analyze this image",
                                                sessionId = localSessionId,
                                                imageBase64 = base64,
                                                workDir = currentWorkspace
                                            )
                                            result.onSuccess {
                                                withContext(Dispatchers.IO) {
                                                    localMessages = app.sessionManager.getMessages(localSessionId)
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Image error: ${e.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            onVoiceToggle = {
                                if (app.voiceManager.isListening()) {
                                    app.voiceManager.stopListening()
                                } else {
                                    app.voiceManager.setVoiceResultListener { text ->
                                        scope.launch {
                                            val result = app.sessionManager.sendMessage(
                                                content = text,
                                                sessionId = localSessionId,
                                                workDir = currentWorkspace
                                            )
                                            result.onSuccess {
                                                withContext(Dispatchers.IO) {
                                                    localMessages = app.sessionManager.getMessages(localSessionId)
                                                }
                                                val latestMsgs = app.sessionManager.getMessages(localSessionId)
                                                val lastAssistant = latestMsgs.lastOrNull {
                                                    it.role == MessageRole.ASSISTANT
                                                }
                                                lastAssistant?.let { msg ->
                                                    app.voiceManager.speak(msg.content)
                                                }
                                            }
                                        }
                                    }
                                    app.voiceManager.startListening()
                                }
                            },
                            onOpenDiff = { old, new ->
                                navController.navigate("diff?old=${Uri.encode(old)}&new=${Uri.encode(new)}&file=change")
                            },
                            onOpenTerminal = {
                                navController.navigate("terminal")
                            },
                            onAssignFolder = {
                                folderPickerLauncher.launch(null)
                            },
                            onOpenSettings = {
                                navController.navigate("settings")
                            },
                            onNewSession = {
                                scope.launch {
                                    val newId = app.sessionManager.createNewSession(title = "New Session")
                                    currentSessionId.longValue = newId
                                    navController.navigate(com.roninai.app.ui.navigation.Routes.chat(newId)) {
                                        popUpTo(0)
                                    }
                                }
                            }
                        )
                    },
                    diffScreenContent = { old, new, file ->
                        DiffViewer(
                            oldContent = Uri.decode(old),
                            newContent = Uri.decode(new),
                            fileName = file,
                            onBack = { navController.popBackStack() }
                        )
                    },
                    terminalScreenContent = {
                        TerminalInspector(
                            entries = terminalEntries.toList(),
                            onRefresh = { },
                            onBack = { navController.popBackStack() }
                        )
                    },
                    settingsScreenContent = {
                        SettingsScreen(
                            engineInfo = engineState,
                            sessions = sessions,
                            onBack = { navController.popBackStack() },
                            onClearSession = { sid ->
                                scope.launch {
                                    app.sessionManager.deleteSession(sid)
                                    if (currentSessionId.longValue == sid) {
                                        val newId = app.sessionManager.createNewSession()
                                        currentSessionId.longValue = newId
                                    }
                                }
                            },
                            onSessionClick = { sid ->
                                currentSessionId.longValue = sid
                                navController.navigate(com.roninai.app.ui.navigation.Routes.chat(sid)) {
                                    popUpTo(0)
                                }
                            }
                        )
                    }
                )
            }
        }
    }

    private fun handleFolderPicked(uri: Uri) {
        val app = RoninApplication.get()
        activityScope.launch {
            val result = app.workspaceManager.assignFolder(
                sessionId = currentSessionId.longValue,
                folderUri = uri
            )
            result.onSuccess { path ->
                app.sessionManager.setWorkspaceForSession(currentSessionId.longValue, path)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Workspace: ${path.substringAfterLast('/')}", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startEngineService() {
        val intent = Intent(this, JarvisEngineService::class.java).apply {
            action = JarvisEngineService.ACTION_START
        }
        startForegroundService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun requestPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        activityScope.cancel()
    }
}
