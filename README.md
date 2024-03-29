[![](https://jitpack.io/v/ivankostadinovic/ExoPlayerWrapper.svg)](https://jitpack.io/#ivankostadinovic/ExoPlayerWrapper)
# ExoPlayerWrapper
A lifecycle aware easy to use [ExoPlayer](https://github.com/google/ExoPlayer) wrapper. Starting from version 2.15.1.4, the library is rewritten in Kotlin. Starting with version 3.1.1.1.0, the library is migrated to Android Jetpack Media3 components. Example of usage is available in the **sample** module.


## Download
This library is available in **JitPack** which is the default Maven repository used in Android Studio. 
You don't need to add a dependency to ExoPlayer separately as this library exposes it.

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
	        implementation 'com.github.ivankostadinovic:ExoPlayerWrapper:<latest-version>' 
}
```
You can see the latest version at the top of the Readme.MD file, inside the JitPack image. 
The first three parts of the version code are aligned with the ExoPlayer version that is used internally.

# Usage

## Playback without video
```java
        String mediaUrl = "...";
        ExoPlayerWrapper exoPlayerWrapper = new ExoPlayerWrapper.Builder(context)
            .build();
        
        exoPlayerWrapper.playMedia(mediaUrl);
```

## Playback with video
In your XML layout file, declare a player view.

```xml
	...
        <androidx.media3.ui.PlayerView
            android:id="@+id/playerView"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />
```

```java
        String mediaUrl = "...";
        
        PlayerView playerView = findViewById(R.id.player_view);
        ExoPlayerWrapper exoPlayerWrapper = new ExoPlayerWrapper.Builder(context, playerView)
            .build();
        
        exoPlayerWrapper.playMedia(mediaUrl);
```

## Observing lifecycle events
Use this if you want the playback to be paused when the activity/fragment is no longer visible, and resumed (if it was playing) after the activity/fragment is visible again.
This also enables automatic releasing of resources used by the player when the activity/fragment is destroyed.
```java
        ExoPlayerWrapper exoPlayerWrapper = new ExoPlayerWrapper.Builder(context)
            .setHandleLifecycleEvents(context, lifeCycleOwner)   //default is false, lifeCycleOwner - LifeCycleOwner to which the player will be bound to (activity or fragment)
            .build();
```

## Observing player and internet connection events
```java
        ExoPlayerWrapper exoPlayerWrapper = new ExoPlayerWrapper.Builder(context)
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
The library will automatically hide the views if there are no tracks of that type available (e.g. if there are no audio tracks available to choose from, the button for selecting audio tracks will be made invisible).
```java
        View buttonSelectAudioTrack, buttonSelectVideoTrack, buttonSelectSubtitleTrack;
        buttonSelectAudioTrack = findViewById(R.id.btn_audio); //a view which when clicked will open audio track selection dialog
        buttonSelectVideoTrack = findViewById(R.id.btn_video; //a view which when clicked will open video track selection dialog
        buttonSelectSubtitleTrack = findViewById(R.id.btn_subtitle); //a view which when clicked will open subtitle track selection dialog

        ExoPlayerWrapper exoPlayerWrapper = new ExoPlayerWrapper.Builder(context)
            .setTrackSelectionButtons(buttonSelectAudioTrack, buttonSelectVideoTrack, buttonSelectSubtitleTrack)
            .setPreferredTrackLanguage("en") //default isn't set, IETF BCP 47 conformant tag
            .build();
```

## Enable extension rendering
```java
        ExoPlayerWrapper exoPlayerWrapper = new ExoPlayerWrapper.Builder(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .build();
```

## OkHttp networking
To enable OkHttp networking instead of default networking, pass a OkHttpClient object to the player wrapper.
```java
	OkHttpClient okHttpClient = new OkHttpClient(); //create a OkHttpClient object, or reuse one you already have in the app.
        ExoPlayerWrapper exoPlayerWrapper = new ExoPlayerWrapper.Builder(context)
            .setOkHttpClient(okHttpClient)
            .build();
```

## Logging
To see the logs from the player, filter the logcat output by "EventLogger". Don't forget to turn this off for production builds.
```java
        ExoPlayerWrapper exoPlayerWrapper = new ExoPlayerWrapper.Builder(context)
	    .setLoggingEnabled(true)
            .build();
```

## Migrating from version 2 to version 3
The library methods didn't change, but ExoPlayer imports (if you use any) are changed. This could introduce breaking changes, change the imports like described in the [ExoPlayer to Jetpack Media3 migration](https://developer.android.com/guide/topics/media/exoplayer/mappings)

