package com.scut.engine

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

// Owns the native engine so it survives rotation / theme changes, and runs
// scripts on one background thread so a slow script can't freeze the UI.
class EngineViewModel : ViewModel() {

    private val worker = Executors.newSingleThreadExecutor()
    private var engine: NativeEngine? = null

    var status by mutableStateOf("Engine starting")
        private set

    init {
        status = try {
            val created = NativeEngine()
            created.create()
            engine = created
            "Engine ready \u00b7 ${created.nodeCount()} nodes"
        } catch (e: LinkageError) {
            Log.e(TAG, "native library unavailable", e)
            "Native library could not be loaded on this device"
        } catch (e: RuntimeException) {
            Log.e(TAG, "engine failed to start", e)
            "Engine failed to start: ${e.message}"
        }
    }

    fun runScript(file: File) {
        val current = engine
        if (current == null) {
            status = "Engine is not running"
            return
        }
        status = "Running ${file.name}..."
        try {
            worker.execute { status = execute(current, file) }
        } catch (e: RejectedExecutionException) {
            status = "Engine is shutting down"
        }
    }

    private fun execute(engine: NativeEngine, file: File): String {
        if (file.length() > MAX_SCRIPT_BYTES) return "${file.name}: larger than 1 MB"
        val source = try {
            file.readText()
        } catch (e: IOException) {
            return "${file.name}: cannot read file"
        }
        return try {
            val error = engine.runScript(source)
            if (error == null) {
                "${file.name}: ok \u00b7 ${engine.nodeCount()} nodes"
            } else {
                "${file.name}: failed - $error"
            }
        } catch (e: RuntimeException) {
            "${file.name}: ${e.message}"
        }
    }

    override fun onCleared() {
        worker.shutdownNow()
        engine?.destroy()
    }

    private companion object {
        const val TAG = "SCUT"
        const val MAX_SCRIPT_BYTES = 1L shl 20
    }
}
