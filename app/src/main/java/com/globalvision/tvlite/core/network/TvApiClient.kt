package com.globalvision.tvlite.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TvApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun get(path: String, params: Map<String, Any?> = emptyMap()): JSONObject {
        val url = buildSignedUrl(path, params)
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept-Language", TvApiConfig.ACCEPT_LANGUAGE)
            .header("X-Client-Version", TvApiConfig.CLIENT_VERSION)
            .header("X-Client-Setting", TvApiConfig.CLIENT_SETTING)
            .header("Content-Type", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val parsed = TvApiCrypto.decryptResponse(body)
            if (!response.isSuccessful) {
                throw IllegalStateException(errorMessage(parsed, response.code))
            }
            return JSONObject(parsed)
        }
    }

    suspend fun post(path: String, body: Map<String, Any?> = emptyMap()): JSONObject {
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
            val raw = response.body?.string().orEmpty()
            val parsed = TvApiCrypto.decryptResponse(raw)
            if (!response.isSuccessful) {
                throw IllegalStateException(errorMessage(parsed, response.code))
            }
            return JSONObject(parsed)
        }
    }

    private fun buildSignedUrl(path: String, params: Map<String, Any?>): String {
        val url = "${TvApiConfig.BASE_URL}$path".toHttpUrl()
        val signedParams = buildMap {
            put("timestamp", System.currentTimeMillis())
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
}

private fun String.toRequestBody() =
    this.toRequestBody("application/json; charset=utf-8".toMediaType())
