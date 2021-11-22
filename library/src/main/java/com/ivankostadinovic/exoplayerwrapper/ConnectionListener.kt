package com.ivankostadinovic.exoplayerwrapper

interface ConnectionListener {
    fun onConnectionError()
    fun onConnectionReturned()
}
