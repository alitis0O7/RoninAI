package com.roninai.app

import android.app.Application
import com.roninai.app.data.RoninDatabase
import com.roninai.app.data.repository.SessionRepository
import com.roninai.app.engine.EngineInfo
import com.roninai.app.engine.EngineState
import com.roninai.app.engine.JarvisEngineService
import com.roninai.app.engine.OpenCodeRunner
import com.roninai.app.session.FolderWorkspaceManager
import com.roninai.app.session.SessionManager
import com.roninai.app.voice.VoiceRealtimeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RoninApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: RoninDatabase
        private set
    lateinit var sessionRepository: SessionRepository
        private set
    lateinit var sessionManager: SessionManager
        private set
    lateinit var workspaceManager: FolderWorkspaceManager
        private set
    lateinit var voiceManager: VoiceRealtimeManager
        private set

    private val _engineStateFlow = MutableStateFlow(EngineInfo())
    val engineStateFlow: StateFlow<EngineInfo> = _engineStateFlow.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = RoninDatabase.getInstance(this)
        sessionRepository = SessionRepository(database.sessionDao(), database.messageDao())
        sessionManager = SessionManager(this, database.sessionDao(), database.messageDao())
        workspaceManager = FolderWorkspaceManager.getInstance(this)
        voiceManager = VoiceRealtimeManager.getInstance(this)

        applicationScope.launch {
            val sessionCount = sessionRepository.getSessionCount()
            if (sessionCount == 0) {
                val defaultSessionId = sessionRepository.createSession(
                    title = "Welcome to RoninAI",
                    workspacePath = null
                )
                sessionManager.switchSession(defaultSessionId)
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        voiceManager.cleanup()
        OpenCodeRunner.getInstance(this).cleanup()
        applicationScope.cancel()
    }

    companion object {
        @Volatile
        private var instance: RoninApplication? = null

        fun get(): RoninApplication {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }
}
