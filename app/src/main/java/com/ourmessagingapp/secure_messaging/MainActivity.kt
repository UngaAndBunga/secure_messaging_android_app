package com.ourmessagingapp.secure_messaging

import Message
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.MenuItem

import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.crypto.tink.Aead
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable
    private lateinit var passwordEditText: EditText
    private lateinit var messageEditText: EditText
    private lateinit var setPasswordButton: Button
    private lateinit var sendMessageButton: Button
    private lateinit var deviceID: String
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle

    private var encryptionKey: Aead? = null

    private val messages = mutableListOf<Message>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getDeviceId(this)

        val serviceIntent = Intent(this, MessageService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        // Initialize views
        passwordEditText = findViewById(R.id.passwordEditText)
        messageEditText = findViewById(R.id.messageEditText)
        setPasswordButton = findViewById(R.id.setPasswordButton)
        sendMessageButton = findViewById(R.id.sendMessageButton)
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        drawerLayout = findViewById(R.id.drawer_layout)
        val navigationView: NavigationView = findViewById(R.id.navigation_view)

        // Set up toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Initialize ActionBarDrawerToggle and sync state
        actionBarDrawerToggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close
        )
        drawerLayout.addDrawerListener(actionBarDrawerToggle)
        actionBarDrawerToggle.syncState()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        navigationView.setNavigationItemSelectedListener { menuItem ->
            menuItem.isChecked = true
            drawerLayout.closeDrawers()
            when (menuItem.itemId) {
                R.id.nav_chats -> {
                    // Handle chats action
                    true
                }
                R.id.nav_add_friends -> {
                    // Handle add friends action
                    true
                }
                R.id.nav_friends -> {
                    // Handle friends action
                    true
                }
                else -> false
            }
        }

        // Set up button listeners
        setPasswordButton.setOnClickListener {
            val password = passwordEditText.text.toString() + deviceID //ensure the password string is indeed unique
            if (password.isNotEmpty()) {
                encryptionKey = CryptoUtils.deriveKey(password)
                savePasswordToPreferences(password);
                Toast.makeText(this, "Password set successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        sendMessageButton.setOnClickListener {
            val message = messageEditText.text.toString()
            if (message.isNotEmpty() && encryptionKey != null) {
                val encryptedMessage = CryptoUtils.encrypt(encryptionKey!!, message)
                val signature = getSha1Hash(encryptedMessage)
                sendMessage(encryptedMessage, signature)
                runOnUiThread {
                    messageEditText.setText("")
                    messages.add(Message(message, deviceID))
                    chatAdapter.notifyDataSetChanged()
                }
            } else {
                Toast.makeText(this, "Message or encryption key missing", Toast.LENGTH_SHORT).show()
            }
        }

        startRepeatingTask()

        chatAdapter = ChatAdapter(messages, deviceID)
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatRecyclerView.adapter = chatAdapter
    }

    private fun getDeviceId(context: Context) {
        deviceID = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    private fun getSha1Hash(input: String): String {
        return try {
            // Create a SHA-1 MessageDigest
            val messageDigest = MessageDigest.getInstance("SHA-1")
            // Update the message digest with the input string's bytes
            messageDigest.update(input.toByteArray(Charsets.UTF_8))
            // Convert the byte array to a hex string
            val hashBytes = messageDigest.digest()
            // Convert hash bytes to hex format
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            ""
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (actionBarDrawerToggle.onOptionsItemSelected(item)) {
            true
        } else super.onOptionsItemSelected(item)
    }

    private fun startRepeatingTask() {
        runnable = object : Runnable {
            override fun run() {
                if (encryptionKey != null) {
                    receiveMessages()
                }
                handler.postDelayed(this, 2000) // 2 seconds
            }
        }
        handler.post(runnable)
    }

    private fun sendMessage(encryptedMessage: String, signature: String, retry: Boolean = false) {
        NetworkUtils.sendMessage(encryptedMessage, deviceID, retry, object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Failed to send message", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                var responseSignature = ""
                response.body?.let { responseBody ->
                    val responseString = responseBody.string()
                    Log.d("response String", responseString)
                    try {
                        val jsonObject = JSONObject(responseString)
                        responseSignature = jsonObject.getString("signature")
                    } catch (e: JSONException) {
                        Log.d("jsonexception", e.toString())
                    }
                }
                if (signature != responseSignature) {
                    Log.d("response", "$signature $responseSignature")
                    if (!retry) {
                        sendMessage(encryptedMessage, signature, true)
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Message verification failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Message sent successfully", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun receiveMessages() {
        NetworkUtils.getMessages(deviceID,object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Please check your internet connection", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
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

                        runOnUiThread {
                            messages.addAll(newMessages)
                            chatAdapter.notifyDataSetChanged()
                            Log.d("ChatAdapter", "Messages size: ${messages.size}")

                        }
                    } catch (e: JSONException) {
                        Log.d("jsonexception", e.toString())
                    }
                } ?: runOnUiThread {
                    Log.d("debug", "Error in response")
                }
            }
        })
    }

    // Store password in SharedPreferences
    private fun savePasswordToPreferences(password: String) {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("password", password)
            apply()
        }
    }


    private fun stopRepeatingTask() {
        handler.removeCallbacks(runnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRepeatingTask()
    }
}
