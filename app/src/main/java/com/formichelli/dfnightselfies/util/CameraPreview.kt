package com.formichelli.dfnightselfies.util

import android.annotation.SuppressLint
import android.app.Activity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout

@Suppress("DEPRECATION")
@SuppressLint("ViewConstructor")
class CameraPreview(activity: Activity, parent: FrameLayout, private val callback: (SurfaceHolder) -> Unit) : SurfaceView(activity), SurfaceHolder.Callback {
    init {
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        holder.addCallback(this)
        // deprecated setting, but required on Android versions prior to 3.0
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)

        parent.removeAllViews()
        parent.addView(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        callback(holder)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // camera release is managed by activity
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        // Surface can't change
    }
}
