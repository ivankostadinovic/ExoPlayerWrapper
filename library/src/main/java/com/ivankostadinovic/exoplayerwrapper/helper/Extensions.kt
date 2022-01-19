package com.ivankostadinovic.exoplayerwrapper.helper

import com.google.android.exoplayer2.PlaybackException

fun PlaybackException.is403Forbidden(): Boolean {
    return this.cause?.message?.contains("403") == true
}
