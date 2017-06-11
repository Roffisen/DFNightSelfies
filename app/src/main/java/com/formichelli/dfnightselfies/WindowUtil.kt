package com.formichelli.dfnightselfies

import android.app.Activity
import android.view.Window
import android.view.WindowManager

object WindowUtil {
    fun setupWindow(activity: Activity) {
        val w = activity.window

        // hide title
        activity.requestWindowFeature(Window.FEATURE_NO_TITLE)

        // hide statusbar if not on lollipop
        if (android.os.Build.VERSION.SDK_INT < 21)
            w.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        // keep screen on
        w.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // set brightness to maximum
        val windowAttributes = w.attributes
        windowAttributes.screenBrightness = 1f
        w.attributes = windowAttributes
    }
}