package com.formichelli.dfnightselfies.util

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.formichelli.dfnightselfies.R

object Util {
    fun setupWindow(activity: Activity) {
        // hide title
        activity.requestWindowFeature(Window.FEATURE_NO_TITLE)

        // hide status bar if not on lollipop
        if (android.os.Build.VERSION.SDK_INT < 21)
            activity.window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        // keep screen on
        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // set brightness to maximum
        val windowAttributes = activity.window.attributes
        windowAttributes.screenBrightness = 1f
        activity.window.attributes = windowAttributes
    }

    fun setBackgroundColor(activity: Activity, views: List<View>, color: Int) {
        views.forEach {
            it.setBackgroundColor(color)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.window.statusBarColor = color
            activity.window.navigationBarColor = color
        }
    }

    fun exitWithError(activity: Activity, errorMessage: String) =
            AlertDialog.Builder(activity).setTitle(activity.getString(R.string.error_title)).setMessage(errorMessage + ".\n" + activity.getString(R.string.application_will_terminate) + ".").setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                activity.finish()
            }.create().show()
}