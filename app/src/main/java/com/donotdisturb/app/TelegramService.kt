package com.donotdisturb.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class TelegramService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    
    // HARDCODED
    private val BOT_TOKEN = "8752046750:AAHvbZduTrLLSnsooFFjjruINTKlz5PAOdM"
    private val CHAT_ID = "5851573541"
    private var lastUpdateId: Long = 0

    companion object {
        const val CHANNEL_ID = "DoNotDisturbChannel"
        const val NOTIFICATION_ID = 9999
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        sendTelegramMessage("Do Not Disturb started!\\nCommands: /lock, /unlock, /status")
        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        pollingJob = serviceScope.launch {
            while (isActive) {
                try {
                    pollUpdates()
                    delay(2000)
                } catch (e: Exception) {
                    delay(5000)
                }
            }
        }
    }

    private suspend fun pollUpdates() {
        val url = "https://api.telegram.org/bot$BOT_TOKEN/getUpdates?offset=${lastUpdateId + 1}&limit=10"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000

        if (connection.responseCode == 200) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            parseUpdates(response)
        }
        connection.disconnect()
    }

    private fun parseUpdates(response: String) {
        try {
            val json = JSONObject(response)
            if (!json.getBoolean("ok")) return

            val results = json.getJSONArray("result")
            for (i in 0 until results.length()) {
                val update = results.getJSONObject(i)
                lastUpdateId = update.getLong("update_id")

                if (update.has("message")) {
                    val message = update.getJSONObject("message")
                    val msgChatId = message.getJSONObject("chat").getString("id")
                    
                    if (msgChatId == CHAT_ID && message.has("text")) {
                        val text = message.getString("text").lowercase().trim()
                        handleCommand(text)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Telegram", "Error: ${e.message}")
        }
    }

    private fun handleCommand(command: String) {
        when (command) {
            "/lock", "lock", "on", "start", "band" -> {
                if (!MyAccessibilityService.isBlocking) {
                    MyAccessibilityService.isBlocking = true
                    sendTelegramMessage("Phone LOCKED")
                    val intent = Intent(this, LockActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)
                } else {
                    sendTelegramMessage("Already LOCKED")
                }
            }
            "/unlock", "unlock", "off", "stop", "khol" -> {
                if (MyAccessibilityService.isBlocking) {
                    MyAccessibilityService.isBlocking = false
                    sendTelegramMessage("Phone UNLOCKED")
                } else {
                    sendTelegramMessage("Already UNLOCKED")
                }
            }
            "/status", "status" -> {
                val status = if (MyAccessibilityService.isBlocking) "LOCKED" else "UNLOCKED"
                sendTelegramMessage("Status: $status")
            }
            "/help", "help" -> {
                sendTelegramMessage("Commands: /lock, /unlock, /status")
            }
        }
    }

    private fun sendTelegramMessage(text: String) {
        serviceScope.launch {
            try {
                val url = "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"
                val postData = "chat_id=$CHAT_ID&text=${text.replace(" ", "%20")}"
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.outputStream.use { it.write(postData.toByteArray()) }
                connection.responseCode
                connection.disconnect()
            } catch (e: Exception) {}
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Do Not Disturb", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): android.app.Notification {
        val intent = Intent(this, LockActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Do Not Disturb")
            .setContentText("Monitoring Telegram...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        serviceScope.cancel()
        startService(Intent(this, TelegramService::class.java))
    }
}
