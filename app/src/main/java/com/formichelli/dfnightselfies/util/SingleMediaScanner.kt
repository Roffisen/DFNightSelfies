package com.formichelli.dfnightselfies.util

import android.content.Context
import android.media.MediaScannerConnection
import android.media.MediaScannerConnection.MediaScannerConnectionClient
import android.net.Uri

import java.io.File

class SingleMediaScanner(context: Context) : MediaScannerConnectionClient {
    private val mediaScannerConnection: MediaScannerConnection = MediaScannerConnection(context, this)
    var file: File? = null

    fun scan(fileToScan: File) {
        mediaScannerConnection.connect()
        file = fileToScan
    }

    override fun onMediaScannerConnected() = mediaScannerConnection.scanFile(file?.absolutePath, null)

    override fun onScanCompleted(path: String, uri: Uri) = mediaScannerConnection.disconnect()
}