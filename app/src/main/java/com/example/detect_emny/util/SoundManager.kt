package com.example.detect_emeny.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

class SoundManager(context: Context) {
    private val soundPool: SoundPool
    private var sliceSoundId: Int = -1
    private var bombSoundId: Int = -1

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        try {
            // Load sounds from assets/sound_play/ folder
            // Assuming the files are named slice.mp3 and bomb.mp3
            context.assets.openFd("sound_play/slice.mp3").use {
                sliceSoundId = soundPool.load(it, 1)
            }
            context.assets.openFd("sound_play/bomb.mp3").use {
                bombSoundId = soundPool.load(it, 1)
            }
        } catch (e: Exception) {
            Log.e("SoundManager", "Error loading sound effects: ${e.message}")
        }
    }

    fun playSlice() {
        if (sliceSoundId != -1) {
            soundPool.play(sliceSoundId, 1.0f, 1.0f, 0, 0, 1.0f)
        }
    }

    fun playBomb() {
        if (bombSoundId != -1) {
            soundPool.play(bombSoundId, 1.0f, 1.0f, 0, 0, 1.0f)
        }
    }

    fun release() {
        soundPool.release()
    }
}
