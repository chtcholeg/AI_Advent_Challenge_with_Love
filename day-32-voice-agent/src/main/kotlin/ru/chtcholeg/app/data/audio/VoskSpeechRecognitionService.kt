package ru.chtcholeg.app.data.audio

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Minimal JNA binding for Vosk — declares only the functions we actually call.
 *
 * Root cause of UnsatisfiedLinkError: the high-level org.vosk.LibVosk uses
 * Native.register() which tries to bind EVERY declared native method at class
 * load time. On macOS the bundled libvosk.dylib doesn't export
 * vosk_recognizer_set_grm (grammar support), so the whole static initializer
 * crashes. By bypassing org.vosk.* entirely and defining our own minimal
 * interface we avoid that registration, load the same native library,
 * and bind only the functions that exist.
 */
private interface VoskLib : Library {
    fun vosk_model_new(path: String): Pointer?
    fun vosk_model_free(model: Pointer)
    fun vosk_recognizer_new(model: Pointer, sample_rate: Float): Pointer?
    fun vosk_recognizer_free(recognizer: Pointer)
    fun vosk_recognizer_accept_waveform(recognizer: Pointer, data: ByteArray, length: Int): Int
    fun vosk_recognizer_partial_result(recognizer: Pointer): String
    fun vosk_recognizer_final_result(recognizer: Pointer): String
    fun vosk_recognizer_reset(recognizer: Pointer)
}

class VoskSpeechRecognitionService {

    private var lib: VoskLib? = null
    private var model: Pointer? = null
    private var recognizer: Pointer? = null

    var isReady: Boolean = false
        private set

    private var hasProcessedAudio = false

    private val modelDir: File
        get() {
            val home = System.getProperty("user.home")
            return File("$home/.config/voice-agent/models/vosk-model-small-ru-0.22")
        }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (!modelDir.exists()) {
            downloadAndExtractModel()
        }

        // Native.load binds only the methods declared in VoskLib,
        // so vosk_recognizer_set_grm is never looked up.
        val loadedLib = Native.load("vosk", VoskLib::class.java)
        lib = loadedLib

        val m = loadedLib.vosk_model_new(modelDir.absolutePath)
            ?: throw IllegalStateException("Не удалось загрузить модель Vosk из ${modelDir.absolutePath}")
        model = m

        val rec = loadedLib.vosk_recognizer_new(m, 16000.0f)
            ?: throw IllegalStateException("Не удалось создать Vosk Recognizer")
        recognizer = rec

        isReady = true
    }

    fun acceptAudioChunk(audioBytes: ByteArray): String? {
        val rec = recognizer ?: return null
        val l = lib ?: return null
        l.vosk_recognizer_accept_waveform(rec, audioBytes, audioBytes.size)
        hasProcessedAudio = true
        val partial = parseJson(l.vosk_recognizer_partial_result(rec), "partial")
        return partial.takeIf { it.isNotBlank() }
    }

    fun getFinalResult(): String {
        if (!hasProcessedAudio) return ""
        val rec = recognizer ?: return ""
        val l = lib ?: return ""
        return parseJson(l.vosk_recognizer_final_result(rec), "text")
    }

    fun reset() {
        val rec = recognizer ?: return
        lib?.vosk_recognizer_reset(rec)
        hasProcessedAudio = false
    }

    private fun parseJson(json: String, key: String): String = try {
        Json.parseToJsonElement(json).jsonObject[key]?.jsonPrimitive?.content ?: ""
    } catch (_: Exception) {
        ""
    }

    private fun downloadAndExtractModel() {
        val modelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip"
        val parentDir = modelDir.parentFile
        parentDir.mkdirs()

        val zipFile = File(parentDir, "vosk-model-small-ru-0.22.zip")
        URL(modelUrl).openStream().use { input ->
            FileOutputStream(zipFile).use { output ->
                input.copyTo(output)
            }
        }

        ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outFile = File(parentDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { zip.copyTo(it) }
                }
                entry = zip.nextEntry
            }
        }
        zipFile.delete()
    }
}
