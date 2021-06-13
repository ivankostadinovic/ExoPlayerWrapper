# ExoPlayerWrapper
A lifecycle aware easy to use [ExoPlayer](https://github.com/google/ExoPlayer) wrapper.



## Download
This library is available in **jitPack** which is the default Maven repository used in Android Studio.

**Step 1.** Add it in your root build.gradle at the end of repositories
```Gradle
allprojects 
    {
        repositories {
            ...
            maven { url 'https://jitpack.io' }
        }
    }
```

**Step 2.** Add the dependency in your apps module build.gradle
```Gradle
dependencies {
	        implementation 'com.github.ivankostadinovic:ExoPlayerWrapper:0.5.0'
}
```

# Usage

## Playback without video
```java
        String mediaUrl = "...";
        ExoPlayerWrapper exoPlayerWrapper = new ExoPlayerWrapper.Builder(this)
            .build();
        
        exoPlayerWrapper.playMedia(mediaUrl);
```

## Playback with video
```java
        String mediaUrl = "...";
        
        PlayerView playerView = findViewById(R.id.player_view);
        ExoPlayerWrapper exoPlayerWrapper = new ExoPlayerWrapper.Builder(this, playerView)
            .build();
        
        exoPlayerWrapper.playMedia(mediaUrl);
```

## Observing lifecycle events
Use this if you want the playback to be paused when the activity/fragment is no longer visible, and resumed (if it was playing) after the activity/fragment is visible again.
This also enables automatic releasing of resources used by the player when the activity/fragment is destroyed.
```java
        ExoPlayerWrapper exoPlayerWrapper = new ExoPlayerWrapper.Builder(this, playerView)
            .setHandleLifecycleEvents(true, this)   //default is false
            .build();
```

## Observing player and internet connection events
```java
        ExoPlayerWrapper exoPlayerWrapper = new ExoPlayerWrapper.Builder(this, playerView)
            .setListener(new Player.Listener() {  //you can override any Player.Listener function here
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {

                }
            })
            .setConnectionListener(new ExoPlayerWrapper.ConnectionListener() {
                @Override
                public void onConnectionError() {
                    showNoInternetError(); // your function to show to the user that there is an internet connection issue
                }

                @Override
                public void onConnectionReturned() {
                    hideNoInternetError(); //your function to show to the user that the internet connection returned
                }
            })
	    .build()
```

## Track selection and preferred language
If you wish to add track selection and/or a preferred audio/subtitle language, pass the views and the preferred language to the library.
```java
        View buttonSelectAudioTrack, buttonSelectVideoTrack, buttonSelectSubtitleTrack;
        buttonSelectAudioTrack = findViewById(R.id.btn_audio); //a view which when clicked will open audio track selection dialog
        buttonSelectVideoTrack = findViewById(R.id.btn_video; //a view which when clicked will open video track selection dialog
        buttonSelectSubtitleTrack = findViewById(R.id.btn_subtitle); //a view which when clicked will open subtitle track selection dialog

        ExoPlayerWrapper exoPlayerWrapper = new ExoPlayerWrapper.Builder(this, playerView)
            .setTrackSelectionButtons(buttonSelectAudioTrack, buttonSelectVideoTrack, buttonSelectSubtitleTrack)
            .setPreferredTrackLanguage("en") //default isn't set, IETF BCP 47 conformant tag
            .build();
```

## Enable extension rendering
```java
        ExoPlayerWrapper exoPlayerWrapper = new ExoPlayerWrapper.Builder(this, playerView)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .build();
```

## Logging
To see the logs from the player, filter the logcat output by "EventLogger". Don't forget to turn this off for production builds.
```java
        ExoPlayerWrapper exoPlayerWrapper = new ExoPlayerWrapper.Builder(this)
	    .setLoggingEnabled(true)
            .build();
```

# License
```
MIT License

Copyright (c) 2021 Ivan Kostadinovic

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
