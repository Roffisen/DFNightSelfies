@file:Suppress("DEPRECATION")

package com.formichelli.dfnightselfies.util

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.Camera
import android.view.Surface
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.VideoView
import com.formichelli.dfnightselfies.preference.PreferenceManager

class PreviewSizeManager(private val activity: Activity, private val preferenceManager: PreferenceManager, private val cameraPreview: FrameLayout, private val photoPreview: ImageView, private val videoPreview: VideoView) {
    private val scaleFactor = 1.5
    private val maxScale = 1
    private val minScale = -2

    fun initializePreviewSize(camera: Camera, photoOrVideo: Boolean): Camera.Size {
        val display = activity.windowManager.defaultDisplay
        val cameraPreviewHeight = display.height / 3

        val ratioFromPreference = Ratio.fromLabel(preferenceManager.ratio)
        val (bestPhotoOrVideoSize, bestPreviewSize) = getBestSizes(camera, ratioFromPreference, photoOrVideo)

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

        val videoPreviewParams = videoPreview.layoutParams as FrameLayout.LayoutParams
        videoPreviewParams.width = (cameraPreviewParams.width * scaleFactor).toInt()
        videoPreviewParams.height = (cameraPreviewParams.width * scaleFactor).toInt()
        videoPreview.layoutParams = videoPreviewParams

        val scaleFactor = Math.pow(scaleFactor, preferenceManager.scale.toDouble())
        cameraPreviewParams.width = (cameraPreviewParams.width * scaleFactor).toInt()
        cameraPreviewParams.height = (cameraPreviewParams.height * scaleFactor).toInt()
        cameraPreview.layoutParams = cameraPreviewParams

        val mCameraParameters = camera.parameters
        mCameraParameters.setRotation(cameraRotation)
        mCameraParameters.pictureFormat = PixelFormat.JPEG
        mCameraParameters.setPreviewSize(bestPreviewSize.width, bestPreviewSize.height)
        mCameraParameters.setPictureSize(bestPhotoOrVideoSize.width, bestPhotoOrVideoSize.height)
        camera.parameters = mCameraParameters

        return bestPhotoOrVideoSize
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

    private fun getBestSizes(camera: Camera, ratio: Ratio, photoOrVideo: Boolean): Pair<Camera.Size, Camera.Size> {
        val photoOrVideoSizes = if (photoOrVideo) camera.parameters.supportedPictureSizes else camera.parameters.supportedVideoSizes
        if (photoOrVideoSizes.isEmpty())
            throw IllegalStateException("No picture sizes available")

        val previewSizes = camera.parameters.supportedPreviewSizes
        if (previewSizes.isEmpty())
            throw IllegalStateException("No preview sizes available")

        var maxPictureSizeValue = 0
        var bestPictureSize = photoOrVideoSizes[0]
        var bestPictureRatio: Ratio? = null
        photoOrVideoSizes.forEach {
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
            return getBestSizes(camera, Ratio.ANY, photoOrVideo)

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
            scaleDifference + preferenceManager.scale > maxScale -> maxScale - preferenceManager.scale
        // don't scale less than minScale
            scaleDifference + preferenceManager.scale < minScale -> minScale - preferenceManager.scale
            else -> scaleDifference
        }

        if (effectiveScaleCount == 0)
            return

        val scaleFactor = Math.pow(scaleFactor, effectiveScaleCount.toDouble())

        val cameraPreviewParams = cameraPreview.layoutParams as FrameLayout.LayoutParams
        cameraPreviewParams.width = (cameraPreview.width * scaleFactor).toInt()
        cameraPreviewParams.height = (cameraPreview.height * scaleFactor).toInt()
        cameraPreview.layoutParams = cameraPreviewParams

        preferenceManager.scale += effectiveScaleCount
    }
}