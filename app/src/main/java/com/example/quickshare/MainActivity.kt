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
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {

    // آدرس سرور PC (با adb reverse: گوشی → PC)
    // اگر reverse فعال است، همین 127.0.0.1 کار می‌کند؛
    // در غیر این صورت IP شبکه LAN سیستم‌تان را جایگزین کنید.
    private val uploadUrl = "http://127.0.0.1:8713/upload"

    // یک executor سبک برای کارهای پس‌زمینه
    private val ioExecutor = Executors.newFixedThreadPool(2)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) handleIntent(intent)
        finish()
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) processOne(uri) else toast("❌ فایل نامعتبر است")
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (uris.isNullOrEmpty()) toast("❌ فایلی دریافت نشد")
                else uris.forEach { processOne(it) }
            }
            else -> toast("⚠️ عملیات پشتیبانی نمی‌شود")
        }
    }

    private fun processOne(sourceUri: Uri) {
        val fileName = resolveFileName(sourceUri) ?: "shared_file"
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                // 1) ذخیره در Downloads/WA_Backup
                val itemUri = saveViaMediaStore(sourceUri, fileName)
                if (itemUri == null) {
                    toast("❌ خطا در ذخیره فایل")
                    return
                }
                // 2) آپلود از روی همان Uri
                ioExecutor.execute {
                    contentResolver.openInputStream(itemUri)?.use { inStream ->
                        val ok = uploadMultipart(uploadUrl, fileName, inStream)
                        if (ok) {
                            // 3) حذف بعد از آپلود موفق
                            contentResolver.delete(itemUri, null, null)
                            // (اختیاری) اطلاع کوچیک
                            // runOnUiThread { toast("✅ ارسال و حذف شد: $fileName") }
                        } else {
                            // runOnUiThread { toast("❌ آپلود ناموفق: $fileName") }
                        }
                    } ?: run {
                        // runOnUiThread { toast("❌ دسترسی به فایل ذخیره‌شده ممکن نشد") }
                    }
                }
            } else {
                // API 24..28
                val outFile = saveLegacyExternal(sourceUri, fileName)
                if (outFile == null) {
                    toast("❌ خطا در ذخیره فایل")
                    return
                }
                ioExecutor.execute {
                    try {
                        FileInputStream(outFile).use { fis ->
                            val ok = uploadMultipart(uploadUrl, fileName, fis)
                            if (ok) {
                                // حذف در اندروید قدیمی
                                outFile.delete()
                                // runOnUiThread { toast("✅ ارسال و حذف شد: $fileName") }
                            } else {
                                // runOnUiThread { toast("❌ آپلود ناموفق: $fileName") }
                            }
                        }
                    } catch (_: Exception) { /* سکوت */ }
                }
            }
        } catch (_: Exception) {
            toast("❌ خطا در پردازش فایل")
        }
    }

    /**
     * ذخیره برای API 29+: در Downloads/WA_Backup با MediaStore
     */
    private fun saveViaMediaStore(sourceUri: Uri, fileName: String): Uri? {
        return try {
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, guessMime(fileName))
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/WA_Backup/")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val itemUri = contentResolver.insert(collection, values) ?: return null
            contentResolver.openOutputStream(itemUri)?.use { out ->
                contentResolver.openInputStream(sourceUri)?.use { inStream ->
                    inStream.copyTo(out)
                } ?: return null
            } ?: return null

            // آزاد کردن فایل
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            contentResolver.update(itemUri, values, null, null)
            itemUri
        } catch (_: Exception) {
            null
        }
    }

    /**
     * ذخیره برای API 24..28: در /sdcard/WA_Backup
     */
    private fun saveLegacyExternal(sourceUri: Uri, fileName: String): File? {
        return try {
            val dir = File(Environment.getExternalStorageDirectory(), "WA_Backup")
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, fileName)
            contentResolver.openInputStream(sourceUri)?.use { inStream ->
                FileOutputStream(outFile).use { out -> inStream.copyTo(out) }
            } ?: return null
            outFile
        } catch (_: Exception) {
            null
        }
    }

    /**
     * آپلود چندبخشی ساده (multipart/form-data).
     * فقط در صورت موفقیت (HTTP 2xx) true برمی‌گرداند.
     */
    private fun uploadMultipart(urlStr: String, fileName: String, inStream: InputStream): Boolean {
        val boundary = "----QS${System.currentTimeMillis()}"
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                connectTimeout = 15000
                readTimeout = 45000
            }
            conn.outputStream.use { out ->
                fun writeStr(s: String) = out.write(s.toByteArray(Charsets.UTF_8))

                // part: name
                writeStr("--$boundary\r\n")
                writeStr("Content-Disposition: form-data; name=\"name\"\r\n\r\n")
                writeStr("$fileName\r\n")

                // part: file
                writeStr("--$boundary\r\n")
                writeStr("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
                writeStr("Content-Type: ${guessMime(fileName)}\r\n\r\n")
                inStream.copyTo(out)
                writeStr("\r\n--$boundary--\r\n")
            }
            val code = conn.responseCode
            code in 200..299
        } catch (_: Exception) {
            false
        } finally {
            try { inStream.close() } catch (_: Exception) {}
            conn?.disconnect()
        }
    }

    private fun resolveFileName(uri: Uri): String? {
        // ساده: از انتهای مسیر/lastPathSegment
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
