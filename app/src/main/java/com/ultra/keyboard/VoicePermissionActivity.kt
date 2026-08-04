package com.ultra.keyboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Nema svoj vizuelni izgled - jedina svrha joj je da odmah, u trenutku kad
 * korisnik dodirne mikrofon na tastaturi, izbaci sistemski dijalog za
 * dozvolu (isto kao "Dictate" na iPhone-u pri prvom korišćenju). Servis
 * tastature (IME) sam ne može da zatraži dozvolu - samo prava Activity može.
 */
class VoicePermissionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            finish()
            return
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        val msg = if (granted) R.string.voice_permission_granted else R.string.voice_permission_denied
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }
}
