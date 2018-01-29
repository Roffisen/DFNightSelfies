package com.formichelli.dfnightselfies.util

import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.VideoView
import java.io.File

class PreviewManager(private val mediaScanner: SingleMediaScanner, private val bitmapManager: BitmapManager, private val photoPreview: ImageView, private val videoPreview: VideoView) {
    var currentBitmap: Bitmap? = null
    var currentVideo: File? = null

    fun hidePreview() {
        photoPreview.visibility = View.GONE
        photoPreview.setImageResource(android.R.color.transparent)
        videoPreview.visibility = View.GONE
    }

    fun showPicturePreview(bitmap: Bitmap) {
        currentBitmap = bitmap
        currentVideo = null

        photoPreview.setImageBitmap(bitmap)
        photoPreview.visibility = View.VISIBLE
    }

    fun showVideoPreview(videoOutputFile: String) {
        currentBitmap = null
        currentVideo = File(videoOutputFile)

        videoPreview.setVideoURI(Uri.fromFile(currentVideo))
        videoPreview.visibility = View.VISIBLE
        videoPreview.setZOrderOnTop(true)
        videoPreview.start()
    }

    fun saveCurrentPreview() {
        if (currentBitmap != null) {
            mediaScanner.scan(bitmapManager.saveToFile(currentBitmap!!, mediaScanner))
        } else if (currentVideo != null) {
            mediaScanner.scan(currentVideo)
        }
    }

    fun deleteCurrentPreview() {
        if (currentVideo != null) {
            currentVideo?.delete()
        }
    }
}