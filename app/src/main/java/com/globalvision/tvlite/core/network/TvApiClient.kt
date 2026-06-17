package com.globalvision.tvlite.core.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit

class TvApiClient(
    context: Context? = null,
    private val client: OkHttpClient = buildClient(context),
) {
    suspend fun get(path: String, params: Map<String, Any?> = emptyMap()): JSONObject {
        return executeWithRetry {
            val url = buildSignedUrl(path, params)
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept-Language", TvApiConfig.ACCEPT_LANGUAGE)
                .header("X-Client-Version", TvApiConfig.CLIENT_VERSION)
                .header("X-Client-Setting", TvApiConfig.CLIENT_SETTING)
                .header("Cache-Control", "public, max-age=300")
                .header("Content-Type", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                updateServerTimeOffset(response.header("Date"))
                val body = response.body?.string().orEmpty()
                val parsed = TvApiCrypto.decryptResponse(body)
                if (!response.isSuccessful) {
                    throw response.toRequestException(parsed)
                }
                JSONObject(parsed)
            }
        }
    }

    suspend fun post(path: String, body: Map<String, Any?> = emptyMap()): JSONObject {
        return executeWithRetry {
            val payload = JSONObject(body.filterValues { it != null }).toString()
            val pack = TvApiCrypto.encryptPack(payload)
            val signedBody = JSONObject()
                .put("pack", pack)
                .put("signature", TvApiCrypto.signPack(pack))
                .toString()

            val requestBody = signedBody.toRequestBody()
            val request = Request.Builder()
                .url("${TvApiConfig.BASE_URL}$path")
                .post(requestBody)
                .header("Accept-Language", TvApiConfig.ACCEPT_LANGUAGE)
                .header("X-Client-Version", TvApiConfig.CLIENT_VERSION)
                .header("X-Client-Setting", TvApiConfig.CLIENT_SETTING)
                .header("Content-Type", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                updateServerTimeOffset(response.header("Date"))
                val raw = response.body?.string().orEmpty()
                val parsed = TvApiCrypto.decryptResponse(raw)
                if (!response.isSuccessful) {
                    throw response.toRequestException(parsed)
                }
                JSONObject(parsed)
            }
        }
    }

    private suspend fun <T> executeWithRetry(
        maxAttempts: Int = 3,
        block: () -> T,
    ): T {
        var lastError: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (throwable: Throwable) {
                lastError = throwable
                val shouldRetry = attempt < maxAttempts - 1 && throwable.isRetryableTimeout()
                if (!shouldRetry) {
                    throw throwable
                }
                delay(400L * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("请求失败")
    }

    private fun buildSignedUrl(path: String, params: Map<String, Any?>): String {
        val url = "${TvApiConfig.BASE_URL}$path".toHttpUrl()
        val signedParams = buildMap {
            put("timestamp", currentServerAlignedTimeMillis())
            params.forEach { (key, value) ->
                if (value != null && value.toString().isNotBlank()) {
                    put(key, value)
                }
            }
        }
        val pack = TvApiCrypto.encryptPack(JSONObject(signedParams).toString())
        return url.newBuilder()
            .addQueryParameter("pack", pack)
            .addQueryParameter("signature", TvApiCrypto.signPack(pack))
            .build()
            .toString()
    }

    private fun errorMessage(parsed: String, code: Int): String {
        return runCatching {
            JSONObject(parsed).optString("msg")
                .ifBlank { JSONObject(parsed).optString("message") }
                .ifBlank { JSONObject(parsed).optString("error") }
                .ifBlank { "请求失败 ($code)" }
        }.getOrDefault("请求失败 ($code)")
    }

    private fun Throwable.isRetryableTimeout(): Boolean {
        if (this is SocketTimeoutException || this is IOException) return true
        if (this is RetryableRequestException) return true
        if (this is IllegalStateException) {
            val message = message.orEmpty()
            return message.contains("超时") || message.contains("timeout", ignoreCase = true)
        }
        return false
    }

    private fun updateServerTimeOffset(dateHeader: String?) {
        if (dateHeader.isNullOrBlank()) return
        runCatching {
            val serverTimeMillis = ZonedDateTime.parse(
                dateHeader,
                DateTimeFormatter.RFC_1123_DATE_TIME,
            ).toInstant().toEpochMilli()
            val offset = serverTimeMillis - System.currentTimeMillis()
            serverTimeOffsetMillis.set(offset)
            Log.d(TAG, "server time offset updated: ${offset}ms")
        }
    }

    private fun currentServerAlignedTimeMillis(): Long =
        System.currentTimeMillis() + serverTimeOffsetMillis.get()

    private fun okhttp3.Response.toRequestException(parsed: String): Throwable {
        val message = errorMessage(parsed, code)
        return if (code == 408 || code == 429 || code in 500..599) {
            RetryableRequestException(message)
        } else {
            IllegalStateException(message)
        }
    }

    companion object {
        private const val TAG = "TvApiClient"
        private const val DISK_CACHE_SIZE_BYTES = 20L * 1024L * 1024L
        private val serverTimeOffsetMillis = AtomicLong(0L)

        private fun buildClient(context: Context?): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
            if (context != null) {
                val cacheDir = File(context.cacheDir, "http_cache")
                builder.cache(Cache(cacheDir, DISK_CACHE_SIZE_BYTES))
            }
            return builder.build()
        }
    }
}

private class RetryableRequestException(message: String) : IOException(message)

private fun String.toRequestBody() =
    this.toRequestBody("application/json; charset=utf-8".toMediaType())
