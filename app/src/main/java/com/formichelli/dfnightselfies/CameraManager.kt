@file:Suppress("DEPRECATION")

package com.formichelli.dfnightselfies

import android.app.Activity
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Camera
import android.media.MediaActionSound
import android.os.Build
import android.view.OrientationEventListener
import android.view.SurfaceHolder
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.formichelli.dfnightselfies.util.*

class CameraManager(private val activity: Activity,
                    private val stateMachine: StateMachine,
                    private val cameraPreview: FrameLayout,
                    private val orientationEventListener: MyOrientationEventListener,
                    private val previewSizeManager: PreviewSizeManager,
                    private val bitmapManager: BitmapManager,
                    private val photoPreview: ImageView,
                    private val photoActionButtons: LinearLayout,
                    private val shutterFrame: FrameLayout,
                    private val sharedPreferences: SharedPreferences) : Camera.PictureCallback {
    private var camera: Camera? = null
    private var cameraSurface: CameraPreview? = null
    private var cameraRotation = 0
    private fun shouldPlaySound() = sharedPreferences.getBoolean(activity.getString(R.string.shutter_sound_preference), false)

    private fun initializeCamera() {
        if (camera != null)
            return

        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA))
            return

        val cameraInfo = Camera.CameraInfo()
        for (i in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(i, cameraInfo)
            if (cameraInfo.facing != Camera.CameraInfo.CAMERA_FACING_FRONT) {
                continue
            }

            try {
                camera = Camera.open(i)
                val camera = camera ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    camera.enableShutterSound(false)
                }

                previewSizeManager.initializePreviewSize(camera)

                cameraSurface = CameraPreview(activity, this, photoActionButtons)
                cameraPreview.removeAllViews()
                cameraPreview.addView(cameraSurface)
                orientationEventListener.setCameraManager(this, cameraInfo.orientation)
                orientationEventListener.onOrientationChanged(OrientationEventListener.ORIENTATION_UNKNOWN)
            } catch (e: RuntimeException) {
                LogHelper.log(activity, "Can't open camera " + i + ": " + e.localizedMessage)
            }
        }
    }

    fun releaseCamera() {
        camera?.stopPreview()
        camera?.release()
        camera = null
    }

    fun startPreview() {
        if (stateMachine.currentState != StateMachine.State.BEFORE_TAKING) {
            return
        }

        initializeCamera()
        val camera = camera ?: return
        val cameraSurface = cameraSurface ?: return

        photoPreview.visibility = View.GONE
        photoPreview.setImageResource(android.R.color.transparent)
        bitmapManager.bitmap = null
        cameraSurface.visibility = View.VISIBLE
        camera.startPreview()
    }

    fun restartPreview() {
        stateMachine.currentState = StateMachine.State.BEFORE_TAKING
        startPreview()
    }

    fun takePicture(force: Boolean = false) {
        if (!force && stateMachine.currentState != StateMachine.State.BEFORE_TAKING)
            return

        val camera = camera ?: return
        stateMachine.currentState = StateMachine.State.WHILE_TAKING

        try {
            camera.takePicture(shutterCallback, null, this)
        } catch (e: Exception) {
            restartPreview()
        }
    }

    private val shutterCallback = Camera.ShutterCallback {
        if (shouldPlaySound() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            MediaActionSound().play(MediaActionSound.SHUTTER_CLICK)
        }

        shutterFrame.visibility = View.VISIBLE
    }

    override fun onPictureTaken(data: ByteArray, camera_: Camera) {
        val camera = camera ?: return

        camera.stopPreview()
        shutterFrame.visibility = View.GONE

        bitmapManager.fromByteArray(data, cameraRotation)

        photoPreview.setImageBitmap(bitmapManager.bitmap)
        photoPreview.visibility = View.VISIBLE
        cameraSurface?.visibility = View.GONE

        stateMachine.currentState = StateMachine.State.AFTER_TAKING
    }

    fun setPreviewDisplay(holder: SurfaceHolder) = camera?.setPreviewDisplay(holder)
    fun setDisplayOrientation(displayOrientation: Int) = camera?.setDisplayOrientation(displayOrientation)

    fun setRotation(cameraRotation_: Int) {
        cameraRotation = cameraRotation_
        val camera = camera ?: return

        val cameraParameters = camera.parameters
        cameraParameters.setRotation(cameraRotation_)
        camera.parameters = cameraParameters
    }
}