package com.formichelli.dfnightselfies.util

import android.app.Activity
import android.view.View
import android.widget.TextView
import com.formichelli.dfnightselfies.CameraManager
import com.formichelli.dfnightselfies.R
import com.formichelli.dfnightselfies.StateMachine
import com.formichelli.dfnightselfies.preference.PreferenceManager
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class CountdownManager(private val activity: Activity, private val countdown: TextView, private val preferenceManager: PreferenceManager) {
    private val selfTimerScheduler = Executors.newSingleThreadScheduledExecutor()
    private var selfTimerFuture: ScheduledFuture<*>? = null
    lateinit var cameraManager: CameraManager

    init {
        resetText()
    }

    fun resetText() {
        countdown.text = activity.getString(R.string.countdown_string_format, preferenceManager.countdownSeconds)
    }

    fun show() {
        countdown.visibility = View.VISIBLE
    }

    fun onClick(stateMachine: StateMachine) {
        if (stateMachine.onTimerClick()) {
            cancel()
        } else {
            selfTimerFuture = selfTimerScheduler.scheduleAtFixedRate(CountDown(preferenceManager.countdownSeconds), 0, 1, TimeUnit.SECONDS)
        }
    }

    fun cancel() = selfTimerFuture?.cancel(true)

    private inner class CountDown constructor(private var value: Int) : Runnable {
        override fun run() {
            activity.runOnUiThread {
                try {
                    countdown.text = value--.toString()

                    if (value < 0) {
                        selfTimerFuture?.cancel(true)
                        cameraManager.takePictureOrVideo(true)
                    }
                } finally {
                }
            }
        }
    }
}