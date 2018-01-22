@file:Suppress("DEPRECATION")

package com.formichelli.dfnightselfies.util

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.hardware.Camera
import android.view.Surface
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.formichelli.dfnightselfies.R

class PreviewSizeManager(private val activity: Activity, private val sharedPreferences: SharedPreferences, private val cameraPreview: FrameLayout, private val photoPreview: ImageView) {
    private val scaleFactor = 1.5
    private val maxScale = 1
    private val minScale = -2

    private var scale: Int = sharedPreferences.getInt("scaleFactor", 0)

    fun initializePreviewSize(camera: Camera) {

        val display = activity.windowManager.defaultDisplay
        val cameraPreviewHeight = display.height / 3

        val ratioFromPreference = Ratio.fromLabel(sharedPreferences.getString(activity.getString(R.string.ratio_preference), "ANY"))
        val (bestPictureSize, bestPreviewSize) = getBestSizes(camera, ratioFromPreference)

        val (portrait, displayOrientation, cameraRotation) = getRotationTriple()

        camera.setDisplayOrientation(displayOrientation)

        val cameraPreviewParams = cameraPreview.layoutParams as FrameLayout.LayoutParams
        cameraPreviewParams.width =
                if (portrait)
                    (cameraPreviewHeight.toDouble() / Ratio.doubleValue(bestPreviewSize.width, bestPreviewSize.height)).toInt()
                else
                    (cameraPreviewHeight.toDouble() * Ratio.doubleValue(bestPreviewSize.width, bestPreviewSize.height)).toInt()
        cameraPreviewParams.height = cameraPreviewHeight

        val photoPreviewParams = photoPreview.layoutParams as FrameLayout.LayoutParams
        photoPreviewParams.width = (cameraPreviewParams.width * scaleFactor).toInt()
        photoPreviewParams.height = (cameraPreviewParams.height * scaleFactor).toInt()
        photoPreview.layoutParams = photoPreviewParams

        val scaleFactor = Math.pow(scaleFactor, scale.toDouble())
        cameraPreviewParams.width = (cameraPreviewParams.width * scaleFactor).toInt()
        cameraPreviewParams.height = (cameraPreviewParams.height * scaleFactor).toInt()
        cameraPreview.layoutParams = cameraPreviewParams

        val mCameraParameters = camera.parameters
        mCameraParameters.setRotation(cameraRotation)
        mCameraParameters.pictureFormat = PixelFormat.JPEG
        mCameraParameters.setPreviewSize(bestPreviewSize.width, bestPreviewSize.height)
        mCameraParameters.setPictureSize(bestPictureSize.width, bestPictureSize.height)
        camera.parameters = mCameraParameters
    }

    private fun getRotationTriple(): Triple<Boolean, Int, Int> {
        val rotation = (activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        return when (rotation) {
            Surface.ROTATION_0 -> Triple(true, 90, 270) // portrait

            Surface.ROTATION_180 -> Triple(true, 270, 90) // portrait (upside down)

            Surface.ROTATION_90 -> Triple(false, 0, 0) // landscape (down at left)

            Surface.ROTATION_270 -> Triple(false, 180, 180) // landscape (down at right)

            else -> Triple(true, 0, 0)
        }
    }

    private fun getBestSizes(camera: Camera, ratio: Ratio): Pair<Camera.Size, Camera.Size> {
        val pictureSizes = camera.parameters.supportedPictureSizes
        if (pictureSizes.isEmpty())
            throw IllegalStateException("No picture sizes available")

        val previewSizes = camera.parameters.supportedPreviewSizes
        if (previewSizes.isEmpty())
            throw IllegalStateException("No preview sizes available")

        var maxPictureSizeValue = 0
        var bestPictureSize = pictureSizes[0]
        var bestPictureRatio: Ratio? = null
        pictureSizes.forEach {
            if (!ratio.matches(it.width, it.height))
                return@forEach

            val sizeValue = it.width * it.height
            if (sizeValue > maxPictureSizeValue) {
                bestPictureRatio = Ratio.fromRatio(it.width, it.height)
                bestPictureSize = it
                maxPictureSizeValue = sizeValue
            }
        }

        if (bestPictureRatio == null)
            return getBestSizes(camera, Ratio.ANY)

        var maxPreviewSizeValue = 0
        var bestPreviewSize = previewSizes[0]
        previewSizes.forEach {
            if (!bestPictureRatio!!.matches(it.width, it.height))
                return@forEach

            val sizeValue = it.width * it.height
            if (sizeValue > maxPreviewSizeValue) {
                bestPreviewSize = it
                maxPreviewSizeValue = sizeValue
            }
        }

        return Pair(bestPictureSize, bestPreviewSize)
    }

    fun resizePreview(scaleDifference: Int) {
        val effectiveScaleCount = when {
        // don't scale more than maxScale
            scaleDifference + scale > maxScale -> maxScale - scale
        // don't scale less than minScale
            scaleDifference + scale < minScale -> minScale - scale
            else -> scaleDifference
        }

        if (effectiveScaleCount == 0)
            return

        val scaleFactor = Math.pow(scaleFactor, effectiveScaleCount.toDouble())

        val cameraPreviewParams = cameraPreview.layoutParams as FrameLayout.LayoutParams
        cameraPreviewParams.width = (cameraPreview.width * scaleFactor).toInt()
        cameraPreviewParams.height = (cameraPreview.height * scaleFactor).toInt()
        cameraPreview.layoutParams = cameraPreviewParams

        scale += effectiveScaleCount

        sharedPreferences.edit().putInt("scaleFactor", scale).apply()
    }
}