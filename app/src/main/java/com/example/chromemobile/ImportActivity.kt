package com.example.chromemobile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import kotlinx.android.synthetic.main.activity_import.*
import kotlinx.android.synthetic.main.content_import.*

class ImportActivity : AppCompatActivity() {

    private val prefs by lazy {
        getPreferences(Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import)
        setSupportActionBar(toolbar)

        supportActionBar?.run {
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        toolbar.setNavigationOnClickListener { finish() }

        importButton.setOnClickListener {
            setResult(Activity.RESULT_OK,
                Intent().apply { putExtra("URL", urlText.text.toString()) })
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        prefs.edit { putString("URL", urlText.text.toString()) }
    }

    override fun onResume() {
        super.onResume()
        urlText.setText(prefs.getString("URL", ""))
    }

}
