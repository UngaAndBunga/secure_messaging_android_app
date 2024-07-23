package com.example.secure_messaging

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.crypto.tink.Aead
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var passwordEditText: EditText
    private lateinit var messageEditText: EditText
    private lateinit var setPasswordButton: Button
    private lateinit var sendMessageButton: Button
    private lateinit var receiveMessagesButton: Button
    private lateinit var receivedText: TextView

    private var encryptionKey: Aead? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        passwordEditText = findViewById(R.id.passwordEditText)
        messageEditText = findViewById(R.id.messageEditText)
        setPasswordButton = findViewById(R.id.setPasswordButton)
        sendMessageButton = findViewById(R.id.sendMessageButton)
        receiveMessagesButton = findViewById(R.id.receiveMessagesButton)
        receivedText = findViewById(R.id.receivedText)

        setPasswordButton.setOnClickListener {
            val password = passwordEditText.text.toString()
            if (password.isNotEmpty()) {
                encryptionKey = CryptoUtils.deriveKey(password)
                Toast.makeText(this, "Password set successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        sendMessageButton.setOnClickListener {
            val message = messageEditText.text.toString()
            if (message.isNotEmpty() && encryptionKey != null) {
                val encryptedMessage = CryptoUtils.encrypt(encryptionKey!!, message)
                sendMessage(encryptedMessage)
            } else {
                Toast.makeText(this, "Message or encryption key missing", Toast.LENGTH_SHORT).show()
            }
        }

        receiveMessagesButton.setOnClickListener {
            if (encryptionKey != null) {
                receiveMessages()
            } else {
                Toast.makeText(this, "Encryption key missing", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendMessage(encryptedMessage: String) {
        NetworkUtils.sendMessage(encryptedMessage, object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Failed to send message", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Message sent successfully", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun receiveMessages() {
        NetworkUtils.getMessages(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Failed to receive messages", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {

                response.body?.let { responseBody ->
                    val responseString = responseBody.string()
                    Log.d("debug", responseString)
                    try{
                        val jsonObject = JSONObject(responseString)
                        val jsonArray = jsonObject.getJSONArray("messages")
                        val messages = StringBuilder()
                        for (i in 0 until jsonArray.length()) {
                            val messageObject = jsonArray.getJSONObject(i)
                            val message = CryptoUtils.decrypt(encryptionKey!!, messageObject.getString("message"))
                            messages.append(message).append(System.lineSeparator())
                        }
                        runOnUiThread {
                            receivedText.text = messages.toString().trim()
                        }
                    }catch (e: JSONException){
                        runOnUiThread{
                            receivedText.text = "jsonexception" + responseString
                        }
                        Log.d("jsonexception", e.toString())
                    }

                }?: runOnUiThread {
                    //receivedText.text = "No messages received"
                    Log.d("debug", "Error")
                }
            }
        })
    }
}
