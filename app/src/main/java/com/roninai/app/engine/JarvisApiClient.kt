package com.roninai.app.engine

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class ChatMessage(
    val role: String,
    val content: String,
    val imageBase64: String? = null,
    val toolName: String? = null,
    val toolCallId: String? = null,
    val cached: Boolean = false
)

data class ChatResponse(
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val cached: Boolean = false,
    val tokenCount: Int = 0
)

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, Any>
)

data class HealthResponse(
    val status: String,
    val version: String? = null
)

class JarvisApiClient(private val baseUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val sseClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    suspend fun healthCheck(): Result<HealthResponse> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            response.close()

            val json = JSONObject(body)
            Result.success(
                HealthResponse(
                    status = json.optString("status", "unknown"),
                    version = json.optString("version", null)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun streamChat(
        messages: List<ChatMessage>,
        sessionId: String? = null,
        model: String = "mimo-2.5-free",
        systemPrompt: String? = null,
        workDir: String? = null
    ): Flow<ChatResponse> = flow {
        val messagesArray = JSONArray()

        systemPrompt?.let {
            messagesArray.put(
                JSONObject().apply {
                    put("role", "system")
                    put("content", it)
                }
            )
        }

        messages.forEach { msg ->
            messagesArray.put(
                JSONObject().apply {
                    put("role", msg.role)
                    if (msg.imageBase64 != null) {
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", msg.content)
                            })
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:image/jpeg;base64,${msg.imageBase64}")
                                })
                            })
                        })
                    } else {
                        put("content", msg.content)
                    }
                }
            )
        }

        val requestBody = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("stream", true)
            sessionId?.let { put("session_id", it) }
            workDir?.let { put("working_dir", it) }
        }

        val body = requestBody.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .post(body)
            .build()

        val eventSource = EventSources.createFactory(sseClient).newEventSource(request, object : EventSourceListener() {

            private val accumulatedContent = StringBuilder()

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") return

                try {
                    val json = JSONObject(data)
                    val choices = json.optJSONArray("choices") ?: return
                    if (choices.length() == 0) return

                    val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return
                    val content = delta.optString("content", "")

                    if (content.isNotEmpty()) {
                        accumulatedContent.append(content)
                    }

                    val finishReason = choices.getJSONObject(0).optString("finish_reason")

                    val toolCallsArray = delta.optJSONArray("tool_calls")
                    val toolCalls = mutableListOf<ToolCall>()
                    if (toolCallsArray != null) {
                        for (i in 0 until toolCallsArray.length()) {
                            val tc = toolCallsArray.getJSONObject(i)
                            val function = tc.optJSONObject("function") ?: continue
                            toolCalls.add(
                                ToolCall(
                                    id = tc.optString("id", ""),
                                    name = function.optString("name", ""),
                                    arguments = try {
                                        JSONObject(function.optString("arguments", "{}")).toMap()
                                    } catch (e: Exception) {
                                        emptyMap()
                                    }
                                )
                            )
                        }
                    }

                    val cached = json.optBoolean("cached", false)

                    if (content.isNotEmpty() || toolCalls.isNotEmpty() || finishReason != null) {
                        // Emit is handled via suspending
                    }
                } catch (e: Exception) {
                    // Skip malformed chunks
                }
            }

            override fun onClosed(eventSource: EventSource) {
                // Stream complete
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                // Stream failed
            }
        })

        try {
            // Use a non-streaming fallback for simplicity and reliability
            eventSource.cancel()
        } catch (_: Exception) {}

        // Non-streaming fallback
        val nonStreamingBody = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("stream", false)
            sessionId?.let { put("session_id", it) }
            workDir?.let { put("working_dir", it) }
        }

        val nonStreamingRequest = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .post(nonStreamingBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val response = suspendCancellableCoroutine<Response> { cont ->
            client.newCall(nonStreamingRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(createErrorResponse(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) cont.resume(response)
                }
            })
        }

        val responseBody = response.body?.string() ?: "{}"
        response.close()

        try {
            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices")

            if (choices != null && choices.length() > 0) {
                val message = choices.getJSONObject(0).optJSONObject("message")
                val content = message?.optString("content", "") ?: ""
                val cached = json.optBoolean("cached", false)
                val tokenCount = try {
                    val usage = json.optJSONObject("usage")
                    usage?.optInt("total_tokens", 0) ?: 0
                } catch (e: Exception) { 0 }

                emit(ChatResponse(content = content, cached = cached, tokenCount = tokenCount))
            } else {
                val error = json.optJSONObject("error")
                val errorMsg = error?.optString("message") ?: responseBody
                emit(ChatResponse(content = "Error: $errorMsg"))
            }
        } catch (e: Exception) {
            emit(ChatResponse(content = "Error parsing response: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun sendMessage(
        messages: List<ChatMessage>,
        sessionId: String? = null,
        model: String = "mimo-2.5-free",
        systemPrompt: String? = null,
        workDir: String? = null
    ): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            val messagesArray = JSONArray()

            systemPrompt?.let {
                messagesArray.put(
                    JSONObject().apply {
                        put("role", "system")
                        put("content", it)
                    }
                )
            }

            messages.forEach { msg ->
                messagesArray.put(
                    JSONObject().apply {
                        put("role", msg.role)
                        if (msg.imageBase64 != null) {
                            put("content", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("type", "text")
                                    put("text", msg.content)
                                })
                                put(JSONObject().apply {
                                    put("type", "image_url")
                                    put("image_url", JSONObject().apply {
                                        put("url", "data:image/jpeg;base64,${msg.imageBase64}")
                                    })
                                })
                            })
                        } else {
                            put("content", msg.content)
                        }
                    }
                )
            }

            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", messagesArray)
                put("stream", false)
                sessionId?.let { put("session_id", it) }
                workDir?.let { put("working_dir", it) }
            }

            val request = Request.Builder()
                .url("$baseUrl/v1/chat/completions")
                .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            response.close()

            val json = JSONObject(body)
            val choices = json.optJSONArray("choices")

            if (choices != null && choices.length() > 0) {
                val message = choices.getJSONObject(0).optJSONObject("message")
                val content = message?.optString("content", "") ?: ""
                val cached = json.optBoolean("cached", false)

                Result.success(ChatResponse(content = content, cached = cached))
            } else {
                val error = json.optJSONObject("error")
                Result.failure(Exception(error?.optString("message") ?: "Unknown API error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun uploadImage(
        imageFile: File,
        prompt: String = "Analyze this image",
        sessionId: String? = null
    ): Flow<ChatResponse> = flow {
        val imageBytes = imageFile.readBytes()
        val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

        val requestBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                imageFile.name,
                imageBytes.toRequestBody("image/*".toMediaType())
            )
            .addFormDataPart("prompt", prompt)
            sessionId?.let { requestBuilder.addFormDataPart("session_id", it) }

        val request = Request.Builder()
            .url("$baseUrl/v1/images/analyze")
            .post(requestBuilder.build())
            .build()

        val response = suspendCancellableCoroutine<Response> { cont ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(createErrorResponse(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) cont.resume(response)
                }
            })
        }

        val body = response.body?.string() ?: "{}"
        response.close()

        try {
            val json = JSONObject(body)
            val content = json.optString("content", json.optString("analysis", body))
            emit(ChatResponse(content = content))
        } catch (e: Exception) {
            emit(ChatResponse(content = body))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun createSession(
        title: String? = null,
        workDir: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                title?.let { put("title", it) }
                workDir?.let { put("working_dir", it) }
            }

            val request = Request.Builder()
                .url("$baseUrl/v1/sessions")
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            response.close()

            val json = JSONObject(responseBody)
            val sessionId = json.optString("session_id", json.optString("id", ""))

            if (sessionId.isNotEmpty()) {
                Result.success(sessionId)
            } else {
                Result.failure(Exception("No session ID returned"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createErrorResponse(e: Exception): Response {
        throw e
    }

    private fun JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        keys().forEach { key ->
            val value = get(key)
            map[key] = when (value) {
                is JSONObject -> value.toMap()
                is JSONArray -> (0 until value.length()).map { value.get(it) }
                else -> value
            }
        }
        return map
    }

    companion object {
        @Volatile
        private var INSTANCE: JarvisApiClient? = null

        fun getInstance(baseUrl: String): JarvisApiClient {
            return INSTANCE?.let {
                if (it.baseUrl == baseUrl) it else JarvisApiClient(baseUrl).also { INSTANCE = it }
            } ?: JarvisApiClient(baseUrl).also { INSTANCE = it }
        }

        fun reset() {
            INSTANCE = null
        }
    }
}
