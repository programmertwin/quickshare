package com.example.quickshare

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        finish()
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) saveFile(uri) else toast("❌ فایل نامعتبر است")
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (uris.isNullOrEmpty()) {
                    toast("❌ فایلی دریافت نشد")
                } else {
                    uris.forEach { saveFile(it) }
                }
            }
            else -> toast("⚠️ عملیات پشتیبانی نمی‌شود")
        }
    }

    private fun saveFile(sourceUri: Uri) {
        try {
            val inStream: InputStream? = contentResolver.openInputStream(sourceUri)
            if (inStream == null) {
                toast("❌ دسترسی به ورودی ممکن نشد")
                return
            }

            val fileName = resolveFileName(sourceUri) ?: "shared_file"
            val success = if (Build.VERSION.SDK_INT >= 29) {
                // Android 10+ : MediaStore → Downloads/WA_Backup
                saveViaMediaStore(inStream, fileName)
            } else {
                // Android 7–9 : مستقیم در /sdcard/WA_Backup
                saveLegacyExternal(inStream, fileName)
            }

            if (success) {
                toast("✅ ذخیره شد: WA_Backup/$fileName")
            } else {
                toast("❌ خطا در ذخیره فایل")
            }
        } catch (e: Exception) {
            toast("❌ خطا در ذخیره فایل")
        }
    }

    // MediaStore: ذخیره در Download/WA_Backup بدون نیاز به دسترسی خاص
    private fun saveViaMediaStore(inStream: InputStream, fileName: String): Boolean {
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, guessMime(fileName))
            // مسیر نسبی داخل پوشه Downloads:
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/WA_Backup/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val itemUri = contentResolver.insert(collection, values) ?: return false
        var out: OutputStream? = null
        return try {
            out = contentResolver.openOutputStream(itemUri)
            if (out == null) return false
            inStream.copyTo(out)
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            contentResolver.update(itemUri, values, null, null)
            true
        } catch (_: Exception) {
            false
        } finally {
            try { out?.close() } catch (_: Exception) {}
            try { inStream.close() } catch (_: Exception) {}
        }
    }

    // Legacy: ذخیره مستقیم در /sdcard/WA_Backup برای API <29
    private fun saveLegacyExternal(inStream: InputStream, fileName: String): Boolean {
        val dir = File(Environment.getExternalStorageDirectory(), "WA_Backup")
        if (!dir.exists()) dir.mkdirs()
        val outFile = File(dir, fileName)
        var out: OutputStream? = null
        return try {
            out = FileOutputStream(outFile)
            inStream.copyTo(out)
            true
        } catch (_: Exception) {
            false
        } finally {
            try { out?.close() } catch (_: Exception) {}
            try { inStream.close() } catch (_: Exception) {}
        }
    }

    private fun resolveFileName(uri: Uri): String? {
        // تلاش ساده برای نام فایل از مسیر
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun guessMime(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".zip") -> "application/zip"
            lower.endsWith(".apk") -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
