package com.formichelli.dfnightselfies

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import com.formichelli.dfnightselfies.util.CameraPreview
import com.formichelli.dfnightselfies.util.CountdownManager
import com.formichelli.dfnightselfies.util.PreviewManager

class StateMachine(private val activity: Activity, private val photoActionButtons: LinearLayout, private val beforePhotoButtons: Array<View>, private val countdownManager: CountdownManager, private val previewManager: PreviewManager) {
    private enum class State { BEFORE_TAKING, DURING_TIMER, WHILE_TAKING, AFTER_TAKING }

    init {
        photoActionButtons.visibility = View.GONE
        showBeforePhotoButtons(false)
    }

    private var currentState = State.BEFORE_TAKING
        set(value) {
            field = value
            showButtons(value)
            enableRotation(value == State.BEFORE_TAKING)
        }


    private fun showButtons(state: State) = when (state) {
        State.BEFORE_TAKING -> {
            photoActionButtons.visibility = View.GONE
            showBeforePhotoButtons(true)
            countdownManager.resetText()
        }

        State.DURING_TIMER -> {
            photoActionButtons.visibility = View.GONE
            showBeforePhotoButtons(false)
            countdownManager.show()
        }

        State.WHILE_TAKING -> {
            photoActionButtons.visibility = View.GONE
            showBeforePhotoButtons(false)
        }

        State.AFTER_TAKING -> {
            photoActionButtons.visibility = View.VISIBLE
            showBeforePhotoButtons(false)
        }
    }

    private fun showBeforePhotoButtons(show: Boolean) {
        beforePhotoButtons.forEach {
            it.visibility = if (show) View.VISIBLE else View.GONE
        }
    }


    private fun enableRotation(enable: Boolean) {
        activity.requestedOrientation = if (enable) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            when ((activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation) {
                Surface.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

                Surface.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT

                Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

                Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE

                else -> activity.requestedOrientation
            }
        }
    }

    fun takingPicture(fromCountdown: Boolean) =
            if (currentState == State.BEFORE_TAKING || (currentState == State.DURING_TIMER && fromCountdown)) {
                currentState = State.WHILE_TAKING
                true
            } else {
                false
            }

    fun onPictureTaken(cameraSurface: CameraPreview, bitmap: Bitmap) {
        onPictureOrVideoTaken(cameraSurface)
        previewManager.showPicturePreview(bitmap)
    }

    fun onVideoTaken(cameraSurface: CameraPreview, videoOutputFile: String) {
        onPictureOrVideoTaken(cameraSurface)
        previewManager.showVideoPreview(videoOutputFile)
    }

    private fun onPictureOrVideoTaken(cameraSurface: CameraPreview) {
        currentState = State.AFTER_TAKING
        cameraSurface.visibility = View.GONE
    }

    fun onStartPreview(cameraSurface: CameraPreview) {
        currentState = State.BEFORE_TAKING

        previewManager.hidePreview()
        cameraSurface.visibility = View.VISIBLE
    }

    fun startOrStopRecording(fromCountdown: Boolean) =
            if (currentState == State.BEFORE_TAKING || (currentState == State.DURING_TIMER && fromCountdown)) {
                currentState = State.WHILE_TAKING
                true
            } else if (currentState == State.WHILE_TAKING) {
                currentState = State.AFTER_TAKING
                false
            } else {
                null
            }

    fun backFromAfterTaking(): Boolean =
            if (currentState == State.AFTER_TAKING) {
                currentState = State.BEFORE_TAKING
                true
            } else {
                false
            }

    fun onTimerClick() =
            if (currentState != State.BEFORE_TAKING) {
                currentState = State.BEFORE_TAKING
                true
            } else {
                currentState = State.DURING_TIMER
                false
            }

    fun isDuringTimer() = currentState == State.DURING_TIMER
}