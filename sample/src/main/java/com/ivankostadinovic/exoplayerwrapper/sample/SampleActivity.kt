package com.ivankostadinovic.exoplayerwrapper.sample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.google.android.exoplayer2.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.ivankostadinovic.exoplayerwrapper.ExoPlayerWrapper
import com.ivankostadinovic.exoplayerwrapper.sample.databinding.ActivitySampleBinding
import okhttp3.OkHttpClient

class SampleActivity : AppCompatActivity() {
    lateinit var playerWrapper: ExoPlayerWrapper
    lateinit var binding: ActivitySampleBinding

    private val streamUrl = "https://srv1.adriatelekom.com/PrvaTVHD/index.m3u8?token=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwczpcL1wvdC1hZHJpYS5jb21cL2FwaVwvbG9naW4iLCJpYXQiOjE2MzA5NDc1ODIsIm5iZiI6MTYzMDk0NzU4MiwianRpIjoiY2hMZ0txcW51UHVtdE1mbSIsInN1YiI6IkFSRU5BQkFUTyIsInBydiI6IjIzYmQ1Yzg5NDlmNjAwYWRiMzllNzAxYzQwMDg3MmRiN2E1OTc2ZjciLCJkZXZpY2VfaWQiOjE3ODYsImlwIjoiMTg1LjI2LjExOS4xMTQiLCJ1c2VyX2lkIjo0fQ.4bC3keDnDFFgDwwOnE6emUYH9JCgRTvyVVqq30ENzWk"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySampleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setUpPlayerWrapper()
        setUpButtonClicks()
        playMedia()
    }

    private fun playMedia() {
        playerWrapper.playMedia(streamUrl)
    }

    private fun setUpButtonClicks() {
        binding.btnReloadCurrentMedia.setOnClickListener { playerWrapper.reloadCurrentMedia() }
    }

    private fun setUpPlayerWrapper() {
        playerWrapper = ExoPlayerWrapper
            .Builder(this, binding.playerView)
            .setListener(getExoPlayerListener())
            .setConnectionListener(getConnectionListener())
            .setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON) // for this to work you have to import the extension you wish to use
            .setHandleLifecycleEvents(true, this)
            .setLoggingEnabled(BuildConfig.DEBUG) // this sets logging enabled only for the debug build type
            .setPreferredTrackLanguage("en") // sets preferred audio/subtitle language to English
            .setOkHttpClient(getOkHttpClient())
            .setTrackSelectionButtons(
                binding.btnSelectAudioTrack,
                binding.btnSelectVideoTrack,
                binding.btnSelectSubtitleTrack
            )
            .build()
    }

    private fun getOkHttpClient(): OkHttpClient {
        return OkHttpClient()
    }

    private fun getExoPlayerListener(): Player.Listener {
        return object : Player.Listener {
            override fun onVolumeChanged(volume: Float) {
                super.onVolumeChanged(volume)
                showMsg(getString(R.string.volume_changed))
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                showMsg(getString(R.string.media_item_transition))
            }
        }
    }

    private fun showMsg(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun getConnectionListener(): ExoPlayerWrapper.ConnectionListener {
        return object : ExoPlayerWrapper.ConnectionListener {
            override fun onConnectionError() {
                showMsg(getString(R.string.connection_error))
            }

            override fun onConnectionReturned() {
                showMsg(getString(R.string.connection_returned))
            }
        }
    }
}