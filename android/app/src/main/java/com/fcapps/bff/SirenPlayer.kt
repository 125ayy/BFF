package com.fcapps.bff

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates a wailing siren in real-time using AudioTrack.
 * Frequency sweeps between 700 Hz and 1700 Hz every ~0.8 seconds.
 */
class SirenPlayer {

    @Volatile private var playing = false
    private var thread: Thread? = null
    private var audioTrack: AudioTrack? = null

    fun start() {
        if (playing) return
        playing = true
        thread = Thread {
            val sampleRate = 44100
            val bufferSize = maxOf(
                AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
                sampleRate / 10  // at least 100ms
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.setVolume(AudioTrack.getMaxVolume())
            audioTrack?.play()

            val buffer = ShortArray(bufferSize)
            var phase = 0.0
            var t = 0.0
            val dt = 1.0 / sampleRate
            // Sweep cycle: 0.8s up + 0.8s down = 1.6s total per wail
            val sweepRate = 1.0 / 1.6

            while (playing) {
                for (i in buffer.indices) {
                    // Oscillate freq between 700 Hz and 1700 Hz
                    val sweep = 0.5 - 0.5 * sin(2.0 * PI * sweepRate * t)  // 0..1
                    val freq = 700.0 + 1000.0 * sweep
                    phase += 2.0 * PI * freq / sampleRate
                    if (phase > 2.0 * PI) phase -= 2.0 * PI
                    buffer[i] = (Short.MAX_VALUE * 0.9 * sin(phase)).toInt().toShort()
                    t += dt
                }
                audioTrack?.write(buffer, 0, buffer.size)
            }

            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        }.also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        playing = false
        audioTrack?.pause()  // unblocks write() immediately
        thread?.join(2000)
        thread = null
    }
}
