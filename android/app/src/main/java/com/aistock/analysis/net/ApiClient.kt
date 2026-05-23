package com.aistock.analysis.net

import com.aistock.analysis.BuildConfig
import com.aistock.analysis.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

class ApiException(val code: Int, val errorCode: String?, message: String) :
    RuntimeException(message)

sealed interface StreamEvent {
    data class Meta(val event: MetaEvent) : StreamEvent
    data class Delta(val event: DeltaEvent) : StreamEvent
    data class Error(val event: ErrorEvent) : StreamEvent
    data object Done : StreamEvent
}

class ApiClient(private val prefs: Prefs) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // SSE: no read timeout
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private fun base() = BuildConfig.API_BASE_URL.trimEnd('/')

    private suspend fun authHeaders(includeAuth: Boolean): Map<String, String> {
        val h = mutableMapOf(
            "X-Device-Id" to prefs.deviceId(),
            "Accept" to "application/json",
        )
        if (includeAuth) {
            val tok = prefs.currentToken()
            if (!tok.isNullOrBlank()) h["Authorization"] = "Bearer $tok"
        }
        return h
    }

    suspend fun status(): StatusResp {
        val req = Request.Builder()
            .url("${base()}/status")
            .apply { authHeaders(true).forEach { (k, v) -> header(k, v) } }
            .get()
            .build()
        return execEnvelope(req, StatusResp.serializer())
    }

    suspend fun tickers(): List<String> {
        val req = Request.Builder()
            .url("${base()}/tickers")
            .apply { authHeaders(false).forEach { (k, v) -> header(k, v) } }
            .get()
            .build()
        return execEnvelope(req, ListSerializer(String.serializer()))
    }

    suspend fun googleSignIn(idToken: String): GoogleAuthResp {
        val body = buildJsonObject { put("idToken", idToken) }.toString()
            .toRequestBody(JSON_MEDIA)
        val req = Request.Builder()
            .url("${base()}/auth/google")
            .apply { authHeaders(false).forEach { (k, v) -> header(k, v) } }
            .post(body)
            .build()
        return execEnvelope(req, GoogleAuthResp.serializer())
    }

    suspend fun verifyPurchase(productId: String, purchaseToken: String): VerifyResp {
        val body = buildJsonObject {
            put("productId", productId)
            put("purchaseToken", purchaseToken)
        }.toString().toRequestBody(JSON_MEDIA)
        val req = Request.Builder()
            .url("${base()}/billing/verify")
            .apply { authHeaders(true).forEach { (k, v) -> header(k, v) } }
            .post(body)
            .build()
        return execEnvelope(req, VerifyResp.serializer())
    }

    fun analyzeStream(ticker: String): Flow<StreamEvent> = flow {
        val body = buildJsonObject { put("ticker", ticker) }.toString()
            .toRequestBody(JSON_MEDIA)
        val req = Request.Builder()
            .url("${base()}/analyze")
            .apply { authHeaders(true).forEach { (k, v) -> header(k, v) } }
            .header("Accept", "text/event-stream")
            .post(body)
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body?.string() ?: ""
                val code = runCatching {
                    (json.parseToJsonElement(errBody) as? JsonObject)?.get("error")
                        ?.toString()?.trim('"')
                }.getOrNull()
                throw ApiException(resp.code, code, "HTTP ${resp.code}: $errBody")
            }
            val source = resp.body!!.source()
            var eventName = "message"
            val dataLines = mutableListOf<String>()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isEmpty()) {
                    if (dataLines.isNotEmpty()) {
                        emitEvent(eventName, dataLines.joinToString("\n"), this)
                        if (eventName == "done") return@flow
                    }
                    eventName = "message"
                    dataLines.clear()
                    continue
                }
                when {
                    line.startsWith(":") -> {} // heartbeat comment
                    line.startsWith("event:") -> eventName = line.substring(6).trim()
                    line.startsWith("data:") -> dataLines.add(line.substring(5).trimStart())
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun emitEvent(
        eventName: String,
        payload: String,
        emitter: FlowCollector<StreamEvent>,
    ) {
        when (eventName) {
            "meta" -> emitter.emit(StreamEvent.Meta(json.decodeFromString(MetaEvent.serializer(), payload)))
            "delta" -> emitter.emit(StreamEvent.Delta(json.decodeFromString(DeltaEvent.serializer(), payload)))
            "error" -> emitter.emit(StreamEvent.Error(json.decodeFromString(ErrorEvent.serializer(), payload)))
            "done" -> emitter.emit(StreamEvent.Done)
        }
    }

    private fun <T> execEnvelope(req: Request, ser: kotlinx.serialization.KSerializer<T>): T {
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val code = runCatching {
                    (json.parseToJsonElement(text) as? JsonObject)?.get("error")
                        ?.toString()?.trim('"')
                }.getOrNull()
                throw ApiException(resp.code, code, "HTTP ${resp.code}: $text")
            }
            val env = json.decodeFromString(Envelope.serializer(ser), text)
            return env.data ?: throw ApiException(resp.code, env.error, "Empty data")
        }
    }
}
