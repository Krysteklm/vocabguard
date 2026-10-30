package com.vocabguard.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Available to any composable inside VocabGuard's activities. Null in previews. */
val LocalSoundPlayer = staticCompositionLocalOf<SoundPlayer?> { null }

/**
 * Short, soft UI sounds synthesised on the device (no audio files, no network).
 *
 * - Plays through the media volume, like games and learning apps do.
 * - Stays silent when the phone is on silent or vibrate, and when the user turns sounds off.
 * - Everything runs on one background thread; a failure just means no sound, never a crash.
 */
class SoundPlayer(context: Context) {

    enum class Sound { Tap, Correct, Wrong, Unlock }

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
    private val tracks = ConcurrentHashMap<Sound, AudioTrack>()

    /** Set from the stored setting; off until it has loaded. */
    @Volatile
    var enabled: Boolean = false

    init {
        scope.launch {
            Sound.entries.forEach { sound ->
                try {
                    tracks[sound] = buildTrack(SoundSynth.render(sound))
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't prepare $sound", e)
                }
            }
        }
    }

    /** [force] plays even when sounds are off (used for the preview when turning them on). */
    fun play(sound: Sound, force: Boolean = false) {
        if (!enabled && !force) return
        if (audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        scope.launch {
            val track = tracks[sound] ?: return@launch
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
                track.reloadStaticData()
                track.play()
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't play $sound", e)
            }
        }
    }

    fun release() {
        scope.cancel()
        tracks.values.forEach { runCatching { it.release() } }
        tracks.clear()
        executor.shutdown()
    }

    private fun buildTrack(pcm: ShortArray): AudioTrack =
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SoundSynth.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
            .also { it.write(pcm, 0, pcm.size) }

    private companion object {
        const val TAG = "SoundPlayer"
    }
}

/** Tiny additive synth: bell-like notes with a soft attack and exponential decay. */
internal object SoundSynth {
    const val SAMPLE_RATE = 44_100

    private data class Note(
        val frequency: Double,
        val startMs: Int,
        val durationMs: Int,
        val volume: Double,
        /** 0 = pure sine (soft), 1 = brighter bell. */
        val brightness: Double = 0.5,
    )

    // Note frequencies (Hz)
    private const val C6 = 1046.50
    private const val E6 = 1318.51
    private const val G6 = 1567.98
    private const val A6 = 1760.00
    private const val C7 = 2093.00
    private const val E4 = 329.63
    private const val B3 = 246.94

    fun render(sound: SoundPlayer.Sound): ShortArray = when (sound) {
        // A quiet wooden "tick".
        SoundPlayer.Sound.Tap -> mix(listOf(Note(1800.0, 0, 35, 0.12, brightness = 0.2)))
        // Two rising notes: the classic "that's right" chime.
        SoundPlayer.Sound.Correct -> mix(
            listOf(
                Note(E6, 0, 260, 0.30),
                Note(A6, 85, 420, 0.32),
            ),
        )
        // A soft, low, falling "bonk". Clear, but never harsh.
        SoundPlayer.Sound.Wrong -> mix(
            listOf(
                Note(E4, 0, 180, 0.30, brightness = 0.1),
                Note(B3, 120, 300, 0.30, brightness = 0.1),
            ),
        )
        // A rising arpeggio with a long, sparkling final note.
        SoundPlayer.Sound.Unlock -> mix(
            listOf(
                Note(C6, 0, 300, 0.24),
                Note(E6, 70, 300, 0.24),
                Note(G6, 140, 320, 0.25),
                Note(C7, 210, 900, 0.28, brightness = 0.8),
                Note(C7 * 2, 230, 600, 0.06, brightness = 0.3),
            ),
        )
    }

    private fun mix(notes: List<Note>): ShortArray {
        val totalMs = notes.maxOf { it.startMs + it.durationMs }
        val buffer = DoubleArray(totalMs * SAMPLE_RATE / 1000)
        for (note in notes) {
            val start = note.startMs * SAMPLE_RATE / 1000
            val length = note.durationMs * SAMPLE_RATE / 1000
            val attack = (0.004 * SAMPLE_RATE).toInt() // 4 ms: no click, still crisp
            val decay = length / 4.0
            for (i in 0 until min(length, buffer.size - start)) {
                val t = i.toDouble() / SAMPLE_RATE
                val envelope = (if (i < attack) i.toDouble() / attack else 1.0) * exp(-i / decay)
                val w = 2 * PI * note.frequency * t
                val tone = sin(w) +
                    note.brightness * 0.35 * sin(2 * w) * exp(-i / (decay / 2)) +
                    note.brightness * 0.12 * sin(3 * w) * exp(-i / (decay / 3))
                buffer[start + i] += tone * envelope * note.volume
            }
        }
        // Short fade-out on the last 10 ms so nothing ever ends with a click.
        val fade = min(buffer.size, SAMPLE_RATE / 100)
        for (i in 0 until fade) buffer[buffer.size - 1 - i] *= i.toDouble() / fade
        return ShortArray(buffer.size) { i ->
            (buffer[i].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
