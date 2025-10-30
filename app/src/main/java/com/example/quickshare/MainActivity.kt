package com.example.quickshare

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // بررسی مجوز دسترسی به حافظه
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ),
                1
            )
        } else {
            handleIntent(intent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 1 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            handleIntent(intent)
        } else {
            Toast.makeText(this, "❌ مجوز دسترسی به حافظه رد شد", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uri ->
                    saveFile(uri)
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                uris?.forEach { saveFile(it) }
            }
        }
        finish()
    }

    private fun saveFile(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val backupDir = File(Environment.getExternalStorageDirectory(), "WA_Backup")

            if (!backupDir.exists()) backupDir.mkdirs()

            val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "shared_file"
            val outFile = File(backupDir, fileName)

            val outputStream = FileOutputStream(outFile)
            inputStream?.copyTo(outputStream)

            inputStream?.close()
            outputStream.close()

            Toast.makeText(this, "✅ فایل در پوشه WA_Backup ذخیره شد", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ خطا در ذخیره فایل", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
}
