package com.formichelli.dfnightselfies.util

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import com.formichelli.dfnightselfies.R
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

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
            AlertDialog.Builder(activity).setTitle(activity.getString(R.string.error_title)).setMessage(errorMessage + ".\n" + activity.getString(R.string.application_will_terminate) + ".").setCancelable(false).setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                activity.finish()
            }.create().show()

    fun getOutputFilePath(activity: Activity, photoOrVideo: Boolean, saveToGallery: Boolean): String {
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED)
            throw IOException()

        val mediaStorageDir =
                if (saveToGallery)
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                else
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), activity.getString(R.string.save_to_gallery_folder))

        if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs())
            throw IOException()

        return mediaStorageDir.path + File.separator + "DF_" + (if (photoOrVideo) "IMG" else "VID") + "_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + "." + (if (photoOrVideo) "jpg" else "mp4")
    }

    fun log(context: Context, message: String, showToast: Boolean = false) {
        Log.e("DFNightSelfies", message)
        if (showToast)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}