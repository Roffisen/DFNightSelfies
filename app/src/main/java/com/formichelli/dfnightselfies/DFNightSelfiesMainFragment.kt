@file:Suppress("DEPRECATION")

package com.formichelli.dfnightselfies

import android.app.AlertDialog
import android.app.Fragment
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.support.v4.content.FileProvider
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.formichelli.dfnightselfies.preference.DFNightSelfiesPreferences
import com.formichelli.dfnightselfies.preference.PreferenceManager
import com.formichelli.dfnightselfies.util.*
import kotlinx.android.synthetic.main.buttons.*
import kotlinx.android.synthetic.main.fragment_dfnightselfies_main.*
import java.io.File

open class DFNightSelfiesMainFragment : Fragment(), View.OnClickListener {
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var previewManager: PreviewManager
    protected lateinit var cameraManager: CameraManager
    private lateinit var countdownManager: CountdownManager
    private lateinit var stateMachine: StateMachine
    private lateinit var permissionManager: PermissionManager
    private lateinit var previewSizeManager: PreviewSizeManager
    private lateinit var orientationEventListener: MyOrientationEventListener
    private lateinit var mediaScanner: SingleMediaScanner
    protected lateinit var bitmapManager: BitmapManager

    protected open fun getPhotoActionButtons(): LinearLayout = photoActionButtons

    protected open fun getBeforePhotoButtons() = arrayOf(settings, gallery, photoOrVideo, countdown)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_dfnightselfies_main, container, false)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        mediaScanner = SingleMediaScanner(activity)

        preferenceManager = PreferenceManager(activity)
        bitmapManager = BitmapManager(activity, preferenceManager)
        previewManager = PreviewManager(mediaScanner, bitmapManager, photoPreview, videoPreview)
        previewSizeManager = PreviewSizeManager(activity, preferenceManager, cameraPreview, photoPreview, videoPreview)
        orientationEventListener = MyOrientationEventListener(activity)
        countdownManager = CountdownManager(activity, countdown, preferenceManager)
        stateMachine = StateMachine(activity, getPhotoActionButtons(), getBeforePhotoButtons(), countdownManager, previewManager)
        permissionManager = PermissionManager(activity)
        cameraManager = CameraManager(activity, stateMachine, cameraPreview, orientationEventListener, previewSizeManager, bitmapManager, getPhotoActionButtons(), shutterFrame, preferenceManager)
        countdownManager.cameraManager = cameraManager

        setOnClickListeners()
    }

    private fun showWelcomeDialogAndThen(then: () -> Unit) {
        if (preferenceManager.lastRunVersion < 7) {
            AlertDialog.Builder(activity).setTitle(R.string.welcome).setMessage(R.string.welcome_text).setCancelable(false).setNeutralButton("OK") { _, _ ->
                then()
            }.show()
        } else {
            var thenCalled = false
            cameraPreview.viewTreeObserver.addOnGlobalLayoutListener {
                if (!thenCalled) {
                    thenCalled = true
                    then()
                }
            }
        }
    }

    private fun setOnClickListeners() {
        view.setOnClickListener(this)

        settings.setOnClickListener(this)

        gallery.setOnClickListener(this)

        photoOrVideo.setOnClickListener(this)

        countdown.setOnClickListener(this)

        for (i in 0 until getPhotoActionButtons().childCount)
            getPhotoActionButtons().getChildAt(i).setOnClickListener(this)
    }

    override fun onStart() {
        super.onStart()

        setPhotoOrVideoIcon()
        Util.setBackgroundColor(activity, listOf(shutterFrame, view), preferenceManager.color)
        countdownManager.resetText()
        orientationEventListener.enable()

        // The first time show a welcome dialog, the other times initialize camera as soon as the camera preview frame is ready
        showWelcomeDialogAndThen {
            if (permissionManager.checkPermissions()) {
                cameraManager.startPreview()
            }
        }
    }

    override fun onStop() {
        super.onStop()

        cameraManager.release()

        orientationEventListener.disable()
    }

    override fun onDestroy() {
        super.onDestroy()

        cameraManager.release()

        countdownManager.cancel()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) = when {
        permissions.isEmpty() -> {// nothing to do
        }
        permissionManager.checkPermissionResult(grantResults) -> cameraManager.startPreview()
        else -> Util.exitWithError(activity, getString(R.string.permissions_needed))
    }

    fun onKeyUp(keyCode: Int) = when (keyCode) {
        KeyEvent.KEYCODE_BACK -> {
            if (stateMachine.backFromAfterTaking())
                cameraManager.startPreview()
            else
                activity.finish()

            true
        }

        KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP -> {
            if (preferenceManager.takeWithVolume)
                cameraManager.takePictureOrVideo(false)
            else if (getPhotoActionButtons().visibility != View.VISIBLE)
                previewSizeManager.resizePreview(if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) -1 else 1)

            true
        }

        else -> false
    }

    fun onKeyDown(keyCode: Int) = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP

    override fun onClick(v: View) {
        when (v.id) {
            view.id -> {
                countdownManager.cancel()
                if (stateMachine.isDuringTimer()) {
                    stateMachine.onTimerClick()
                } else {
                    cameraManager.takePictureOrVideo(false)
                }
            }

            R.id.save -> {
                previewManager.saveCurrentPreview()
                cameraManager.startPreview()
            }

            R.id.share -> {
                previewManager.saveCurrentPreview()
                startShareIntent(getShareUri())
            }

            R.id.delete -> {
                previewManager.deleteCurrentPreview()
                cameraManager.startPreview()
            }

            R.id.settings -> openSettings()

            R.id.countdown -> countdownManager.onClick(stateMachine)

            R.id.gallery -> showGallery()

            R.id.photoOrVideo -> {
                preferenceManager.switchPictureOrVideo()
                setPhotoOrVideoIcon()
            }
        }
    }

    private fun setPhotoOrVideoIcon() =
            photoOrVideo.setImageResource(if (preferenceManager.pictureOrVideo) R.drawable.video else R.drawable.camera)

    private fun getShareUri() = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".provider", File(mediaScanner.file?.absolutePath))

    private fun showGallery() = startActivity(Intent().setAction(Intent.ACTION_VIEW).setType("image/*"))

    private fun openSettings() = startActivity(Intent(activity, DFNightSelfiesPreferences::class.java))

    private fun startShareIntent(uri: Uri) {
        val type = if (uri.path.endsWith(".jpeg")) "image/jpeg" else "video/mp4"
        val shareIntent = Intent().setAction(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, "#dfnightselfies")
        startActivityForResult(Intent.createChooser(shareIntent, resources.getText(R.string.share)), 0)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
    }
}