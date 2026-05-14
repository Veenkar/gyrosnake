package com.gyrosnake.audio

import android.content.Context
import android.media.MediaPlayer

/**
 * Strategy + State pattern: a single generic music player that holds one MediaPlayer at a time.
 *
 * Callers pass a raw resource ID to [play] — if the same track is already playing, it
 * resumes; if a different track is requested, the old one is released and the new one starts
 * at the same playback offset (Memento pattern: position is captured before release and
 * restored on the incoming track). If the offset overshoots the new track's duration,
 * playback starts from the beginning instead.
 *
 * This keeps paired tracks (e.g. normalsnake / discosnake / leafsnake) perceptually in sync
 * across powerup transitions without any coordination from the caller.
 *
 * All tracks loop automatically. Short one-shot sound effects belong in SoundManager.
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
    // True only while a track that participates in cross-track position sync is loaded.
    // Tracks marked startFromBeginning opt out of sync both when entered and exited.
    private var syncable = false

    /**
     * Plays [resId] at [volume] (0.0–1.0, linear amplitude).
     *
     * [startFromBeginning] = true: always start this track from 0, and do not carry
     * its playback position forward to the next track (opts out of Memento sync in
     * both directions). Use for tracks like Leaf that are independent of the main loop.
     *
     * [startFromBeginning] = false (default): participate in cross-track sync —
     * Memento captures the outgoing position and seeks the incoming track to the same
     * offset, keeping paired tracks (e.g. normalsnake / discosnake) perceptually aligned.
     * Falls back to 0 if the offset overshoots the new track's duration, or if the
     * outgoing track had opted out of sync.
     */
    fun play(resId: Int, volume: Float = 1f, startFromBeginning: Boolean = false) {
        if (resId == currentResId) {
            player?.setVolume(volume, volume)   // always sync volume (slider changes land here)
            if (state != State.PLAYING) {
                player?.start()
                state = State.PLAYING
            }
            return
        }
        // Memento: only carry offset when both outgoing and incoming tracks participate.
        val offsetMs = if (syncable && !startFromBeginning) player?.currentPosition ?: 0 else 0
        player?.release()
        currentResId = resId
        syncable = !startFromBeginning
        player = MediaPlayer.create(context, resId)?.apply {
            isLooping = true
            setVolume(volume, volume)
            val targetMs = if (offsetMs in 1 until duration) offsetMs else 0
            seekTo(targetMs)
        }
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
