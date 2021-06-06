# ExoPlayerWrapper
A lifecycle aware easy to use [ExoPlayer](https://github.com/google/ExoPlayer) wrapper.

## Usage

### Playback without video
```
        String mediaUrl = "...";
        ExoPlayerWrapper exoPlayerWrapper = new ExoPlayerWrapper.Builder(this)
            .build();
        
        exoPlayerWrapper.playMedia(mediaUrl);
```

### Playback with video
```
        String mediaUrl = "...";
        
        PlayerView playerView = findViewById(R.id.player_view);
        ExoPlayerWrapper exoPlayerWrapper = new ExoPlayerWrapper.Builder(this, playerView)
            .build();
        
        exoPlayerWrapper.playMedia(mediaUrl);
```

### Observing player, lifecycle and internet connection events

```
        ExoPlayerWrapper exoPlayerWrapper = new ExoPlayerWrapper.Builder(this, playerView)
            .setHandleLifecycleEvents(true, this)   //default is false
            .setListener(new Player.Listener() {
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {

                }
            })
            .setConnectionListener(new ExoPlayerWrapper.ConnectionListener() {
                @Override
                public void onConnectionError() {
                    hideNoInternetError();
                }

                @Override
                public void onConnectionReturned() {
                    showNoInternetError();

                }
            })
            .build();
```

### Track selection and preferred language
If you wish to add track selection and/or a preferred audio/subtitle language, pass the views and the preferred language to the library.
```
        View buttonSelectAudioTrack, buttonSelectVideoTrack, buttonSelectSubtitleTrack;
        buttonSelectAudioTrack = findViewById(R.id.btn_audio); //a view which when clicked will open audio track selection dialog
        buttonSelectVideoTrack = findViewById(R.id.btn_video; //a view which when clicked will open video track selection dialog
        buttonSelectSubtitleTrack = findViewById(R.id.btn_subtitle); //a view which when clicked will open subtitle track selection dialog

        ExoPlayerWrapper exoPlayerWrapper = new ExoPlayerWrapper.Builder(this, playerView)
            .setTrackSelectionButtons(buttonSelectAudioTrack, buttonSelectVideoTrack, buttonSelectSubtitleTrack)
            .setPreferredTrackLanguage("en") //default isn't set
            .build();
```

### Enable extension rendering
```
        ExoPlayerWrapper exoPlayerWrapper = new ExoPlayerWrapper.Builder(this, playerView)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .build();
```