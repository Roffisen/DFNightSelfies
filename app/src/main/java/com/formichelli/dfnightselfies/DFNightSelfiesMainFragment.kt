@file:Suppress("DEPRECATION")

package com.formichelli.dfnightselfies

import android.Manifest
import android.app.Activity.RESULT_CANCELED
import android.app.AlertDialog
import android.app.Fragment
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.hardware.Camera
import android.hardware.SensorManager
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.formichelli.dfnightselfies.preference.DFNightSelfiesPreferences
import com.formichelli.dfnightselfies.util.SingleMediaScanner
import kotlinx.android.synthetic.main.buttons.*
import kotlinx.android.synthetic.main.fragment_dfnight_selfies_main.*
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

open class DFNightSelfiesMainFragment : Fragment(), View.OnClickListener, Camera.PictureCallback {
    private val TAG = "DFNightSelfies"
    private val SHARE_TEXT = "#dfnightselfies"
    private val SCALE_FACTOR = 1.5
    private val MAX_SCALE = 1
    private val MIN_SCALE = -2
    private val permissionToIdMap = mutableMapOf(
            Manifest.permission.CAMERA to 1,
            Manifest.permission.WRITE_EXTERNAL_STORAGE to 2
    )

    private var cameraFacing: Int = 0
    internal var beforePhotoButtons: Array<View>? = null
    internal var cameraSurface: CameraPreview? = null
    internal var mediaScanner: SingleMediaScanner? = null
    internal var orientationEventListener: OrientationEventListener? = null
    internal var selfTimerScheduler = Executors.newSingleThreadScheduledExecutor()
    internal var selfTimerFuture: ScheduledFuture<*>? = null
    protected var bitmap: Bitmap? = null

    internal var takingPicture: Boolean = false
    private var permissionGranted = false

    internal var scale: Int = 0
    internal var color: Int = 0
    internal var countdownStart: Int = 0
    internal var shouldPlaySound: Boolean = false

    internal var mCamera: Camera? = null
    internal var cameraOrientation: Int = 0

    private var cameraRotation: Int = 0
    private var rotationFix: Boolean = false
    private var saveToGallery: Boolean = false

    open protected fun getPhotoActionButtons() : LinearLayout = photoActionButtons
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dfnight_selfies_main, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity) ?: return

        view.setOnClickListener(this)

        settings.setOnClickListener(this)

        gallery.setOnClickListener(this)

        countdown.setOnClickListener(this)

        beforePhotoButtons = arrayOf(settings, gallery, countdown)

        for (i in 0..getPhotoActionButtons().childCount - 1)
            getPhotoActionButtons().getChildAt(i).setOnClickListener(this)

        // The first time show a welcome dialog, the other times initialize camera as soon as the camera preview frame is ready
        if (sharedPreferences.getInt("lastRunVersion", 0) < 7) {
            AlertDialog.Builder(activity).setTitle(R.string.welcome).setMessage(R.string.welcome_text).setNeutralButton("OK") { _, _ ->
                val currentVersion = try {
                    activity.packageManager.getPackageInfo(activity.packageName, 0).versionCode
                } catch (e: PackageManager.NameNotFoundException) {
                    0
                }

                sharedPreferences.edit().putInt("lastRunVersion", currentVersion).apply()

                initializeCamera()
            }.show()
        } else {
            cameraPreview.viewTreeObserver.addOnGlobalLayoutListener {
                if (permissionGranted && mCamera == null) {
                    initializeCamera()
                }
            }
        }

        scale = sharedPreferences.getInt("scaleFactor", 0)

        cameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val window = activity.window
            window.statusBarColor = color
            window.navigationBarColor = color
        }

        orientationEventListener = object : OrientationEventListener(activity, SensorManager.SENSOR_DELAY_UI) {
            internal var display = (activity.getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay
            internal var lastDisplayRotation: Int = 0

            override fun onOrientationChanged(orientation: Int) {
                val mCamera = mCamera ?: return

                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
                    lastDisplayRotation = OrientationEventListener.ORIENTATION_UNKNOWN
                }

                val displayRotation = getDisplayRotation()
                if (lastDisplayRotation == displayRotation) {
                    return
                } else {
                    lastDisplayRotation = displayRotation
                }

                synchronized(this) {
                    mCamera.setDisplayOrientation(getDisplayOrientation(displayRotation))
                    cameraRotation = getCameraRotation(displayRotation)
                    try {
                        val cameraParameters = mCamera.parameters
                        cameraParameters.setRotation(cameraRotation)
                        mCamera.parameters = cameraParameters
                    } catch (e: Exception) {
                        // nothing to do
                    }
                }
            }

            private fun getCameraRotation(displayOrientation: Int): Int {
                return when (cameraFacing) {
                    Camera.CameraInfo.CAMERA_FACING_FRONT -> {
                        (cameraOrientation - displayOrientation + 360) % 360
                    }
                    else -> {
                        (cameraOrientation + displayOrientation) % 360
                    }
                }
            }

            private fun getDisplayRotation() = when (display.rotation) {
                Surface.ROTATION_0 -> 0

                Surface.ROTATION_90 -> 270

                Surface.ROTATION_180 -> 180

                Surface.ROTATION_270 -> 90

                else -> -1
            }

            private fun getDisplayOrientation(displayOrientation: Int) = (displayOrientation + 90) % 360
        }

        showButtons(false)
    }

    override fun onStart() {
        super.onStart()

        if (getPhotoActionButtons().visibility == View.GONE) {
            if (mCamera == null) {
                if (checkPermissions()) {
                    initializeCamera()
                }
            }
        }

        orientationEventListener?.enable()

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity) ?: return

        color = sharedPreferences.getInt(getString(R.string.color_picker_preference), -1)
        setBackgroundColor(color)

        countdownStart = Integer.valueOf(sharedPreferences.getString(getString(R.string.countdown_preference), "3")) ?: 3
        countdown.text = getString(R.string.countdown_string_format, countdownStart)

        saveToGallery = sharedPreferences.getBoolean(getString(R.string.save_to_gallery_preference), false)
        rotationFix = sharedPreferences.getBoolean(getString(R.string.rotation_fix_preference), false)
        shouldPlaySound = sharedPreferences.getBoolean(getString(R.string.shutter_sound_preference), false)
    }

    override fun onStop() {
        super.onStop()

        releaseCamera()

        orientationEventListener?.disable()
    }

    override fun onDestroy() {
        super.onDestroy()

        releaseCamera()

        selfTimerFuture?.cancel(true)
    }

    private fun initializeCamera(): Boolean {
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            return false
        }

        releaseCamera()

        val mCameraInfo = Camera.CameraInfo()
        for (i in 0..Camera.getNumberOfCameras() - 1) {
            Camera.getCameraInfo(i, mCameraInfo)
            if (mCameraInfo.facing != cameraFacing) {
                continue
            }

            return try {
                mCamera = Camera.open(i)
                val mCamera = mCamera ?: return false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    mCamera.enableShutterSound(false)
                }

                initializePreviewSize()

                cameraSurface = CameraPreview(activity, mCamera)
                cameraPreview.removeAllViews()
                cameraPreview.addView(cameraSurface)
                cameraOrientation = mCameraInfo.orientation
                orientationEventListener?.onOrientationChanged(OrientationEventListener.ORIENTATION_UNKNOWN)
                true
            } catch (e: RuntimeException) {
                log("Can't open camera " + i + ": " + e.localizedMessage)
                false
            }
        }

        return false
    }

    private fun releaseCamera() {
        mCamera?.stopPreview()
        mCamera?.release()
        mCamera = null
    }

    private fun checkPermissions(): Boolean {
        permissionToIdMap.keys.forEach {
            if (ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions()
                return false
            }
        }

        permissionGranted = true
        return true
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(activity, permissionToIdMap.keys.toTypedArray(), 0)
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        if (grantResults.isNotEmpty()) {
            grantResults.forEach {
                if (it != PackageManager.PERMISSION_GRANTED) {
                    exitWithError(R.string.cant_get_front_camera)
                    return
                }
            }

            initializeCamera()
        } else {
            exitWithError(R.string.cant_get_front_camera)
        }
    }

    private fun resizePreview(scaleCount: Int) {
        val cameraPreviewWidth = cameraPreview.width
        val cameraPreviewHeight = cameraPreview.height

        val effectiveScaleCount =
                if (scaleCount + scale > MAX_SCALE) {
                    // don't scale more than MAX_SCALE
                    MAX_SCALE - scale
                } else if (scaleCount + scale < MIN_SCALE) {
                    // don't scale less than MIN_SCALE
                    MIN_SCALE - scale
                } else {
                    scaleCount
                }

        if (effectiveScaleCount == 0) {
            return
        }

        val cameraPreviewParams = cameraPreview.layoutParams as FrameLayout.LayoutParams
        cameraPreviewParams.width = (cameraPreviewWidth * scaleFactor(effectiveScaleCount)).toInt()
        cameraPreviewParams.height = (cameraPreviewHeight * scaleFactor(effectiveScaleCount)).toInt()
        cameraPreview.layoutParams = cameraPreviewParams

        scale += effectiveScaleCount

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity) ?: return
        sharedPreferences.edit().putInt("scaleFactor", scale).apply()
    }

    internal fun scaleFactor(scaleCount: Int): Double {
        val shouldInvert = scaleCount < 0
        val absScaleCount = if (shouldInvert) {
            scaleCount
        } else {
            scaleCount
        }

        var scaleFactor = 1.0
        for (i in 1..absScaleCount) {
            scaleFactor *= SCALE_FACTOR
        }

        return if (shouldInvert) {
            1 / scaleFactor
        } else {
            scaleFactor
        }
    }

    private fun initializePreviewSize() {
        val mCamera = mCamera ?: return
        val sizes = mCamera.parameters.supportedPreviewSizes

        val display = activity.windowManager.defaultDisplay
        val cameraPreviewWidth = display.width / 3
        val cameraPreviewHeight = display.height / 3

        var bestSize = getBestSize(sizes)

        val rotation = (activity.getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        val (portrait, displayOrientation, cameraRotation) = when (rotation) {
            Surface.ROTATION_0 -> {
                // portrait
                Triple(true, 90, 270)
            }
            Surface.ROTATION_180 -> {
                // portrait (upside down)
                Triple(true, 270, 90)
            }
            Surface.ROTATION_90 -> {
                // landscape (down at left)
                Triple(false, 0, 0)
            }
            Surface.ROTATION_270 -> {
                // landscape (down at right)
                Triple(false, 180, 180)
            }
            else -> {
                Triple(true, 0, 0)
            }
        }

        // rotate preview
        mCamera.setDisplayOrientation(displayOrientation)

        // rotate taken photo
        var mCameraParameters: Camera.Parameters = mCamera.parameters
        mCameraParameters.setRotation(cameraRotation)
        mCamera.parameters = mCameraParameters

        val cameraPreviewParams = cameraPreview.layoutParams as FrameLayout.LayoutParams
        if (portrait) {
            cameraPreviewParams.width = (cameraPreviewHeight.toDouble() / bestSize.width * bestSize.height).toInt()
            cameraPreviewParams.height = cameraPreviewHeight
        } else {
            cameraPreviewParams.width = cameraPreviewWidth
            cameraPreviewParams.height = (cameraPreviewWidth.toDouble() / bestSize.width * bestSize.height).toInt()
        }


        val photoPreviewParams = photoPreview.layoutParams as FrameLayout.LayoutParams
        photoPreviewParams.width = (cameraPreviewParams.width * SCALE_FACTOR).toInt()
        photoPreviewParams.height = (cameraPreviewParams.height * SCALE_FACTOR).toInt()
        photoPreview.layoutParams = photoPreviewParams

        var scaleFactor = 1f
        var lastScale = scale
        while (lastScale != 0) {
            if (lastScale > 0) {
                scaleFactor *= SCALE_FACTOR.toFloat()
                lastScale--
            } else {
                scaleFactor /= SCALE_FACTOR.toFloat()
                lastScale++
            }
        }

        cameraPreviewParams.width *= scaleFactor.toInt()
        cameraPreviewParams.height *= scaleFactor.toInt()
        cameraPreview.layoutParams = cameraPreviewParams

        mCameraParameters = mCamera.parameters
        mCameraParameters.pictureFormat = PixelFormat.JPEG
        bestSize = getBestSize(mCameraParameters.supportedPreviewSizes)
        mCameraParameters.setPreviewSize(bestSize.width, bestSize.height)
        bestSize = getBestSize(mCameraParameters.supportedPictureSizes)
        mCameraParameters.setPictureSize(bestSize.width, bestSize.height)
        mCamera.parameters = mCameraParameters

    }

    private fun getBestSize(sizes: List<Camera.Size>): Camera.Size {
        if (sizes.isEmpty())
            throw IllegalStateException("No sizes available")

        var maxSize: Camera.Size = sizes[0]
        var maxSizeValue = maxSize.width * maxSize.height

        sizes.forEach {
            val sizeValue = it.width * it.height
            if (sizeValue > maxSizeValue) {
                maxSize = it
                maxSizeValue = sizeValue
            }
        }

        return maxSize
    }

    private fun exitWithError(errorMessageId: Int) {
        val errorDialog = AlertDialog.Builder(activity)
                .setTitle("Error")
                .setMessage(getString(errorMessageId) + ".\n" + getString(R.string.application_will_terminate) + ".")
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    activity.finish()
                }.create()
        errorDialog.show()
    }

    private fun log(message: String) {
        Log.e(TAG, message)
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    fun setBackgroundColor(color: Int) {
        shutterFrame.setBackgroundColor(color)
        view.setBackgroundColor(color)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val window = activity.window
            window.statusBarColor = color
            window.navigationBarColor = color
        }
    }

    fun onKeyUp(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (getPhotoActionButtons().visibility == View.VISIBLE)
                    restartPreview()
                else
                    activity.finish()
                return true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (getPhotoActionButtons().visibility != View.VISIBLE)
                    resizePreview(-1)
                return true
            }

            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (getPhotoActionButtons().visibility != View.VISIBLE)
                    resizePreview(1)
                return true
            }
        }

        return false
    }

    fun onKeyDown(keyCode: Int) = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP

    private fun startPreview() {
        val mCamera = mCamera ?: return

        photoPreview.visibility = View.GONE
        photoPreview.setImageResource(android.R.color.transparent)
        bitmap = null
        cameraSurface?.visibility = View.VISIBLE
        mCamera.startPreview()
    }

    /**
     * A basic Camera preview class
     */
    inner class CameraPreview(context: Context, private val mCamera: Camera?) : SurfaceView(context), SurfaceHolder.Callback {
        private val mHolder: SurfaceHolder = holder

        init {
            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            mHolder.addCallback(this)
            // deprecated setting, but required on Android versions prior to 3.0
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        }

        override fun surfaceCreated(holder: SurfaceHolder) {
            val mCamera = mCamera ?: return

            // The Surface has been created, now tell the camera where to draw the preview.
            try {
                mCamera.setPreviewDisplay(holder)
                if (getPhotoActionButtons().visibility == View.GONE) {
                    startPreview()
                }
            } catch (e: Exception) {
                log("Error setting camera preview: " + e.message)
            }

        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            // camera release is managed by activity
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            // Surface can't change
        }
    }

    private val shutterCallback = Camera.ShutterCallback {
        if (shouldPlaySound) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                MediaActionSound().play(MediaActionSound.SHUTTER_CLICK)
            }
        }

        shutterFrame.visibility = View.VISIBLE
    }

    override fun onPictureTaken(data: ByteArray, camera: Camera) {
        val mCamera = mCamera ?: return
        val cameraSurface = cameraSurface ?: return

        mCamera.stopPreview()
        shutterFrame.visibility = View.GONE

        bitmap = rotate(BitmapFactory.decodeByteArray(data, 0, data.size), cameraRotation)

        photoPreview.setImageBitmap(bitmap)
        photoPreview.visibility = View.VISIBLE
        cameraSurface.visibility = View.GONE

        showButtons(true)
    }

    private fun saveBitmapToFile() {
        val pictureFile = getOutputMediaFile()
        if (pictureFile == null) {
            log("Error creating media file, check storage permissions")
            return
        }

        try {
            val fos = FileOutputStream(pictureFile)
            bitmap?.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.close()
            mediaScanner = SingleMediaScanner(activity, pictureFile)
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "File not found: " + e.message)
        } catch (e: IOException) {
            Log.d(TAG, "Error accessing file: " + e.message)
        }
    }

    private fun rotate(bitmap: Bitmap, cameraRotation: Int): Bitmap {
        if (rotationFix) {
            val matrix = Matrix()
            matrix.setRotate(cameraRotation.toFloat())
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            return bitmap
        }
    }

    protected fun showButtons(isPictureTaken: Boolean) {
        val showIfPictureTaken = if (isPictureTaken) View.VISIBLE else View.GONE
        val showIfPictureNotTaken = if (isPictureTaken) View.GONE else View.VISIBLE

        getPhotoActionButtons().visibility = showIfPictureTaken
        beforePhotoButtons?.forEach {
            it.visibility = showIfPictureNotTaken
        }

        if (!isPictureTaken) {
            countdown.text = getString(R.string.countdown_string_format, countdownStart)
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            view.id -> {
                takePicture()
            }

            R.id.save -> {
                saveBitmapToFile()
                restartPreview()
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            }

            R.id.share -> {
                saveBitmapToFile()
                val shareUri = Uri.parse("file://" + mediaScanner?.file?.absolutePath)
                startShareIntent(shareUri)
            }

            R.id.delete -> {
                restartPreview()
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            }

            R.id.settings -> openSettings()

            R.id.countdown -> startSelfTimer()

            R.id.gallery -> showGallery()
        }
    }

    private fun showGallery() {
        val mIntent = Intent()
        mIntent.action = Intent.ACTION_VIEW
        mIntent.type = "image/*"
        startActivity(mIntent)
    }

    private fun openSettings() = startActivity(Intent(activity, DFNightSelfiesPreferences::class.java))

    private fun startSelfTimer() {
        if (takingPicture) {
            takingPicture = false
            selfTimerFuture?.cancel(true)
            showButtons(false)
        } else {
            takingPicture = true
            selfTimerFuture = selfTimerScheduler.scheduleAtFixedRate(CountDown(countdownStart), 0, 1, TimeUnit.SECONDS)

            beforePhotoButtons?.forEach {
                if (it !== countdown) {
                    it.visibility = View.GONE
                }
            }
        }
    }

    private fun startShareIntent(pictureUri: Uri) {
        val shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.type = "image/*"
        shareIntent.putExtra(Intent.EXTRA_STREAM, pictureUri)
        shareIntent.putExtra(Intent.EXTRA_TEXT, SHARE_TEXT)
        startActivityForResult(Intent.createChooser(shareIntent, resources.getText(R.string.share)), 0)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
    }

    private fun takePicture() {
        if (takingPicture) {
            return
        }
        val mCamera = mCamera ?: return

        takingPicture = true
        mediaScanner = null
        when ((activity.getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation) {
            Surface.ROTATION_0 -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

            Surface.ROTATION_180 -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT

            Surface.ROTATION_90 -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

            Surface.ROTATION_270 -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        }
        mCamera.takePicture(shutterCallback, null, this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_CANCELED)
            if (mCamera != null)
                restartPreview()
    }

    protected fun restartPreview() {
        takingPicture = false
        mediaScanner = null
        showButtons(false)
        startPreview()
    }

    private fun getOutputMediaFile(): File? {
        try {
            if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED)
                throw IOException()

            val mediaStorageDir =
                    if (saveToGallery) {
                        Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DCIM)
                    } else {
                        File(Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_PICTURES), getString(R.string.save_to_gallery_folder))
                    }

            if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs())
                throw IOException()

            return File(mediaStorageDir.path + File.separator + "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg")
        } catch (e: IOException) {
            return null
        }
    }

    private inner class CountDown internal constructor(private var value: Int) : Runnable {
        override fun run() {
            activity.runOnUiThread {
                countdown.text = value--.toString()

                if (value < 0) {
                    selfTimerFuture?.cancel(true)
                    takingPicture = false
                    takePicture()
                }
            }
        }
    }
}
