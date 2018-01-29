@file:Suppress("DEPRECATION")

package com.formichelli.dfnightselfies

import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.Camera
import android.media.MediaActionSound
import android.media.MediaRecorder
import android.os.Build
import android.view.OrientationEventListener
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.formichelli.dfnightselfies.preference.PreferenceManager
import com.formichelli.dfnightselfies.util.*
import java.io.IOException

class CameraManager(private val activity: Activity,
                    private val stateMachine: StateMachine,
                    private val cameraPreview: FrameLayout,
                    private val orientationEventListener: MyOrientationEventListener,
                    private val previewSizeManager: PreviewSizeManager,
                    private val bitmapManager: BitmapManager,
                    private val photoActionButtons: LinearLayout,
                    private val shutterFrame: FrameLayout,
                    private val preferenceManager: PreferenceManager) : Camera.PictureCallback {
    private var camera: Camera? = null
    private lateinit var bestPhotoOrVideoSize: Camera.Size
    private var mediaRecorder: MediaRecorder? = null
    private var cameraSurface: CameraPreview? = null
    private var displayOrientation = 0
    private var cameraRotation = 0
    private var videoOutputFile: String? = null

    init {
        preferenceManager.pictureOrVideoChanged {
            releaseCamera()
            startPreview()
        }
    }

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

                bestPhotoOrVideoSize = previewSizeManager.initializePreviewSize(camera, preferenceManager.pictureOrVideo)

                cameraSurface = CameraPreview(activity, cameraPreview) {
                    try {
                        this.camera?.setPreviewDisplay(it)
                        if (photoActionButtons.visibility == View.GONE)
                            startPreview()
                    } catch (e: Exception) {
                        Util.log(activity, "Error setting camera preview: " + e.message)
                    }
                }
                orientationEventListener.setCameraManager(this, cameraInfo.orientation)
                orientationEventListener.onOrientationChanged(OrientationEventListener.ORIENTATION_UNKNOWN)
            } catch (e: RuntimeException) {
                cantGetCameraError(e)
            }
        }
    }

    private fun cantGetCameraError(e: Exception) {
        Util.log(activity, activity.getString(R.string.cant_get_front_camera) + ": " + e.localizedMessage)
        Util.exitWithError(activity, activity.getString(R.string.cant_get_front_camera))
    }

    fun release() {
        releaseCamera()
        releaseMediaRecorder()
    }

    private fun releaseCamera() {
        camera?.stopPreview()
        camera?.release()
        camera = null
    }

    fun startPreview() {
        initializeCamera()

        val camera = camera ?: return
        val cameraSurface = cameraSurface ?: return

        bitmapManager.bitmap = null
        stateMachine.onStartPreview(cameraSurface)
        camera.startPreview()
    }

    fun takePictureOrVideo(fromCountdown: Boolean) {
        if (preferenceManager.pictureOrVideo) {
            takePicture(fromCountdown)
        } else {
            when (stateMachine.startOrStopRecording(fromCountdown)) {
                true -> startVideoRecording()
                false -> stopVideoRecording()
                null -> return
            }
        }
    }

    private fun takePicture(fromCountdown: Boolean = false) {
        val camera = camera ?: return

        if (!stateMachine.takingPicture(fromCountdown)) {
            return
        }

        try {
            camera.takePicture(shutterCallback, null, this)
        } catch (e: Exception) {
            startPreview()
        }
    }

    private fun startVideoRecording() {
        if (preferenceManager.shouldPlaySound && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            MediaActionSound().play(MediaActionSound.START_VIDEO_RECORDING)
        }

        initializeMediaRecorder()
        mediaRecorder?.start()
    }

    private fun stopVideoRecording() {
        val cameraSurface = cameraSurface ?: return
        if (preferenceManager.shouldPlaySound && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            MediaActionSound().play(MediaActionSound.STOP_VIDEO_RECORDING)
        }

        releaseMediaRecorder()

        stateMachine.onVideoTaken(cameraSurface, videoOutputFile!!)
    }

    private fun initializeMediaRecorder() {
        if (mediaRecorder != null)
            return
        val camera = camera ?: return

        val mediaRecorder = MediaRecorder()
        camera.stopPreview()
        camera.unlock()
        this.camera = null
        mediaRecorder.setCamera(camera)
        mediaRecorder.setOrientationHint(if (displayOrientation == 90) 270 else displayOrientation)
        mediaRecorder.setPreviewDisplay(cameraSurface!!.holder.surface)
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT)
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder.setAudioEncodingBitRate(preferenceManager.audioBitRate)
        mediaRecorder.setAudioSamplingRate(preferenceManager.audioSamplingRate)
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mediaRecorder.setVideoSize(bestPhotoOrVideoSize.width, bestPhotoOrVideoSize.height)
        mediaRecorder.setVideoFrameRate(preferenceManager.frameRate)
        mediaRecorder.setVideoEncodingBitRate(bestPhotoOrVideoSize.width * bestPhotoOrVideoSize.height * preferenceManager.frameRate)

        videoOutputFile = Util.getOutputFilePath(activity, false, preferenceManager.saveToGallery)
        mediaRecorder.setOutputFile(videoOutputFile)

        try {
            mediaRecorder.prepare()
        } catch (e: IOException) {
            cantGetCameraError(e)
        }

        this.mediaRecorder = mediaRecorder
    }

    private fun releaseMediaRecorder() {
        mediaRecorder?.stop()
        mediaRecorder?.release()
        mediaRecorder = null
    }

    private val shutterCallback = Camera.ShutterCallback {
        if (preferenceManager.shouldPlaySound && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            MediaActionSound().play(MediaActionSound.SHUTTER_CLICK)
        }

        shutterFrame.visibility = View.VISIBLE
    }

    override fun onPictureTaken(data: ByteArray, camera_: Camera) {
        val camera = camera ?: return
        val cameraSurface = cameraSurface ?: return

        camera.stopPreview()
        shutterFrame.visibility = View.GONE

        stateMachine.onPictureTaken(cameraSurface, bitmapManager.fromByteArray(data, cameraRotation))
    }

    fun setDisplayOrientation(displayOrientation_: Int) {
        displayOrientation = displayOrientation_
        camera?.setDisplayOrientation(displayOrientation)
    }

    fun setRotation(cameraRotation_: Int) {
        cameraRotation = cameraRotation_
        val camera = camera ?: return

        val cameraParameters = camera.parameters
        cameraParameters.setRotation(cameraRotation_)
        camera.parameters = cameraParameters
    }
}