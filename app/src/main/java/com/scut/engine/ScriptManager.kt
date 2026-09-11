package com.scut.engine

import android.content.Context
import java.io.File

// Effect scripts live under
class ScriptManager(private val context: Context) {

    private val scriptsDir: File
        get() = File(context.getExternalFilesDir(null), "scripts").apply { mkdirs() }

    fun ensureExamplesCopied(assetNames: List<String>) {
        val dir = scriptsDir
        if (dir.listFiles()?.isNotEmpty() == true) return

        assetNames.forEach { name ->
            context.assets.open("scripts/$name").use { input ->
                File(dir, name).outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    fun listScripts(): List<File> =
        scriptsDir.listFiles { file -> file.extension == "lua" }
            ?.sortedBy { it.name }
            ?: emptyList()

    fun read(name: String): String = File(scriptsDir, name).readText()

    /** Where to point a file manager / "open with" intent if you want to jump straight there. */
    fun directoryPath(): String = scriptsDir.absolutePath
}
