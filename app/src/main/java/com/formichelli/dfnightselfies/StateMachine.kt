package com.formichelli.dfnightselfies

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import com.formichelli.dfnightselfies.util.CountdownManager

class StateMachine(private val activity: Activity, private val photoActionButtons: LinearLayout, private val beforePhotoButtons: Array<View>, private val countdownManager: CountdownManager) {
    enum class State { BEFORE_TAKING, DURING_TIMER, WHILE_TAKING, AFTER_TAKING }

    var currentState = State.BEFORE_TAKING
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
}