package com.ivankostadinovic.exoplayerwrapper.sample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
import com.ivankostadinovic.exoplayerwrapper.ConnectionListener
import com.ivankostadinovic.exoplayerwrapper.ExoPlayerWrapper
import com.ivankostadinovic.exoplayerwrapper.helper.is403Forbidden
import com.ivankostadinovic.exoplayerwrapper.sample.databinding.ActivitySampleBinding
import okhttp3.OkHttpClient

class SampleActivity : AppCompatActivity() {
    lateinit var playerWrapper: ExoPlayerWrapper
    lateinit var binding: ActivitySampleBinding

    private var streamUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_sample)
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

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                if(error.is403Forbidden()) {
                    streamUrl = ""
                    playMedia()
                }
            }
        }
    }

    private fun showMsg(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun getConnectionListener(): ConnectionListener {
        return object : ConnectionListener {
            override fun onConnectionError() {
                showMsg(getString(R.string.connection_error))
            }

            override fun onConnectionReturned() {
                showMsg(getString(R.string.connection_returned))
            }
        }
    }
}