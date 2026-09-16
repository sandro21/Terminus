package com.terminus.app.data

import com.terminus.app.BuildConfig
import com.terminus.shared.BundleDto
import com.terminus.shared.DepartureResponse
import com.terminus.shared.DeviceRegistration
import com.terminus.shared.SubscriptionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class ApiClient {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
    private val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')
    private val mediaType = "application/json".toMediaType()

    suspend fun bundle(version: String?): BundleDto = get(
        "/bundle" + (version?.let { "?version=${URLEncoder.encode(it, StandardCharsets.UTF_8.name())}" } ?: ""),
    )

    suspend fun departures(stationId: String): DepartureResponse = get(
        "/stations/${URLEncoder.encode(stationId, StandardCharsets.UTF_8.name())}/departures",
    )

    suspend fun registerDevice(request: DeviceRegistration) = post("/devices", json.encodeToString(request))

    suspend fun subscribe(request: SubscriptionRequest) = post("/subscriptions", json.encodeToString(request))

    private suspend inline fun <reified T> get(path: String): T = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(baseUrl + path).header("User-Agent", "Terminus-Android/1.0").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            json.decodeFromString(response.body.string())
        }
    }

    private suspend fun post(path: String, body: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(baseUrl + path)
            .header("User-Agent", "Terminus-Android/1.0")
            .post(body.toRequestBody(mediaType)).build()
        client.newCall(request).execute().use { response -> check(response.isSuccessful) { "HTTP ${response.code}" } }
    }
}
