package com.ivankostadinovic.exoplayerwrapper;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.Uri;
import android.view.View;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.OnLifecycleEvent;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.DefaultHlsExtractorFactory;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.CaptionStyleCompat;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.ui.TrackSelectionDialogBuilder;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;

import timber.log.Timber;

import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_MAX_BUFFER_MS;
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_MIN_BUFFER_MS;
import static com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES;

public class ExoPlayerWrapper implements LifecycleObserver {
    public FragmentActivity ctx;
    public SimpleExoPlayer player;
    public HlsMediaSource.Factory hlsFactory;
    public DashMediaSource.Factory dashFactory;
    public ProgressiveMediaSource.Factory progressiveFactory;
    public SsMediaSource.Factory ssFactory;
    public RtspMediaSource.Factory rtspFactory;
    public DefaultTrackSelector trackSelector;
    public MediaSource currentMediaSource;
    public boolean wasPlaying = true, noInternetErrorShowing;

    @Nullable
    public Runnable internetReturnedRunnable, noInternetRunnable;

    @Nullable
    public View btnSelectAudioTrack, btnSelectVideoTrack, btnSelectSubtitleTrack;

    @Nullable
    public PlayerView playerView;

    private NetworkRequest networkRequest;
    private ConnectivityManager.NetworkCallback networkCallback;
    private ConnectivityManager connectivityManager;

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void lifecycleOnStart() {
        Timber.d("player onStart");
        ExoPlayerWrapper.this.onStart();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void lifecycleOnStop() {
        Timber.d("player onStop");
        ExoPlayerWrapper.this.onStop();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    public void lifecycleOnDestroy() {
        Timber.d("player onDestroy");
        ExoPlayerWrapper.this.release();
    }

    private ExoPlayerWrapper(@NonNull FragmentActivity ctx,
                             boolean handleLifecycleEvents,
                             @NonNull LifecycleOwner lifecycleOwner,
                             int extensionRendererMode,
                             @Nullable PlayerView playerView,
                             @Nullable Player.Listener listener,
                             @Nullable Runnable noInternetRunnable,
                             @Nullable Runnable internetReturnedRunnable,
                             @Nullable String preferredTrackLanguage,
                             @Nullable View btnSelectAudioTrack,
                             @Nullable View btnSelectVideoTrack,
                             @Nullable View btnSelectSubtitleTrack) {

        if (handleLifecycleEvents) {
            lifecycleOwner.getLifecycle().addObserver(this);
        }

        if (btnSelectAudioTrack != null) {
            btnSelectAudioTrack.setOnClickListener(v -> onAudioClick(ctx));
        }

        if (btnSelectVideoTrack != null) {
            btnSelectVideoTrack.setOnClickListener(v -> onVideoClick(ctx));
        }

        if (btnSelectSubtitleTrack != null) {
            btnSelectSubtitleTrack.setOnClickListener(v -> onSubtitleClick(ctx));
        }

        this.btnSelectAudioTrack = btnSelectAudioTrack;
        this.btnSelectSubtitleTrack = btnSelectSubtitleTrack;
        this.btnSelectVideoTrack = btnSelectVideoTrack;
        this.playerView = playerView;
        this.internetReturnedRunnable = internetReturnedRunnable;
        this.noInternetRunnable = noInternetRunnable;
        this.ctx = ctx;

        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(
            ctx,
            null,
            new DefaultHttpDataSource.Factory()
                .setUserAgent(Util.getUserAgent(ctx, ctx.getPackageName()))
                .setAllowCrossProtocolRedirects(true));

        DefaultRenderersFactory defaultRenderersFactory = new DefaultRenderersFactory(ctx)
            .setExtensionRendererMode(extensionRendererMode);

        trackSelector = new DefaultTrackSelector(ctx);
        trackSelector.setParameters(trackSelector
            .buildUponParameters()
            .setPreferredTextLanguage(preferredTrackLanguage));


        DefaultLoadErrorHandlingPolicy errorHandlingPolicy = new DefaultLoadErrorHandlingPolicy() {
            @Override
            public long getRetryDelayMsFor(LoadErrorInfo loadErrorInfo) {
                if (!handleInternetError()) {
                    return 1000;
                }
                return C.TIME_UNSET;
            }

            @Override
            public int getMinimumLoadableRetryCount(int dataType) {
                return Integer.MAX_VALUE;
            }
        };

        DefaultHlsExtractorFactory defaultHlsExtractorFactory = new DefaultHlsExtractorFactory(FLAG_ALLOW_NON_IDR_KEYFRAMES, false);
        hlsFactory = new HlsMediaSource.Factory(dataSourceFactory)
            .setExtractorFactory(defaultHlsExtractorFactory)
            .setLoadErrorHandlingPolicy(errorHandlingPolicy)
            .setAllowChunklessPreparation(true);

        progressiveFactory = new ProgressiveMediaSource.Factory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(errorHandlingPolicy);

        dashFactory = new DashMediaSource.Factory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(errorHandlingPolicy);

        ssFactory = new SsMediaSource.Factory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(errorHandlingPolicy);

        rtspFactory = new RtspMediaSource.Factory()
            .setLoadErrorHandlingPolicy(errorHandlingPolicy);

        LoadControl loadControl = new DefaultLoadControl.Builder()
            .setBufferDurationsMs(DEFAULT_MIN_BUFFER_MS,
                DEFAULT_MAX_BUFFER_MS,
                500,
                2000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build();

        player = new SimpleExoPlayer
            .Builder(ctx, defaultRenderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setReleaseTimeoutMs(5000)
            .build();

        if (playerView != null) {
            playerView.setPlayer(player);
            if (playerView.getSubtitleView() != null) {
                playerView.getSubtitleView().setStyle(new CaptionStyleCompat(
                    ctx.getResources().getColor(R.color.subtitle_color),      //subtitle text color
                    ctx.getResources().getColor(android.R.color.transparent), //subtitle background color
                    ctx.getResources().getColor(android.R.color.transparent), //subtitle window color
                    CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, //subtitle edge type
                    ctx.getResources().getColor(android.R.color.black), //subtitle edge color
                    null));
            }
        }

        if (BuildConfig.DEBUG) {
            player.addAnalyticsListener(new EventLogger(trackSelector));
        }

        if (listener != null) {
            player.addListener(listener);
        }

        player.addListener(new ExoPlayerWrapperEventListener());

        setUpInternetListener();
    }

    public boolean handleInternetError() {
        try {
            if (checkInternetConnection()) {
                return true;
            } else {
                ctx.runOnUiThread(() -> {
                    if (noInternetRunnable != null && player.getPlaybackState() != Player.STATE_READY) {
                        noInternetErrorShowing = true;
                        noInternetRunnable.run();
                    }
                });
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean checkInternetConnection() {
        NetworkInfo activeNetworkInfo = ((ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void setUpInternetListener() {
        networkRequest = new NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                if (internetReturnedRunnable != null) {
                    internetReturnedRunnable.run();
                }
                super.onAvailable(network);
            }
        };
        connectivityManager = (ConnectivityManager) ctx.getApplication().getSystemService(Context.CONNECTIVITY_SERVICE);
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
    }


    private void unregisterNetworkCallback() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (Exception e) {
            Timber.d(e);
        }
    }

    public void release() {
        if (player != null) {
            player.release();
        }
        player = null;
        btnSelectSubtitleTrack = null;
        btnSelectAudioTrack = null;
        btnSelectVideoTrack = null;
        if (playerView != null) {
            playerView.onPause();
            playerView.setPlayer(null);
        }
        playerView = null;
        unregisterNetworkCallback();
        connectivityManager = null;
        networkCallback = null;
        networkRequest = null;
    }

    public void onStop() {
        if (player == null) {
            return;
        }
        wasPlaying = player.getPlayWhenReady();
        if (player.getPlayWhenReady()) {
            player.setPlayWhenReady(false);
        }
    }

    public void onStart() {
        if (player == null) {
            return;
        }
        player.setPlayWhenReady(wasPlaying);
    }

    public void onSubtitleClick(Context ctx) {
        try {
            new TrackSelectionDialogBuilder(ctx, "Select subtitles", trackSelector, getRendererIndex(C.TRACK_TYPE_TEXT))
                .setTheme(R.style.DialogTheme)
                .setShowDisableOption(true)
                .build()
                .show();
        } catch (Exception e) {
            Timber.d("No tracks available");
        }
    }

    public void onVideoClick(Context ctx) {
        try {
            new TrackSelectionDialogBuilder(ctx, ctx.getString(R.string.select_subtitles), trackSelector, getRendererIndex(C.TRACK_TYPE_VIDEO))
                .setTheme(R.style.DialogTheme)
                .setShowDisableOption(true)
                .build()
                .show();
        } catch (Exception e) {
            Timber.d("No tracks available");
        }
    }

    public void onAudioClick(Context ctx) {
        try {
            new TrackSelectionDialogBuilder(ctx, ctx.getString(R.string.select_audio_track), trackSelector, getRendererIndex(C.TRACK_TYPE_AUDIO))
                .setTheme(R.style.DialogTheme)
                .build()
                .show();
        } catch (Exception e) {
            Timber.d("No tracks available");
        }
    }

    private int getRendererIndex(int TRACK_TYPE) {
        if (trackSelector.getCurrentMappedTrackInfo() == null) {
            return -1;
        }
        for (int i = 0; i < player.getRendererCount(); i++) {
            if (player.getRendererType(i) == TRACK_TYPE) {
                if (trackSelector.getCurrentMappedTrackInfo().getRendererSupport(i) == MappingTrackSelector.MappedTrackInfo.RENDERER_SUPPORT_PLAYABLE_TRACKS) {
                    return i;
                }
            }
        }
        return -1;
    }

    public void playMedia(Uri uri) {
        Timber.d("media url " + uri);
        currentMediaSource = getMediaSource(uri);
        player.setMediaSource(currentMediaSource);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    public void reloadCurrentMedia() {
        player.setMediaSource(currentMediaSource);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private MediaSource getMediaSource(Uri uri) {
        switch (Util.inferContentType(uri)) {
            case C.TYPE_SS:
                Timber.d("Content type ss");
                return ssFactory.createMediaSource(MediaItem.fromUri(uri));
            case C.TYPE_DASH:
                Timber.d("Content type dash");
                return dashFactory.createMediaSource(MediaItem.fromUri(uri));
            case C.TYPE_HLS:
                Timber.d("Content type hls");
                return hlsFactory.createMediaSource(MediaItem.fromUri(uri));
            case C.TYPE_OTHER:
                Timber.d("Content type progressive");
                return progressiveFactory.createMediaSource(MediaItem.fromUri(uri));
            default: {
                Timber.d("Content type hls");
                return hlsFactory.createMediaSource(MediaItem.fromUri(uri));
            }
            case C.TYPE_RTSP:
                Timber.d("Content type rtsp");
                return rtspFactory.createMediaSource(MediaItem.fromUri(uri));
        }
    }

    public void setPlayWhenReady(boolean playWhenReady) {
        if (player != null) {
            player.setPlayWhenReady(playWhenReady);
        }
    }

    public boolean getPlayWhenReady() {
        if (player != null) {
            return player.getPlayWhenReady();
        }
        return false;
    }

    public static class Builder {
        private final FragmentActivity ctx;
        private int extensionRendererMode;
        private Player.Listener listener;
        private String preferredTrackLanguage;
        private View btnSelectAudioTrack, btnSelectVideoTrack, btnSelectSubtitleTrack;
        private final PlayerView playerView;
        private boolean handleLifecycleEvents;
        private LifecycleOwner lifecycleOwner;
        private Runnable noInternetRunnable, internetReturnedRunnable;

        public Builder(@NonNull FragmentActivity ctx,
                       @Nullable PlayerView playerView) {
            this.ctx = ctx;
            this.playerView = playerView;
        }

        public Builder setExtensionRendererMode(int extensionRendererMode) {
            this.extensionRendererMode = extensionRendererMode;
            return this;
        }

        public Builder setListener(@Nullable Player.Listener listener) {
            this.listener = listener;
            return this;
        }

        public Builder setPreferredTrackLanguage(@Nullable String language) {
            this.preferredTrackLanguage = language;
            return this;
        }

        public Builder setTrackSelectionButtons(@Nullable View audio,
                                                @Nullable View video,
                                                @Nullable View subtitle) {
            btnSelectAudioTrack = audio;
            btnSelectVideoTrack = video;
            btnSelectSubtitleTrack = subtitle;
            return this;
        }

        public Builder setHandleLifecycleEvents(boolean handleLifecycleEvents,
                                                @NonNull LifecycleOwner lifecycleOwner) {
            this.lifecycleOwner = lifecycleOwner;
            this.handleLifecycleEvents = handleLifecycleEvents;
            return this;
        }

        public Builder setInternetListeners(@Nullable Runnable noInternetRunnable,
                                            @Nullable Runnable internetReturnedRunnable) {
            this.noInternetRunnable = noInternetRunnable;
            this.internetReturnedRunnable = internetReturnedRunnable;
            return this;
        }


        public ExoPlayerWrapper build() {
            return new ExoPlayerWrapper(ctx,
                handleLifecycleEvents,
                lifecycleOwner,
                extensionRendererMode,
                playerView,
                listener,
                noInternetRunnable,
                internetReturnedRunnable,
                preferredTrackLanguage,
                btnSelectAudioTrack,
                btnSelectVideoTrack,
                btnSelectSubtitleTrack);
        }
    }

    private class ExoPlayerWrapperEventListener implements Player.Listener {
        @Override
        public void onPlayerError(@NonNull ExoPlaybackException error) {
            handlePlayerError(error);
        }

        @Override
        public void onTracksChanged(@NonNull TrackGroupArray trackGroups, @NonNull TrackSelectionArray trackSelections) {
            handleTracksChanged(trackGroups);
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_READY && noInternetErrorShowing) {
                noInternetErrorShowing = false;
                if (internetReturnedRunnable != null) {
                    internetReturnedRunnable.run();
                }
            }
        }
    }

    private void handlePlayerError(@NonNull ExoPlaybackException error) {
        if (error.type == ExoPlaybackException.TYPE_SOURCE &&
            (error.getSourceException() instanceof BehindLiveWindowException ||
                error.getSourceException() instanceof HttpDataSource.InvalidResponseCodeException)) {
            reloadCurrentMedia();
        }
    }

    public void handleTracksChanged(TrackGroupArray trackGroups) {
        boolean textFound = false;
        boolean audioFound = false;
        for (int i = 0; i < trackGroups.length; i++) {
            for (int g = 0; g < trackGroups.get(i).length; g++) {
                String sampleMimeType = trackGroups.get(i).getFormat(g).sampleMimeType;
                if (sampleMimeType != null) {
                    if (MimeTypes.isAudio(sampleMimeType)) {
                        audioFound = true;
                    } else if (MimeTypes.isText(sampleMimeType)) {
                        textFound = true;
                    }
                }
            }
        }
        if (btnSelectAudioTrack != null) {
            btnSelectAudioTrack.setVisibility(audioFound ? View.VISIBLE : View.INVISIBLE);
        }
        if (btnSelectSubtitleTrack != null) {
            btnSelectSubtitleTrack.setVisibility(textFound ? View.VISIBLE : View.INVISIBLE);
        }
    }
}