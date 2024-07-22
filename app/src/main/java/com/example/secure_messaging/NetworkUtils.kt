package com.example.secure_messaging

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

object NetworkUtils {
    private val client = OkHttpClient();

    fun sendMessage(message: String, callback: Callback) {
        val requestBody =
            "{\"message\":\"$message\"}".toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://ourmessaging.app/api/send_message")
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(callback)
    }
    fun getMessages(callback: Callback) {
        val request = Request.Builder()
            .url("https://ourmessaging.app/api/get_messages")
            .build()
        client.newCall(request).enqueue(callback)
    }
}
