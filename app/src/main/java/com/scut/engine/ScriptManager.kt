package com.scut.engine

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException

class ScriptManager(private val context: Context) {

    // getExternalFilesDir can return null (storage unavailable), so fall back
    private val scriptsDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "scripts").apply { mkdirs() }

    fun ensureExamplesCopied(assetNames: List<String>) {
        val dir = scriptsDir
        if (dir.listFiles()?.isNotEmpty() == true) return

        assetNames.forEach { name ->
            try {
                context.assets.open("scripts/$name").use { input ->
                    File(dir, name).outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: IOException) {
                Log.w("SCUT", "could not copy example $name", e)
            }
        }
    }

    fun listScripts(): List<File> =
        scriptsDir.listFiles { file -> file.extension == "lua" }
            ?.sortedBy { it.name }
            ?: emptyList()

    fun directoryPath(): String = scriptsDir.absolutePath
}
