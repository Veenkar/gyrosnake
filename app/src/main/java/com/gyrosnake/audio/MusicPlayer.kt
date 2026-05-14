package com.gyrosnake.audio

import android.content.Context
import android.media.MediaPlayer

/**
 * Strategy + State pattern: a single generic music player that holds one MediaPlayer at a time.
 *
 * Callers pass a raw resource ID to [play] — if the same track is already playing, it
 * resumes; if a different track is requested, the old one is released and the new one starts
 * from the beginning. This makes it trivial to add new soundtrack tracks without changing
 * any logic here — only the caller's routing table (see GameViewModel.resolveTrack) needs
 * updating.
 *
 * All tracks are looped automatically. Short one-shot sound effects belong in SoundManager.
 *
 * Internal 3-state machine (PLAYING, PAUSED, STOPPED) shields callers from MediaPlayer's
 * own complex state transitions. All commands are safe to call from any state.
 *
 * Uses pause+seekTo(0) instead of MediaPlayer.stop() to stay in the Paused state,
 * so a subsequent play() can call start() directly without a fresh prepare() cycle.
 */
class MusicPlayer(private val context: Context) {

    private enum class State { PLAYING, PAUSED, STOPPED }

    private var player: MediaPlayer? = null
    private var currentResId: Int = 0
    private var state = State.STOPPED

    /**
     * Plays [resId]:
     * - Same track, already playing → no-op.
     * - Same track, paused or stopped → resume / restart from current position.
     * - Different track → release old player, create and start new one from the beginning.
     */
    fun play(resId: Int) {
        if (resId == currentResId) {
            if (state != State.PLAYING) {
                player?.start()
                state = State.PLAYING
            }
            return
        }
        player?.release()
        currentResId = resId
        player = MediaPlayer.create(context, resId)?.apply { isLooping = true }
        player?.start()
        state = State.PLAYING
    }

    /** Suspends playback, preserving track position for seamless resume. No-op if not playing. */
    fun pause() {
        if (state == State.PLAYING) {
            player?.pause()
            state = State.PAUSED
        }
    }

    /** Stops and rewinds so the next play() starts the track from the beginning. */
    fun stop() {
        if (state != State.STOPPED) {
            player?.pause()
            player?.seekTo(0)
            state = State.STOPPED
        }
    }

    /** Releases the underlying audio session. Must be called from ViewModel.onCleared(). */
    fun release() {
        player?.release()
        player = null
        currentResId = 0
        state = State.STOPPED
    }
}
