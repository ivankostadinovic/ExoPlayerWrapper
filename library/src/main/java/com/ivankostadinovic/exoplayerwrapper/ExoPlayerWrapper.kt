package com.ivankostadinovic.exoplayerwrapper

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.Uri
import android.view.View
import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.C.DEFAULT_SEEK_BACK_INCREMENT_MS
import androidx.media3.common.C.DEFAULT_SEEK_FORWARD_INCREMENT_MS
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.MappingTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import com.google.common.collect.ImmutableList
import com.ivankostadinovic.exoplayerwrapper.helper.Utils.getColor
import com.ivankostadinovic.exoplayerwrapper.helper.Utils.getConnectivityManager
import com.ivankostadinovic.exoplayerwrapper.helper.Utils.getNetworkRequest
import com.ivankostadinovic.exoplayerwrapper.helper.Utils.isInternetAvailable
import com.ivankostadinovic.exoplayerwrapper.helper.Utils.runOnUiThread
import com.ivankostadinovic.exoplayerwrapper.helper.is403Forbidden
import okhttp3.OkHttpClient

/**
 * An [ExoPlayer] implementation. Instances can be obtained from [ExoPlayerWrapper.Builder].
 */
@Suppress("unused")
@OptIn(UnstableApi::class)
class ExoPlayerWrapper private constructor(
    private val ctx: Context,
    loggingEnabled: Boolean,
    handleLifecycleEvents: Boolean,
    lifecycleOwner: LifecycleOwner?,
    extensionRendererMode: Int,
    private val playerView: PlayerView?,
    listener: Player.Listener?,
    private val connectionListener: ConnectionListener?,
    preferredTrackLanguage: String?,
    private val btnSelectAudioTrack: View?,
    private val btnSelectVideoTrack: View?,
    private val btnSelectSubtitleTrack: View?,
    okHttpClient: OkHttpClient?,
    seekForwardIncrementMs: Long,
    seekBackwardIncrementMs: Long
) : DefaultLifecycleObserver, Player.Listener {

    companion object {
        const val UNDEFINED: Long = -1
    }

    val player: ExoPlayer
    val hlsFactory: HlsMediaSource.Factory
    val dashFactory: DashMediaSource.Factory
    val progressiveFactory: ProgressiveMediaSource.Factory
    val ssFactory: SsMediaSource.Factory
    val rtspFactory: RtspMediaSource.Factory
    val trackSelector: DefaultTrackSelector

    // current state
    var wasPlaying = true
    var noInternetErrorShowing = false

    var currentMediaSource: MediaSource? = null
    var currentMediaUri: Uri? = null
    var positionWhenErrorOcurred = UNDEFINED

    private lateinit var networkRequest: NetworkRequest
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    init {
        if (handleLifecycleEvents) {
            lifecycleOwner?.lifecycle?.addObserver(this)
        }

        btnSelectAudioTrack?.setOnClickListener { _: View? -> showAudioTrackSelectionDialog(ctx) }
        btnSelectVideoTrack?.setOnClickListener { _: View? -> showVideoTrackSelectionDialog(ctx) }
        btnSelectSubtitleTrack?.setOnClickListener { _: View? ->
            showSubtitleTrackSelectionDialog(
                ctx
            )
        }

        val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(
            ctx,
            getNetworkDataSourceFactory(okHttpClient)
        )

        val defaultRenderersFactory = DefaultRenderersFactory(ctx)
            .setExtensionRendererMode(extensionRendererMode)

        trackSelector = DefaultTrackSelector(ctx)
        trackSelector.setParameters(
            trackSelector
                .buildUponParameters()
                .setPreferredAudioLanguage(preferredTrackLanguage)
                .setPreferredTextLanguage(preferredTrackLanguage)
        )

        val errorHandlingPolicy: DefaultLoadErrorHandlingPolicy =
            object : DefaultLoadErrorHandlingPolicy() {
                override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                    handleInternetError()
                    return super.getRetryDelayMsFor(loadErrorInfo)
                }


            }

//        val defaultHlsExtractorFactory = DefaultHlsExtractorFactory(
//            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES,
//            false
//        )

        hlsFactory = HlsMediaSource
            .Factory(dataSourceFactory)
            //.setExtractorFactory(defaultHlsExtractorFactory)
            .setLoadErrorHandlingPolicy(errorHandlingPolicy)
            //.setAllowChunklessPreparation(true)

        progressiveFactory = ProgressiveMediaSource
            .Factory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(errorHandlingPolicy)

        dashFactory = DashMediaSource
            .Factory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(errorHandlingPolicy)

        ssFactory = SsMediaSource
            .Factory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(errorHandlingPolicy)

        rtspFactory = RtspMediaSource
            .Factory()
            .setLoadErrorHandlingPolicy(errorHandlingPolicy)

//        val loadControl = DefaultLoadControl.Builder()
//            .setBufferDurationsMs(
//                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
//                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
//                500,
//                2000
//            )
//            .setPrioritizeTimeOverSizeThresholds(true)
//            .build()

        player = ExoPlayer.Builder(ctx, defaultRenderersFactory)
            .setTrackSelector(trackSelector)
            //.setLoadControl(loadControl)
            .setSeekForwardIncrementMs(seekForwardIncrementMs)
            .setSeekBackIncrementMs(seekBackwardIncrementMs)
            .setReleaseTimeoutMs(5000) // sometimes releasing player takes a bit longer and would cause errors in the background
            .build()

        playerView?.let {
            it.player = player
            it.subtitleView?.setStyle(
                CaptionStyleCompat(
                    getColor(R.color.subtitle_color, ctx), // subtitle text color
                    getColor(android.R.color.transparent, ctx), // subtitle background color
                    getColor(android.R.color.transparent, ctx), // subtitle window color
                    CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, // subtitle edge type
                    getColor(android.R.color.black, ctx), // subtitle edge color
                    null
                )
            )
        }

        if (loggingEnabled) {
            player.addAnalyticsListener(EventLogger())
        }

        player.addListener(this)
        listener?.let {
            player.addListener(it)
        }

        setUpInternetListener()
    }

    private fun getNetworkDataSourceFactory(okHttpClient: OkHttpClient?): DataSource.Factory {
        val userAgent = Util.getUserAgent(ctx, ctx.packageName)

        return if (okHttpClient != null) {
            OkHttpDataSource.Factory(okHttpClient)
                .setUserAgent(userAgent)
        } else {
            DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
        }
    }

    private fun handleInternetError(): Boolean {
        return try {
            if (isInternetAvailable(ctx)) {
                true
            } else {
                connectionListener?.let {
                    runOnUiThread {
                        if (player.playbackState != Player.STATE_READY) {
                            noInternetErrorShowing = true
                            connectionListener.onConnectionError()
                        }
                    }
                }
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    private fun setUpInternetListener() {
        networkRequest = getNetworkRequest()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (noInternetErrorShowing) {
                    connectionListener?.onConnectionReturned()
                }
                super.onAvailable(network)
            }
        }

        val cm = getConnectivityManager(ctx)
        cm?.registerNetworkCallback(networkRequest, networkCallback)
    }
    private fun unregisterNetworkCallback() {
        try {
            val cm = getConnectivityManager(ctx)
            cm?.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        player.release()
        playerView?.player = null
        unregisterNetworkCallback()
    }

    fun showSubtitleTrackSelectionDialog(ctx: Context) {
        try {
            TrackSelectionDialogBuilder(
                ctx,
                ctx.getString(R.string.select_subtitle_track),
                player,
                C.TRACK_TYPE_TEXT
            )
                .setTheme(R.style.DialogTheme)
                .setShowDisableOption(true)
                .build()
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showVideoTrackSelectionDialog(ctx: Context) {
        try {
            TrackSelectionDialogBuilder(
                ctx,
                ctx.getString(R.string.select_video_track),
                player,
                C.TRACK_TYPE_VIDEO
            )
                .setTheme(R.style.DialogTheme)
                .setShowDisableOption(true)
                .build()
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showAudioTrackSelectionDialog(ctx: Context) {
        try {
            TrackSelectionDialogBuilder(
                ctx,
                ctx.getString(R.string.select_audio_track),
                player,
                C.TRACK_TYPE_AUDIO
            )
                .setTheme(R.style.DialogTheme)
                .build()
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Play media from the given url
     *
     * @param url url
     */
    fun playMedia(url: String) {
        playMedia(Uri.parse(url))
    }

    /**
     * @param uri uri
     */
    fun playMedia(uri: Uri) {
        playMedia(uri, null)
    }

    /**
     * @param url play Media from the given url
     * @param tag optional tag for Player analytics
     */
    fun playMedia(url: String, tag: Any?) {
        playMedia(Uri.parse(url), tag)
    }

    /**
     * @param uri play Media from the given Uri
     * @param tag optional tag for Player analytics
     */
    fun playMedia(uri: Uri, tag: Any? = null) {
        if (positionWhenErrorOcurred != UNDEFINED && !onlyTokenChanged(uri)) {
            positionWhenErrorOcurred = UNDEFINED
        }
        currentMediaUri = uri
        currentMediaSource = getMediaSource(uri, tag)
        reloadCurrentMedia()
    }

    private fun onlyTokenChanged(uri: Uri?): Boolean {
        return uri.toString().substringBeforeLast("token=") ==
            currentMediaUri.toString().substringBeforeLast("token=")
    }

    /**
     * Reload the currently playing media
     */
    fun reloadCurrentMedia() {
        currentMediaSource?.let {
            player.setMediaSource(it)
            player.prepare()
            player.play()
        }
    }

    private fun getMediaSource(uri: Uri, tag: Any?): MediaSource {
        return when (Util.inferContentType(uri)) {
            C.CONTENT_TYPE_SS -> ssFactory.createMediaSource(createMediaItem(uri, tag))
            C.CONTENT_TYPE_DASH -> dashFactory.createMediaSource(createMediaItem(uri, tag))
            C.CONTENT_TYPE_HLS -> hlsFactory.createMediaSource(createMediaItem(uri, tag))
            C.CONTENT_TYPE_OTHER -> progressiveFactory.createMediaSource(createMediaItem(uri, tag))
            C.CONTENT_TYPE_RTSP -> rtspFactory.createMediaSource(createMediaItem(uri, tag))
            else -> {
                hlsFactory.createMediaSource(createMediaItem(uri, tag))
            }
        }
    }

    private fun createMediaItem(uri: Uri, tag: Any?): MediaItem {
        return MediaItem.Builder()
            .setTag(tag)
            .setUri(uri)
            .build()
    }

    fun play() {
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun stop() {
        player.stop()
    }

    // lifecycle listener methods
    override fun onStart(owner: LifecycleOwner) {
        if (currentMediaSource == null) {
            return
        }
        player.prepare()
        player.playWhenReady = wasPlaying
        playerView?.onResume()
        super.onStart(owner)
    }

    override fun onStop(owner: LifecycleOwner) {
        wasPlaying = player.playWhenReady
        player.stop()
        playerView?.onPause()
        super.onStop(owner)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        release()
        super.onDestroy(owner)
    }

    // exoplayer listener methods
    override fun onPlayerError(error: PlaybackException) {
        handlePlayerError(error)
    }

    override fun onTracksChanged(tracks: Tracks) {
        super.onTracksChanged(tracks)
        handleTracksChanged(tracks.groups)
    }

    override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_READY) {
            if (noInternetErrorShowing) {
                noInternetErrorShowing = false
                connectionListener?.onConnectionReturned()
            }
            if (positionWhenErrorOcurred != UNDEFINED) {
                player.seekTo(positionWhenErrorOcurred)
                positionWhenErrorOcurred = UNDEFINED
            }
        }
    }

    private fun handlePlayerError(error: PlaybackException) {
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_REMOTE_ERROR,
            PlaybackException.ERROR_CODE_TIMEOUT,
            PlaybackException.ERROR_CODE_UNSPECIFIED -> {
                positionWhenErrorOcurred = player.contentPosition
                if (error.is403Forbidden()) {
                    return
                }
                reloadCurrentMedia()
            }
            else -> error.printStackTrace()
        }
    }

    private fun handleTracksChanged(trackGroups: ImmutableList<Tracks.Group>) {
        var textFound = false
        var audioFound = false
        var videoFound = false
        for (i in 0 until trackGroups.size) {
            for (g in 0 until trackGroups[i].length) {
                trackGroups[i].getTrackFormat(g).sampleMimeType.let {
                    when {
                        MimeTypes.isAudio(it) -> {
                            audioFound = true
                        }
                        MimeTypes.isText(it) -> {
                            textFound = true
                        }
                        MimeTypes.isVideo(it) -> {
                            videoFound = true
                        }
                    }
                }
            }
        }
        btnSelectAudioTrack?.visibility = if (audioFound) View.VISIBLE else View.INVISIBLE
        btnSelectSubtitleTrack?.visibility = if (textFound) View.VISIBLE else View.INVISIBLE
        btnSelectVideoTrack?.visibility = if (videoFound) View.VISIBLE else View.INVISIBLE
    }

    class Builder {
        private val ctx: Context
        private var listener: Player.Listener? = null
        private var preferredTrackLanguage: String? = null
        private var btnSelectAudioTrack: View? = null
        private var btnSelectVideoTrack: View? = null
        private var btnSelectSubtitleTrack: View? = null

        private var playerView: PlayerView? = null
        private var connectionListener: ConnectionListener? = null
        private var lifecycleOwner: LifecycleOwner? = null
        private var handleLifecycleEvents = false
        private var extensionRendererMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
        private var loggingEnabled = false
        private var okHttpClient: OkHttpClient? = null
        private var seekForwardIncrementMs: Long = DEFAULT_SEEK_FORWARD_INCREMENT_MS
        private var seekBackwardIncrementMs: Long = DEFAULT_SEEK_BACK_INCREMENT_MS

        /**
         * @param ctx Context that will be used to initialize the player
         */
        constructor(ctx: Context) {
            this.ctx = ctx
        }

        /**
         * @param ctx        Context that will be used with the player
         * @param playerView PlayerView for which the player will be bound to
         */
        constructor(
            ctx: Context,
            playerView: PlayerView?
        ) {
            this.ctx = ctx
            this.playerView = playerView
        }

        /**
         * @param extensionRendererMode extension renderer mode
         * @return builder, for convenience
         */
        fun setExtensionRendererMode(extensionRendererMode: @DefaultRenderersFactory.ExtensionRendererMode Int): Builder {
            this.extensionRendererMode = extensionRendererMode
            return this
        }

        /**
         * @param loggingEnabled enables the default analytics listener, EventLogger. To see the logs from the player, filter the logcat output by "EventLogger".
         * @return builder, for convenience
         */
        fun setLoggingEnabled(loggingEnabled: Boolean): Builder {
            this.loggingEnabled = loggingEnabled
            return this
        }

        /**
         * @param listener for player events
         * @return builder, for convenience
         */
        fun setListener(listener: Player.Listener?): Builder {
            this.listener = listener
            return this
        }

        /**
         * @param preferredTrackLanguage Preferred audio language as an IETF BCP 47 conformant tag
         * @return builder, for convenience
         */
        fun setPreferredTrackLanguage(preferredTrackLanguage: String?): Builder {
            this.preferredTrackLanguage = preferredTrackLanguage
            return this
        }

        /**
         * @param btnAudio    a View which when clicked will show audio tracks dialog
         * @param btnVideo    a View which when clicked will show video tracks dialog
         * @param btnSubtitle a View which when clicked will show subtitle tracks dialog
         * @return builder, for convenience
         */
        fun setTrackSelectionButtons(
            btnAudio: View?,
            btnVideo: View?,
            btnSubtitle: View?
        ): Builder {
            btnSelectAudioTrack = btnAudio
            btnSelectVideoTrack = btnVideo
            btnSelectSubtitleTrack = btnSubtitle
            return this
        }

        /**
         * @param handleLifecycleEvents to automatically start, pause and release the player resources on lifecycle events
         * @param lifecycleOwner        lifecycle owner to which the player will be bound to
         * @return builder, for convenience
         */
        fun setHandleLifecycleEvents(
            handleLifecycleEvents: Boolean,
            lifecycleOwner: LifecycleOwner
        ): Builder {
            this.lifecycleOwner = lifecycleOwner
            this.handleLifecycleEvents = handleLifecycleEvents
            return this
        }

        /**
         * @param connectionListener callbacks for connection errors and connection returns
         * @return builder, for convenience
         */
        fun setConnectionListener(connectionListener: ConnectionListener?): Builder {
            this.connectionListener = connectionListener
            return this
        }

        /**
         * @param okHttpClient OkHttpClient which will be used for network calls
         * @return builder, for convenience
         */
        fun setOkHttpClient(okHttpClient: OkHttpClient?): Builder {
            this.okHttpClient = okHttpClient
            return this
        }

        /**
         * @param seekForwardIncrementMs forwardIncrementMs for seekbar
         * @param seekBackwardIncrementMs backwardIncrementMs for seekbar
         * @return builder, for convenience
         */
        fun setForwardBackwardIncrementMs(
            seekForwardIncrementMs: Long,
            seekBackwardIncrementMs: Long
        ): Builder {
            this.seekForwardIncrementMs = seekForwardIncrementMs
            this.seekBackwardIncrementMs = seekBackwardIncrementMs
            return this
        }

        fun build(): ExoPlayerWrapper {
            return ExoPlayerWrapper(
                ctx,
                loggingEnabled,
                handleLifecycleEvents,
                lifecycleOwner,
                extensionRendererMode,
                playerView,
                listener,
                connectionListener,
                preferredTrackLanguage,
                btnSelectAudioTrack,
                btnSelectVideoTrack,
                btnSelectSubtitleTrack,
                okHttpClient,
                seekForwardIncrementMs,
                seekBackwardIncrementMs
            )
        }
    }
}
