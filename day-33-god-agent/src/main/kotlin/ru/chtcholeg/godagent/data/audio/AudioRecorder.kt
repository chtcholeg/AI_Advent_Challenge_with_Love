package ru.chtcholeg.godagent.data.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

class AudioRecorder {

    // Vosk requires 16 kHz, 16-bit, mono, signed, little-endian
    val format = AudioFormat(16000f, 16, 1, true, false)

    private var line: TargetDataLine? = null

    val isRecording: Boolean get() = line?.isRunning == true

    fun startRecording(): Flow<ByteArray> = flow {
        val info = DataLine.Info(TargetDataLine::class.java, format)
        if (!AudioSystem.isLineSupported(info)) {
            throw IllegalStateException("Микрофон недоступен на этом устройстве")
        }
        val targetLine = (AudioSystem.getLine(info) as TargetDataLine).also { line = it }
        targetLine.open(format)
        targetLine.start()

        val buffer = ByteArray(4000)
        try {
            while (targetLine.isOpen) {
                val count = targetLine.read(buffer, 0, buffer.size)
                if (count > 0) emit(buffer.copyOf(count))
            }
        } finally {
            targetLine.stop()
            targetLine.close()
            line = null
        }
    }.flowOn(Dispatchers.IO)

    fun stopRecording() {
        line?.stop()
        line?.close()
        line = null
    }
}
