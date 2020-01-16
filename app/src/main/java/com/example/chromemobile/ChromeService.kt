package com.example.chromemobile

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChromeService : Service() {

    private lateinit var chrome: chromemobile.ChromeService

    private val notification by lazy { createNotification() }

    private val log = object : Mutex by Mutex() {
        val text = StringBuilder()
        var pendingIntent: PendingIntent? = null
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        chromemobile.Chromemobile.setLogOutput { message ->
            runBlocking {
                log.withLock {
                    log.text.append(message)
                    runCatching {
                        log.pendingIntent?.send(this@ChromeService, 0, Intent().apply {
                            putExtra("MESSAGE", message)
                        })
                    }.onFailure {
                        log.pendingIntent = null
                    }
                }
            }
        }

        chrome = chromemobile.ChromeService(filesDir.absolutePath)
    }

    override fun onDestroy() {
        super.onDestroy()
        chrome.shutdown()
        chromemobile.Chromemobile.setLogOutput { }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.extras?.getBoolean("FOREGROUND") == true) {
            startForeground(NOTIFICATION_ID, notification)
        }
        when (intent?.extras?.getString("COMMAND")) {
            "START" -> {
                intent.extras?.getParcelable<PendingIntent>("PENDING_INTENT")?.run {
                    send(this@ChromeService, RESULT_OK, Intent().apply {
                        putExtra("WORKING", chrome.isWorking)
                    })
                }
            }
            "TRACE" -> {
                intent.extras?.getParcelable<PendingIntent>("PENDING_INTENT").let {
                    pendingIntent -> runBlocking {
                        log.withLock {
                            log.pendingIntent = pendingIntent
                            pendingIntent?.send(this@ChromeService, 0, Intent().apply {
                                putExtra("MESSAGE", log.text.toString())
                            })
                        }
                    }
                }
            }
            "IMPORT" -> {
                intent.extras?.getString("URL")?.let { url ->
                    runCatching {
                        chrome.loadURL(url)
                    }.onSuccess {
                        intent.extras?.getParcelable<PendingIntent>("PENDING_INTENT")?.run {
                            send(this@ChromeService, RESULT_OK, Intent().apply {
                                putExtra("MESSAGE", R.string.import_succeeded)
                            })
                        }
                    }.onFailure { e ->
                        intent.extras?.getParcelable<PendingIntent>("PENDING_INTENT")?.run {
                            send(this@ChromeService, RESULT_FAILED, Intent().apply {
                                putExtra("MESSAGE", e.message)
                            })
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun createNotification(): Notification {
        return run {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getSystemService(NotificationManager::class.java).createNotificationChannel(
                    NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        getText(R.string.notification_channel),
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
                NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                NotificationCompat.Builder(this)
            }
        }.apply {
            this.setContentTitle(getText(R.string.notification_title))
                .setContentText(getText(R.string.notification_message))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this@ChromeService,
                        0,
                        Intent(this@ChromeService, MainActivity::class.java),
                        0
                    )
                )
        }.build()
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "ChromeMobileService"
        const val NOTIFICATION_ID = 1
        const val RESULT_OK = 0
        const val RESULT_FAILED = 1
    }
}
