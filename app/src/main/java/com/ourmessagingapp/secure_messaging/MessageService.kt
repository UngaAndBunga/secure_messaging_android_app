package com.ourmessagingapp.secure_messaging

import Message
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import com.google.crypto.tink.Aead
import androidx.core.content.ContextCompat


class MessageService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable
    private var encryptionKey: Aead? = null
    private var previousMessageCount = 0
    private val CHANNEL_ID = "MessageServiceChannel"
    private var deviceID: String = ""

    override fun onCreate() {
        super.onCreate()

        deviceID = Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID)

        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Secure Messaging Service")
            .setContentText("Running...")
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)

        runnable = object : Runnable {
            override fun run() {
                startReceivingMessages()
                handler.postDelayed(this, 2000) // 2000 milliseconds = 2 seconds
            }
        }

        handler.post(runnable)
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Message Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun getPasswordFromPreferences(): String? {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("password", null)
    }

    private fun startReceivingMessages() {
        val password = getPasswordFromPreferences()
        if (password != null) {
            encryptionKey = CryptoUtils.deriveKey(password)
        } else {
            Log.e("com.ourmessagingapp.secure_messaging.MessageService", "Password not available")
            return
        }

        NetworkUtils.getMessages(deviceID, object : Callback {
            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.body?.let { responseBody ->
                    val responseString = responseBody.string()
                    try {
                        val jsonObject = JSONObject(responseString)
                        val jsonArray = jsonObject.getJSONArray("messages")
                        val newMessages = mutableListOf<Message>()

                        for (i in 0 until jsonArray.length()) {
                            val messageObject = jsonArray.getJSONObject(i)
                            val decryptedMessage = CryptoUtils.decrypt(encryptionKey!!, messageObject.getString("message"))
                            val deviceID = messageObject.getString("deviceID")
                            newMessages.add(Message(decryptedMessage, deviceID))
                        }

                        if (newMessages.size > previousMessageCount) {
                            playNotificationSound()
                        }

                        previousMessageCount = newMessages.size

                        // Send broadcast with new messages
                        val intent = Intent("NEW_MESSAGES")
                        intent.putParcelableArrayListExtra("messages", ArrayList(newMessages))
                        LocalBroadcastManager.getInstance(this@MessageService).sendBroadcast(intent)

                    } catch (e: JSONException) {
                        Log.d("jsonexception", e.toString())
                    }
                } ?: run {
                    Log.d("debug", "Error in response")
                }
            }

            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e("com.ourmessagingapp.secure_messaging.MessageService", "Failed to get messages", e)
            }
        })
    }

    private fun playNotificationSound() {
        try {
            val mediaPlayer = MediaPlayer.create(applicationContext, R.raw.popping_sound)
            mediaPlayer.start()
            mediaPlayer.setOnCompletionListener { mediaPlayerInstance: MediaPlayer ->
                // Release the MediaPlayer resource once the sound has finished
                mediaPlayerInstance.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
