package com.formichelli.dfnightselfies.util

import android.annotation.SuppressLint
import android.app.Activity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.LinearLayout
import com.formichelli.dfnightselfies.CameraManager

@Suppress("DEPRECATION")
@SuppressLint("ViewConstructor")
class CameraPreview(private val activity: Activity, private val cameraManager: CameraManager, private val photoActionButtons: LinearLayout) : SurfaceView(activity), SurfaceHolder.Callback {
    init {
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        holder.addCallback(this)
        // deprecated setting, but required on Android versions prior to 3.0
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            cameraManager.setPreviewDisplay(holder)
            if (photoActionButtons.visibility == View.GONE)
                cameraManager.startPreview()
        } catch (e: Exception) {
            LogHelper.log(activity, "Error setting camera preview: " + e.message)
        }

    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // camera release is managed by activity
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        // Surface can't change
    }
}
