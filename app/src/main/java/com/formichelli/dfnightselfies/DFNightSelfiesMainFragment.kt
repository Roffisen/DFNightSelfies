@file:Suppress("DEPRECATION")

package com.formichelli.dfnightselfies

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
import android.hardware.Camera
import android.hardware.SensorManager
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.support.v4.content.FileProvider
import android.util.Log
import android.view.*
import android.widget.LinearLayout
import android.widget.Toast
import com.formichelli.dfnightselfies.preference.DFNightSelfiesPreferences
import com.formichelli.dfnightselfies.util.PermissionManager
import com.formichelli.dfnightselfies.util.PreviewSizeManager
import com.formichelli.dfnightselfies.util.SingleMediaScanner
import kotlinx.android.synthetic.main.buttons.*
import kotlinx.android.synthetic.main.fragment_dfnightselfies_main.*
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
    private val logTag = "DFNightSelfies"
    private val shareText = "#dfnightselfies"

    private lateinit var permissionManager: PermissionManager
    private lateinit var previewSizeManager: PreviewSizeManager
    private lateinit var beforePhotoButtons: Array<View>
    private lateinit var cameraSurface: CameraPreview
    private lateinit var orientationEventListener: OrientationEventListener
    private lateinit var mediaScanner: SingleMediaScanner
    private var selfTimerScheduler = Executors.newSingleThreadScheduledExecutor()
    internal var selfTimerFuture: ScheduledFuture<*>? = null
    protected var bitmap: Bitmap? = null

    internal var color: Int = 0
    private var countdownStart: Int = 0
    private var shouldPlaySound: Boolean = false

    internal var camera: Camera? = null
    internal var cameraOrientation: Int = 0

    private var cameraRotation: Int = 0
    private var rotationFix: Boolean = false
    private var saveToGallery: Boolean = false
    private var takeWithVolume: Boolean = false

    private var phase: Phase? = null
        set(value) {
            val fieldValue = value ?: return

            field = fieldValue
            showButtons(fieldValue)
            enableRotation(fieldValue == Phase.BEFORE_TAKING)
        }

    private fun enableRotation(enable: Boolean) {
        activity.requestedOrientation = if (enable) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            when ((activity.getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation) {
                Surface.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

                Surface.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT

                Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

                Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE

                else -> activity.requestedOrientation
            }
        }
    }

    protected open fun getPhotoActionButtons(): LinearLayout = photoActionButtons

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_dfnightselfies_main, container, false)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        permissionManager = PermissionManager(activity)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity) ?: return

        view.setOnClickListener(this)

        settings.setOnClickListener(this)

        gallery.setOnClickListener(this)

        countdown.setOnClickListener(this)

        beforePhotoButtons = arrayOf(settings, gallery, countdown)

        for (i in 0 until getPhotoActionButtons().childCount)
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
                if (permissionManager.checkPermissions() && camera == null) {
                    initializeCamera()
                }
            }
        }

        previewSizeManager = PreviewSizeManager(activity, sharedPreferences, cameraPreview, photoPreview)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.window.statusBarColor = color
            activity.window.navigationBarColor = color
        }

        orientationEventListener = MyOrientationEventListener()

        mediaScanner = SingleMediaScanner(activity)

        phase = Phase.BEFORE_TAKING
    }

    override fun onStart() {
        super.onStart()

        if (phase == Phase.BEFORE_TAKING) {
            if (camera == null) {
                if (permissionManager.checkPermissions()) {
                    initializeCamera()
                }
            }
        }

        orientationEventListener.enable()

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity) ?: return

        color = sharedPreferences.getInt(getString(R.string.color_picker_preference), -1)
        setBackgroundColor(color)

        countdownStart = Integer.valueOf(sharedPreferences.getString(getString(R.string.countdown_preference), "3")) ?: 3
        countdown.text = getString(R.string.countdown_string_format, countdownStart)

        saveToGallery = sharedPreferences.getBoolean(getString(R.string.save_to_gallery_preference), false)
        rotationFix = sharedPreferences.getBoolean(getString(R.string.rotation_fix_preference), false)
        shouldPlaySound = sharedPreferences.getBoolean(getString(R.string.shutter_sound_preference), false)
        takeWithVolume = sharedPreferences.getBoolean(getString(R.string.take_with_volume_preference), false)
    }

    override fun onStop() {
        super.onStop()

        releaseCamera()

        orientationEventListener.disable()
    }

    override fun onDestroy() {
        super.onDestroy()

        releaseCamera()

        selfTimerFuture?.cancel(true)
    }

    private fun initializeCamera(): Boolean {
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA))
            return false

        releaseCamera()

        val mCameraInfo = Camera.CameraInfo()
        for (i in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(i, mCameraInfo)
            if (mCameraInfo.facing != Camera.CameraInfo.CAMERA_FACING_FRONT) {
                continue
            }

            return try {
                camera = Camera.open(i)
                val mCamera = camera ?: return false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    mCamera.enableShutterSound(false)
                }

                previewSizeManager.initializePreviewSize(mCamera)

                cameraSurface = CameraPreview(activity, mCamera)
                cameraPreview.removeAllViews()
                cameraPreview.addView(cameraSurface)
                cameraOrientation = mCameraInfo.orientation
                orientationEventListener.onOrientationChanged(OrientationEventListener.ORIENTATION_UNKNOWN)
                true
            } catch (e: RuntimeException) {
                log("Can't open camera " + i + ": " + e.localizedMessage)
                false
            }
        }

        return false
    }

    private fun releaseCamera() {
        camera?.stopPreview()
        camera?.release()
        camera = null
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

    private fun exitWithError(errorMessageId: Int) {
        AlertDialog.Builder(activity).setTitle("Error").setMessage(getString(errorMessageId) + ".\n" + getString(R.string.application_will_terminate) + ".").setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
            activity.finish()
        }.create().show()
    }

    private fun log(message: String) {
        Log.e(logTag, message)
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    private fun setBackgroundColor(color: Int) {
        shutterFrame.setBackgroundColor(color)
        view.setBackgroundColor(color)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.window.statusBarColor = color
            activity.window.navigationBarColor = color
        }
    }

    fun onKeyUp(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (phase == Phase.AFTER_TAKING)
                    restartPreview()
                else
                    activity.finish()

                return true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP -> {
                if (takeWithVolume)
                    takePicture()
                else if (getPhotoActionButtons().visibility != View.VISIBLE)
                    previewSizeManager.resizePreview(if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) -1 else 1)

                return true
            }
        }

        return false
    }

    fun onKeyDown(keyCode: Int) = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP

    private fun startPreview() {
        val mCamera = camera ?: return

        photoPreview.visibility = View.GONE
        photoPreview.setImageResource(android.R.color.transparent)
        bitmap = null
        cameraSurface.visibility = View.VISIBLE
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
                if (getPhotoActionButtons().visibility == View.GONE)
                    startPreview()
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
        if (shouldPlaySound)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                MediaActionSound().play(MediaActionSound.SHUTTER_CLICK)

        shutterFrame.visibility = View.VISIBLE
    }

    override fun onPictureTaken(data: ByteArray, camera: Camera) {
        val mCamera = this.camera ?: return

        mCamera.stopPreview()
        shutterFrame.visibility = View.GONE

        bitmap = rotate(BitmapFactory.decodeByteArray(data, 0, data.size), cameraRotation)

        photoPreview.setImageBitmap(bitmap)
        photoPreview.visibility = View.VISIBLE
        cameraSurface.visibility = View.GONE

        phase = Phase.AFTER_TAKING
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
            mediaScanner.scan(pictureFile)
        } catch (e: FileNotFoundException) {
            Log.d(logTag, "File not found: " + e.message)
        } catch (e: IOException) {
            Log.d(logTag, "Error accessing file: " + e.message)
        }
    }

    private fun rotate(bitmap: Bitmap, cameraRotation: Int): Bitmap {
        return if (rotationFix) {
            val matrix = Matrix()
            matrix.setRotate(cameraRotation.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    private fun showButtons(phase: Phase) = when (phase) {
        Phase.BEFORE_TAKING -> {
            getPhotoActionButtons().visibility = View.GONE
            showBeforePhotoButtons(true)
            countdown.text = getString(R.string.countdown_string_format, countdownStart)
        }

        Phase.DURING_TIMER -> {
            getPhotoActionButtons().visibility = View.GONE
            showBeforePhotoButtons(false)
            countdown.visibility = View.VISIBLE
        }

        Phase.WHILE_TAKING -> {
            getPhotoActionButtons().visibility = View.GONE
            showBeforePhotoButtons(false)
        }

        Phase.AFTER_TAKING -> {
            getPhotoActionButtons().visibility = View.VISIBLE
            showBeforePhotoButtons(false)
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
            }

            R.id.share -> {
                saveBitmapToFile()
                startShareIntent(getShareUri())
            }

            R.id.delete -> {
                restartPreview()
            }

            R.id.settings -> openSettings()

            R.id.countdown -> startSelfTimer()

            R.id.gallery -> showGallery()
        }
    }

    private fun getShareUri() = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".provider", File(mediaScanner.file?.absolutePath))

    private fun showGallery() {
        val mIntent = Intent()
        mIntent.action = Intent.ACTION_VIEW
        mIntent.type = "image/*"
        startActivity(mIntent)
    }

    private fun openSettings() = startActivity(Intent(activity, DFNightSelfiesPreferences::class.java))

    private fun startSelfTimer() {
        if (phase != Phase.BEFORE_TAKING) {
            phase = Phase.BEFORE_TAKING
            selfTimerFuture?.cancel(true)
        } else {
            phase = Phase.DURING_TIMER
            selfTimerFuture = selfTimerScheduler.scheduleAtFixedRate(CountDown(countdownStart), 0, 1, TimeUnit.SECONDS)
        }
    }

    private fun showBeforePhotoButtons(show: Boolean) {
        beforePhotoButtons.forEach {
            it.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    private fun startShareIntent(pictureUri: Uri) {
        val shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.type = "image/*"
        shareIntent.putExtra(Intent.EXTRA_STREAM, pictureUri)
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText)
        startActivityForResult(Intent.createChooser(shareIntent, resources.getText(R.string.share)), 0)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
    }

    private fun takePicture(force: Boolean = false) {
        if (!force && phase != Phase.BEFORE_TAKING)
            return

        val camera = camera ?: return
        phase = Phase.WHILE_TAKING

        try {
            camera.takePicture(shutterCallback, null, this)
        } catch (e: Exception) {
            restartPreview()
        }
    }

    protected fun restartPreview() {
        phase = Phase.BEFORE_TAKING
        startPreview()
    }

    private fun getOutputMediaFile(): File? {
        try {
            if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED)
                throw IOException()

            val mediaStorageDir =
                    if (saveToGallery)
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                    else
                        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), getString(R.string.save_to_gallery_folder))

            if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs())
                throw IOException()

            return File(mediaStorageDir.path + File.separator + "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg")
        } catch (e: IOException) {
            return null
        }
    }

    private inner class MyOrientationEventListener : OrientationEventListener(activity, SensorManager.SENSOR_DELAY_UI) {
        internal var display = (activity.getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay
        internal var lastDisplayRotation: Int = 0

        override fun onOrientationChanged(orientation: Int) {
            val camera = camera ?: return

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

    private inner class CountDown internal constructor(private var value: Int) : Runnable {
        override fun run() {
            activity.runOnUiThread {
                try {
                    countdown.text = value--.toString()

                    if (value < 0) {
                        selfTimerFuture?.cancel(true)
                        takePicture(true)
                    }
                } finally {
                }
            }
        }
    }
}

enum class Phase {
    BEFORE_TAKING, DURING_TIMER, WHILE_TAKING, AFTER_TAKING
}
