package com.example.chromemobile

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*

class MainActivity : AppCompatActivity() {

    private var serviceWorking = false
    private var shuttingDown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        logScrollView.getChildAt(0).let { layout ->
            var height = layout.height
            layout.viewTreeObserver.addOnGlobalLayoutListener {
                height = layout.height.also { new ->
                    logScrollView.scrollBy(0, new - height)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        Intent(this, ChromeService::class.java).run {
            putExtra("COMMAND", "START")
            putExtra(
                "PENDING_INTENT",
                createPendingResult(
                    ON_CHROME_SERVICE_START,
                    Intent(),
                    PendingIntent.FLAG_ONE_SHOT
                )
            )
            putExtra(
                "ON_DESTROY",
                createPendingResult(
                    ON_CHROME_SERVICE_DESTROY,
                    Intent(),
                    PendingIntent.FLAG_ONE_SHOT
                )
            )
            startChromeService(this)
        }

        Intent(this, ChromeService::class.java).run {
            putExtra("COMMAND", "TRACE")
            putExtra(
                "PENDING_INTENT",
                createPendingResult(ON_CHROME_SERVICE_TRACE, Intent(), 0)
            )
            startChromeService(this)
        }
    }

    override fun onStop() {
        super.onStop()
        when {
            shuttingDown -> {
                return
            }
            serviceWorking -> {
                Intent(this, ChromeService::class.java).run {
                    putExtra("COMMAND", "TRACE")
                    startChromeService(this)
                }
            }
            else -> {
                stopChromeService()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            ON_CHROME_SERVICE_START -> {
                serviceWorking = data?.extras?.getBoolean("WORKING") == true
                if (serviceWorking) {
                    // Foreground the service.
                    Intent(this, ChromeService::class.java).run {
                        putExtra("COMMAND", "START")
                        startChromeService(this)
                    }
                } else {
                    startImportActivity()
                }
            }
            ON_CHROME_SERVICE_TRACE -> {
                if (data?.extras?.getBoolean("RESET") == true) {
                    logTextView.text = ""
                }
                data?.extras?.getString("MESSAGE")?.let { message ->
                    logTextView.append(message)
                }
            }
            ON_CHROME_SERVICE_IMPORT -> {
                if (resultCode == ChromeService.RESULT_OK) {
                    serviceWorking = true
                    // Foreground the service.
                    Intent(this, ChromeService::class.java).run {
                        putExtra("COMMAND", "START")
                        startChromeService(this)
                    }
                }
                data?.extras?.getString("MESSAGE")?.let { message ->
                    Snackbar.make(logTextView, message, Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show()
                }
            }
            ON_CHROME_SERVICE_DESTROY -> {
                shuttingDown = true
                finish()
            }
            START_IMPORT_ACTIVITY -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.extras?.getString("URL")?.let { url ->
                        Intent(this, ChromeService::class.java).run {
                            putExtra("COMMAND", "IMPORT")
                            putExtra("URL", url)
                            putExtra(
                                "PENDING_INTENT",
                                createPendingResult(
                                    ON_CHROME_SERVICE_IMPORT,
                                    Intent(),
                                    PendingIntent.FLAG_ONE_SHOT
                                )
                            )
                            startChromeService(this)
                        }
                    }
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_import_url -> true.also {
                startImportActivity()
            }
            R.id.action_restart -> true.also {
                Intent(this, ChromeService::class.java).run {
                    putExtra("COMMAND", "RESTART")
                    startChromeService(this)
                }
            }
            R.id.action_shutdown -> true.also {
                shuttingDown = true
                stopChromeService()
                finish()
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun startChromeService(service: Intent) {
        if (serviceWorking) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                service.putExtra("FOREGROUND", true)
                startForegroundService(service)
                return
            }
        }
        startService(service)
    }

    private fun stopChromeService() {
        stopService(Intent(this, ChromeService::class.java))
    }

    private fun startImportActivity() {
        val intent = Intent(this, ImportActivity::class.java)
        startActivityForResult(intent, START_IMPORT_ACTIVITY)
    }

    companion object {
        const val ON_CHROME_SERVICE_START = 0
        const val ON_CHROME_SERVICE_TRACE = 1
        const val ON_CHROME_SERVICE_IMPORT = 2
        const val ON_CHROME_SERVICE_DESTROY = 3
        const val START_IMPORT_ACTIVITY = 100
    }
}
