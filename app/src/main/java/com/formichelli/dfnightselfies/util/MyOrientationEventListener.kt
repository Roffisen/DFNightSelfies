package com.formichelli.dfnightselfies.util

import android.app.Activity
import android.content.Context
import android.hardware.Camera
import android.hardware.SensorManager
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager

internal class MyOrientationEventListener(activity: Activity) : OrientationEventListener(activity, SensorManager.SENSOR_DELAY_UI) {
    private var display = (activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
    private var lastDisplayRotation: Int = 0

    private lateinit var camera: Camera
    private var cameraOrientation: Int = 0
    internal var cameraRotation: Int = 0
        private set

    fun setCamera(camera_: Camera, cameraOrientation_: Int) {
        camera = camera_
        cameraOrientation = cameraOrientation_
    }

    override fun onOrientationChanged(orientation: Int) {
        if (orientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN)
            lastDisplayRotation = android.view.OrientationEventListener.ORIENTATION_UNKNOWN

        val displayRotation = getDisplayRotation()
        if (lastDisplayRotation == displayRotation)
            return
        else
            lastDisplayRotation = displayRotation

        synchronized(this) {
            camera.setDisplayOrientation(getDisplayOrientation(displayRotation))
            cameraRotation = getCameraRotation(displayRotation)
            try {
                val cameraParameters = camera.parameters
                cameraParameters.setRotation(cameraRotation)
                camera.parameters = cameraParameters
            } catch (e: Exception) {
                // nothing to do
            }
        }
    }

    private fun getCameraRotation(displayOrientation: Int): Int = (cameraOrientation - displayOrientation + 360) % 360

    private fun getDisplayRotation() = when (display.rotation) {
        Surface.ROTATION_0 -> 0

        Surface.ROTATION_90 -> 270

        Surface.ROTATION_180 -> 180

        Surface.ROTATION_270 -> 90

        else -> -1
    }

    private fun getDisplayOrientation(displayOrientation: Int) = (displayOrientation + 90) % 360
}
