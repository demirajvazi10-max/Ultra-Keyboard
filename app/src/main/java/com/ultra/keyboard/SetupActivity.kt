package com.ultra.keyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider

class SetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        findViewById<Button>(R.id.btnOpenSettings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        findViewById<Button>(R.id.btnSendLog).setOnClickListener {
            val file = AppLogger.getLogFile(this)
            if (!file.exists() || file.length() == 0L) {
                Toast.makeText(this, R.string.log_missing, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(sendIntent, getString(R.string.send_log)))
        }

        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            AppLogger.clear(this)
            Toast.makeText(this, R.string.log_cleared, Toast.LENGTH_SHORT).show()
        }
    }
}
