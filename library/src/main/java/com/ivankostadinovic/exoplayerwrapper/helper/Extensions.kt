package com.ivankostadinovic.exoplayerwrapper.helper

import androidx.media3.common.PlaybackException

fun PlaybackException.is403Forbidden(): Boolean {
    return this.cause?.message?.contains("403") == true
}
