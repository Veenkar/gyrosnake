package com.gyrosnake.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Singleton pattern: one shared audio context for the entire app lifetime.
 * All retro sounds are generated procedurally (square waves via AudioTrack),
 * so zero binary audio assets are needed.
 */
object SoundManager {

    private const val SAMPLE_RATE = 22050  // Hz — low rate for lo-fi retro feel
    private const val VOLUME      = 0.45f  // master volume (0..1)

    // --- Procedural sound generation ---

    /**
     * Template Method pattern: generates a square-wave tone of [freqHz] Hz
     * lasting [durationMs] milliseconds.  Square waves have a naturally harsh,
     * retro chip-tune character.
     */
    private fun squareWave(freqHz: Float, durationMs: Int, vol: Float = VOLUME): ByteArray {
        val numSamples = SAMPLE_RATE * durationMs / 1000
        val halfPeriod = (SAMPLE_RATE / freqHz / 2).toInt().coerceAtLeast(1)
        val hi = (Short.MAX_VALUE * vol).toInt().toShort()
        val lo = (-Short.MAX_VALUE * vol).toInt().toShort()
        val bytes = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val s = if ((i / halfPeriod) % 2 == 0) hi else lo
            bytes[i * 2]     = (s.toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (s.toInt() shr 8 and 0xFF).toByte()
        }
        return bytes
    }

    private fun play(bytes: ByteArray) {
        Thread {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bytes.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(bytes, 0, bytes.size)
            track.play()
            val durationMs = bytes.size.toLong() * 1000 / (SAMPLE_RATE * 2)
            Thread.sleep(durationMs + 80)
            track.release()
        }.start()
    }

    // --- Public sound API (Observer hook points called by GameEngine callbacks) ---

    /** Short high blip — played when snake eats food. */
    fun playEat(vol: Float = 1f) { if (vol > 0f) play(squareWave(880f, 70, VOLUME * vol)) }

    /** Descending two-tone — played on death. */
    fun playDie(vol: Float = 1f) {
        if (vol <= 0f) return
        Thread {
            play(squareWave(330f, 120, VOLUME * vol))
            Thread.sleep(100)
            play(squareWave(220f, 280, VOLUME * vol))
        }.start()
    }

    /** Ascending arpeggio — played when a new game starts. */
    fun playStart(vol: Float = 1f) {
        if (vol <= 0f) return
        Thread {
            listOf(440f, 554f, 659f, 880f).forEach { f ->
                play(squareWave(f, 70, VOLUME * vol))
                Thread.sleep(70)
            }
        }.start()
    }
}
