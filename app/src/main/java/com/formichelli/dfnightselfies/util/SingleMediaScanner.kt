package com.formichelli.dfnightselfies.util

import android.content.Context
import android.media.MediaScannerConnection
import android.media.MediaScannerConnection.MediaScannerConnectionClient
import android.net.Uri

import java.io.File

class SingleMediaScanner(context: Context, val file: File) : MediaScannerConnectionClient {
    private val mediaScannerConnection: MediaScannerConnection = MediaScannerConnection(context, this)
    var shareUri: Uri? = null
        private set

    init {
        mediaScannerConnection.connect()
    }

    override fun onMediaScannerConnected() = mediaScannerConnection.scanFile(file.absolutePath, null)

    override fun onScanCompleted(path: String, uri: Uri) {
        shareUri = uri
        mediaScannerConnection.disconnect()
    }
}