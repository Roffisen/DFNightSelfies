package com.formichelli.dfnightselfies.util

import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.VideoView
import java.io.File

object PreviewManager {
    fun showPicturePreview(photoPreview: ImageView, bitmap: Bitmap) {
        photoPreview.setImageBitmap(bitmap)
        photoPreview.visibility = View.VISIBLE
    }

    fun showVideoPreview(videoPreview: VideoView, videoOutputFile: String) {
        videoPreview.setVideoURI(Uri.fromFile(File(videoOutputFile)))
        videoPreview.visibility = View.VISIBLE
        videoPreview.setZOrderOnTop(true)
        videoPreview.start()
    }
}