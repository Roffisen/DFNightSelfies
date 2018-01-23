@file:Suppress("DEPRECATION")

package com.formichelli.dfnightselfies

import android.app.AlertDialog
import android.app.Fragment
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.content.FileProvider
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.formichelli.dfnightselfies.preference.DFNightSelfiesPreferences
import com.formichelli.dfnightselfies.util.*
import kotlinx.android.synthetic.main.buttons.*
import kotlinx.android.synthetic.main.fragment_dfnightselfies_main.*
import java.io.File

open class DFNightSelfiesMainFragment : Fragment(), View.OnClickListener {
    private val shareText = "#dfnightselfies"

    protected lateinit var cameraManager: CameraManager
    private lateinit var countdownManager: CountdownManager
    private lateinit var stateMachine: StateMachine
    private lateinit var permissionManager: PermissionManager
    private lateinit var previewSizeManager: PreviewSizeManager
    private lateinit var orientationEventListener: MyOrientationEventListener
    private lateinit var mediaScanner: SingleMediaScanner
    protected lateinit var bitmapManager: BitmapManager

    private var takeWithVolume: Boolean = false

    protected open fun getPhotoActionButtons(): LinearLayout = photoActionButtons

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_dfnightselfies_main, container, false)

    private fun showWelcomeDialogAndThen(sharedPreferences: SharedPreferences, then: () -> Boolean) {
        if (sharedPreferences.getInt("lastRunVersion", 0) < 7) {
            AlertDialog.Builder(activity).setTitle(R.string.welcome).setMessage(R.string.welcome_text).setNeutralButton("OK") { _, _ ->
                val currentVersion = try {
                    activity.packageManager.getPackageInfo(activity.packageName, 0).versionCode
                } catch (e: PackageManager.NameNotFoundException) {
                    0
                }

                sharedPreferences.edit().putInt("lastRunVersion", currentVersion).apply()

                then()
            }.show()
        } else {
            cameraPreview.viewTreeObserver.addOnGlobalLayoutListener {
                if (permissionManager.checkPermissions()) {
                    then()
                }
            }
        }
    }

    private fun setOnClickListeners() {
        view.setOnClickListener(this)

        settings.setOnClickListener(this)

        gallery.setOnClickListener(this)

        countdown.setOnClickListener(this)

        for (i in 0 until getPhotoActionButtons().childCount)
            getPhotoActionButtons().getChildAt(i).setOnClickListener(this)
    }

    override fun onStart() {
        super.onStart()

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity) ?: return

        previewSizeManager = PreviewSizeManager(activity, sharedPreferences, cameraPreview, photoPreview)
        orientationEventListener = MyOrientationEventListener(activity)
        bitmapManager = BitmapManager(activity, sharedPreferences)
        stateMachine = StateMachine(activity, getPhotoActionButtons(), arrayOf(settings, gallery, countdown), countdownManager)
        permissionManager = PermissionManager(activity)
        cameraManager = CameraManager(activity, stateMachine, cameraPreview, orientationEventListener, previewSizeManager, bitmapManager, photoPreview, getPhotoActionButtons(), shutterFrame, sharedPreferences.getBoolean(getString(R.string.shutter_sound_preference), false))
        countdownManager = CountdownManager(activity, cameraManager, countdown, Integer.valueOf(sharedPreferences.getString(getString(R.string.countdown_preference), "3"))
                ?: 3)

        mediaScanner = SingleMediaScanner(activity)

        setOnClickListeners()

        // The first time show a welcome dialog, the other times initialize camera as soon as the camera preview frame is ready
        showWelcomeDialogAndThen(sharedPreferences) { cameraManager.initializeCamera() }
        if (stateMachine.currentState == StateMachine.State.BEFORE_TAKING) {
            if (permissionManager.checkPermissions()) {
                cameraManager.initializeCamera()
            }
        }

        orientationEventListener.enable()

        setBackgroundColor(sharedPreferences.getInt(getString(R.string.color_picker_preference), -1))

        takeWithVolume = sharedPreferences.getBoolean(getString(R.string.take_with_volume_preference), false)
    }

    override fun onStop() {
        super.onStop()

        cameraManager.releaseCamera()

        orientationEventListener.disable()
    }

    override fun onDestroy() {
        super.onDestroy()

        cameraManager.releaseCamera()

        countdownManager.cancel()
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        if (permissionManager.checkPermissionResult(grantResults)) {
            cameraManager.initializeCamera()
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
                if (stateMachine.currentState == StateMachine.State.AFTER_TAKING)
                    cameraManager.restartPreview()
                else
                    activity.finish()

                return true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP -> {
                if (takeWithVolume)
                    cameraManager.takePicture()
                else if (getPhotoActionButtons().visibility != View.VISIBLE)
                    previewSizeManager.resizePreview(if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) -1 else 1)

                return true
            }
        }

        return false
    }

    fun onKeyDown(keyCode: Int) = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP

    override fun onClick(v: View) {
        when (v.id) {
            view.id -> {
                cameraManager.takePicture()
            }

            R.id.save -> {
                bitmapManager.saveToFile(mediaScanner)
                cameraManager.restartPreview()
            }

            R.id.share -> {
                bitmapManager.saveToFile(mediaScanner)
                startShareIntent(getShareUri())
            }

            R.id.delete -> {
                cameraManager.restartPreview()
            }

            R.id.settings -> openSettings()

            R.id.countdown -> countdownManager.onClick(stateMachine)

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

    private fun startShareIntent(pictureUri: Uri) {
        val shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.type = "image/*"
        shareIntent.putExtra(Intent.EXTRA_STREAM, pictureUri)
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText)
        startActivityForResult(Intent.createChooser(shareIntent, resources.getText(R.string.share)), 0)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
    }
}