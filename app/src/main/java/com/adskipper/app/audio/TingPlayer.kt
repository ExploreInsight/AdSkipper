package com.adskipper.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.adskipper.app.R

class TingPlayer(private val context: Context) {

    private val TAG = "AdSkip:Ting"
    private var mediaPlayer: MediaPlayer? = null

    fun playTing() {
        try {
            release()

            mediaPlayer = MediaPlayer().apply {
                val afd = context.resources.openRawResourceFd(R.raw.ting)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()

                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )

                setVolume(0.4f, 0.4f)

                setOnCompletionListener {
                    release()
                }

                setOnPreparedListener {
                    it.start()
                    Log.d(TAG, "🔔 Ting played!")
                }

                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play ting", e)
        }
    }

    fun release() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
    }
}
