package com.ultra.keyboard

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Upisuje log poruke u fajl na uređaju (pored standardnog Logcat-a), tako da
 * neko ko nema adb/Android Studio može jednostavno da POŠALJE taj fajl
 * (preko dugmeta u aplikaciji) umesto da nam vuče logcat.
 */
object AppLogger {
    private const val FILE_NAME = "ultra_keyboard_log.txt"
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun append(context: Context, tag: String, message: String) {
        try {
            val file = getLogFile(context)
            FileWriter(file, true).use { writer ->
                writer.append("${timeFormat.format(Date())} $tag: $message\n")
            }
        } catch (e: Exception) {
            // Nikad ne rušimo tastaturu zbog problema sa logovanjem
        }
    }

    fun getLogFile(context: Context): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, FILE_NAME)
    }

    @Synchronized
    fun clear(context: Context) {
        try {
            getLogFile(context).writeText("")
        } catch (e: Exception) {
            // ignorišemo
        }
    }
}
