package com.roninai.app.session

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class WorkspaceState(
    val sessionId: Long? = null,
    val folderUri: Uri? = null,
    val folderPath: String? = null,
    val folderName: String? = null,
    val isAccessible: Boolean = false,
    val error: String? = null
)

class FolderWorkspaceManager(private val context: Context) {

    private val _state = MutableStateFlow(WorkspaceState())
    val state: StateFlow<WorkspaceState> = _state.asStateFlow()

    private val persistableUris = mutableMapOf<Long, Uri>()
    private val cachedPaths = mutableMapOf<Long, String>()

    suspend fun assignFolder(
        sessionId: Long,
        folderUri: Uri,
        takePermission: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (takePermission) {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    context.contentResolver.takePersistableUriPermission(folderUri, takeFlags)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Could not take persistable permission: ${e.message}")
                }
            }

            val path = resolvePathFromUri(folderUri)

            persistableUris[sessionId] = folderUri
            cachedPaths[sessionId] = path

            _state.value = _state.value.copy(
                sessionId = sessionId,
                folderUri = folderUri,
                folderPath = path,
                folderName = extractFolderName(folderUri, path),
                isAccessible = true,
                error = null
            )

            Log.i(TAG, "Workspace assigned: session=$sessionId, path=$path")
            Result.success(path)
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = e.message)
            Result.failure(e)
        }
    }

    suspend fun removeFolder(sessionId: Long) {
        persistableUris.remove(sessionId)
        cachedPaths.remove(sessionId)

        if (_state.value.sessionId == sessionId) {
            _state.value = WorkspaceState()
        }
    }

    fun getWorkspacePath(sessionId: Long): String? = cachedPaths[sessionId]

    fun getWorkspaceUri(sessionId: Long): Uri? = persistableUris[sessionId]

    fun getAllWorkspaces(): Map<Long, String> = cachedPaths.toMap()

    suspend fun listFiles(sessionId: Long, subPath: String = ""): Result<List<WorkspaceFile>> =
        withContext(Dispatchers.IO) {
            try {
                val basePath = cachedPaths[sessionId]
                    ?: return@withContext Result.failure(Exception("No workspace assigned"))

                val targetDir = if (subPath.isEmpty()) {
                    File(basePath)
                } else {
                    File(basePath, subPath)
                }

                if (!targetDir.exists() || !targetDir.isDirectory) {
                    return@withContext Result.failure(Exception("Directory not found: $subPath"))
                }

                val files = targetDir.listFiles()?.map { file ->
                    WorkspaceFile(
                        name = file.name,
                        path = file.absolutePath,
                        relativePath = file.absolutePath.removePrefix(basePath).trimStart('/'),
                        isDirectory = file.isDirectory,
                        size = file.length(),
                        lastModified = file.lastModified()
                    )
                }?.sortedWith(compareByDescending<WorkspaceFile> { it.isDirectory }.thenBy { it.name })
                    ?: emptyList()

                Result.success(files)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun readFile(sessionId: Long, relativePath: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val basePath = cachedPaths[sessionId]
                    ?: return@withContext Result.failure(Exception("No workspace assigned"))

                val file = File(basePath, relativePath)
                if (!file.exists()) {
                    return@withContext Result.failure(Exception("File not found: $relativePath"))
                }

                if (file.length() > MAX_READ_SIZE) {
                    return@withContext Result.failure(Exception("File too large: ${file.length()} bytes"))
                }

                Result.success(file.readText())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun writeFile(sessionId: Long, relativePath: String, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val basePath = cachedPaths[sessionId]
                    ?: return@withContext Result.failure(Exception("No workspace assigned"))

                val file = File(basePath, relativePath)
                file.parentFile?.mkdirs()
                file.writeText(content)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun deleteFile(sessionId: Long, relativePath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val basePath = cachedPaths[sessionId]
                    ?: return@withContext Result.failure(Exception("No workspace assigned"))

                val file = File(basePath, relativePath)
                if (!file.exists()) {
                    return@withContext Result.failure(Exception("File not found"))
                }

                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getDiskUsage(sessionId: Long): Result<DiskUsage> =
        withContext(Dispatchers.IO) {
            try {
                val basePath = cachedPaths[sessionId]
                    ?: return@withContext Result.failure(Exception("No workspace assigned"))

                val dir = File(basePath)
                if (!dir.exists()) {
                    return@withContext Result.failure(Exception("Directory not found"))
                }

                var totalSize = 0L
                var fileCount = 0
                var dirCount = 0

                dir.walkTopDown().forEach { file ->
                    if (file.isDirectory && file != dir) {
                        dirCount++
                    } else if (file.isFile) {
                        totalSize += file.length()
                        fileCount++
                    }
                }

                Result.success(
                    DiskUsage(
                        totalSizeBytes = totalSize,
                        fileCount = fileCount,
                        directoryCount = dirCount
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun resolvePathFromUri(uri: Uri): String {
        // Try to resolve actual path from content URI
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex("_display_name")
                if (displayNameIndex >= 0) {
                    val displayName = it.getString(displayNameIndex)
                    // For SAF URIs, we store the URI string and use virtual path
                    return "${context.filesDir}/workspaces/$displayName"
                }
            }
        }

        // Fallback: try document path
        val docId = uri.lastPathSegment ?: "unknown"
        val path = "${context.filesDir}/workspaces/$docId"
        File(path).mkdirs()
        return path
    }

    private fun extractFolderName(uri: Uri, path: String): String {
        return uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast(':')
            ?: File(path).name
            ?: "Workspace"
    }

    companion object {
        private const val TAG = "FolderWorkspaceManager"
        private const val MAX_READ_SIZE = 1024 * 1024 // 1MB

        @Volatile
        private var INSTANCE: FolderWorkspaceManager? = null

        fun getInstance(context: Context): FolderWorkspaceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FolderWorkspaceManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}

data class WorkspaceFile(
    val name: String,
    val path: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

data class DiskUsage(
    val totalSizeBytes: Long,
    val fileCount: Int,
    val directoryCount: Int
) {
    val totalSizeFormatted: String
        get() {
            val kb = totalSizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> "%.1f GB".format(gb)
                mb >= 1.0 -> "%.1f MB".format(mb)
                kb >= 1.0 -> "%.1f KB".format(kb)
                else -> "$totalSizeBytes B"
            }
        }
}

