package com.ourmessagingapp.secure_messaging

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object NetworkUtils {
    private val client = OkHttpClient();

    fun sendMessage(message: String, deviceId: String, retry: Boolean, callback: Callback) {
        val request = Request.Builder()
            .url("https://ourmessaging.app/api/send_message")
            .post(createRequestBody(message, deviceId, retry))
            .build()
        client.newCall(request).enqueue(callback)
    }

    fun getMessages(deviceID: String, callback: Callback) {
        val url = "https://ourmessaging.app/api/get_messages?deviceID=$deviceID"
        val request = Request.Builder()
            .url(url)
            .build()
        client.newCall(request).enqueue(callback)
    }

    private fun createRequestBody(message: String, deviceId: String, retry: Boolean): RequestBody {
        val json = JSONObject().apply {
            put("message", message)
            put("deviceID", deviceId)
            put("retry", retry)
        }
        return json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
    }
}
