package com.formichelli.dfnightselfies.util

import android.app.Activity
import android.content.SharedPreferences
import android.view.View
import android.widget.TextView
import com.formichelli.dfnightselfies.CameraManager
import com.formichelli.dfnightselfies.R
import com.formichelli.dfnightselfies.StateMachine
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class CountdownManager(private val activity: Activity, private val countdown: TextView, private val sharedPreferences: SharedPreferences) {
    private val selfTimerScheduler = Executors.newSingleThreadScheduledExecutor()
    private var selfTimerFuture: ScheduledFuture<*>? = null
    lateinit var cameraManager: CameraManager

    private fun countdownSeconds() = sharedPreferences.getString(activity.getString(R.string.countdown_preference), "3").toInt()

    init {
        resetText()
    }

    fun resetText() {
        countdown.text = activity.getString(R.string.countdown_string_format, countdownSeconds())
    }

    fun show() {
        countdown.visibility = View.VISIBLE
    }

    fun onClick(stateMachine: StateMachine) {
        if (stateMachine.currentState != StateMachine.State.BEFORE_TAKING) {
            stateMachine.currentState = StateMachine.State.BEFORE_TAKING
            selfTimerFuture?.cancel(true)
        } else {
            stateMachine.currentState = StateMachine.State.DURING_TIMER
            selfTimerFuture = selfTimerScheduler.scheduleAtFixedRate(CountDown(countdownSeconds()), 0, 1, TimeUnit.SECONDS)
        }
    }

    fun cancel() {
        selfTimerFuture?.cancel(true)
    }

    private inner class CountDown constructor(private var value: Int) : Runnable {
        override fun run() {
            activity.runOnUiThread {
                try {
                    countdown.text = value--.toString()

                    if (value < 0) {
                        selfTimerFuture?.cancel(true)
                        cameraManager.takePicture(true)
                    }
                } finally {
                }
            }
        }
    }
}