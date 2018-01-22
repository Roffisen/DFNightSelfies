@file:Suppress("DEPRECATION")

package com.formichelli.dfnightselfies

import android.app.AlertDialog
import android.app.Fragment
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.hardware.Camera
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.content.FileProvider
import android.view.*
import android.widget.LinearLayout
import com.formichelli.dfnightselfies.preference.DFNightSelfiesPreferences
import com.formichelli.dfnightselfies.util.*
import kotlinx.android.synthetic.main.buttons.*
import kotlinx.android.synthetic.main.fragment_dfnightselfies_main.*
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

open class DFNightSelfiesMainFragment : Fragment(), View.OnClickListener, Camera.PictureCallback {
    private val shareText = "#dfnightselfies"

    private lateinit var permissionManager: PermissionManager
    private lateinit var previewSizeManager: PreviewSizeManager
    private lateinit var beforePhotoButtons: Array<View>
    private lateinit var cameraSurface: CameraPreview
    private lateinit var orientationEventListener: MyOrientationEventListener
    private lateinit var mediaScanner: SingleMediaScanner
    private lateinit var bitmapManager: BitmapManager
    private var selfTimerScheduler = Executors.newSingleThreadScheduledExecutor()
    internal var selfTimerFuture: ScheduledFuture<*>? = null

    internal var color: Int = 0
    private var countdownStart: Int = 0
    private var shouldPlaySound: Boolean = false

    private var camera: Camera? = null

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

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity) ?: return

        permissionManager = PermissionManager(activity)

        previewSizeManager = PreviewSizeManager(activity, sharedPreferences, cameraPreview, photoPreview)

        bitmapManager = BitmapManager(activity, sharedPreferences)

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.window.statusBarColor = color
            activity.window.navigationBarColor = color
        }

        orientationEventListener = MyOrientationEventListener(activity)

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
                orientationEventListener.setCamera(mCamera, mCameraInfo.orientation)
                orientationEventListener.onOrientationChanged(OrientationEventListener.ORIENTATION_UNKNOWN)
                true
            } catch (e: RuntimeException) {
                LogHelper.log(activity, "Can't open camera " + i + ": " + e.localizedMessage)
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
        if (permissionManager.checkPermissionResult(grantResults)) {
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
        bitmapManager.bitmap = null
        cameraSurface.visibility = View.VISIBLE
        mCamera.startPreview()
    }

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

        bitmapManager.fromByteArray(data, orientationEventListener.cameraRotation)

        photoPreview.setImageBitmap(bitmapManager.bitmap)
        photoPreview.visibility = View.VISIBLE
        cameraSurface.visibility = View.GONE

        phase = Phase.AFTER_TAKING
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
                bitmapManager.saveToFile(mediaScanner)
                restartPreview()
            }

            R.id.share -> {
                bitmapManager.saveToFile(mediaScanner)
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