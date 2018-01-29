package com.formichelli.dfnightselfies.preference

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.formichelli.dfnightselfies.R
import com.formichelli.dfnightselfies.takephotointent.DFNightSelfiesFromIntentActivity

@SuppressLint("ApplySharedPref")
class PreferenceManager(private val activity: Activity) {
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)

    val shouldPlaySound: Boolean
        get () {
            return sharedPreferences.getBoolean(activity.getString(R.string.shutter_sound_preference), false)
        }

    val countdownSeconds: Int
        get () {
            return sharedPreferences.getString(activity.getString(R.string.countdown_preference), "3").toInt()
        }

    val rotationFix: Boolean
        get() {
            return sharedPreferences.getBoolean(activity.getString(R.string.rotation_fix_preference), false)
        }

    val saveToGallery: Boolean
        get() {
            return sharedPreferences.getBoolean(activity.getString(R.string.save_to_gallery_preference), false)
        }

    var scale: Int
        get() {
            return sharedPreferences.getInt(activity.getString(R.string.scale_factor_preference), 0)
        }
        set(value) {
            sharedPreferences.edit().putInt(activity.getString(R.string.scale_factor_preference), value).commit()
        }

    val ratio: String
        get() {
            return sharedPreferences.getString(activity.getString(R.string.ratio_preference), "ANY")
        }

    val takeWithVolume: Boolean
        get() {
            return sharedPreferences.getBoolean(activity.getString(R.string.take_with_volume_preference), false)
        }
    val color: Int
        get() {
            return sharedPreferences.getInt(activity.getString(R.string.color_picker_preference), -1)
        }

    val lastRunVersion: Int
        get() {
            val lastRunVersion = sharedPreferences.getInt(activity.getString(R.string.last_run_version_preference), 0)

            val currentVersion = try {
                activity.packageManager.getPackageInfo(activity.packageName, 0).versionCode
            } catch (e: Exception) {
                0
            }

            sharedPreferences.edit().putInt(activity.getString(R.string.last_run_version_preference), currentVersion).commit()

            return lastRunVersion
        }

    val frameRate = 24

    val audioBitRate = 196000

    val audioSamplingRate = 44100

    val pictureOrVideo: Boolean
        get() {
            if (activity is DFNightSelfiesFromIntentActivity) {
                // only allows photos from intent
                return true
            }

            return sharedPreferences.getBoolean(activity.getString(R.string.picture_or_video_preference), true)
        }

    fun switchPictureOrVideo() {
        sharedPreferences.edit().putBoolean(activity.getString(R.string.picture_or_video_preference), !pictureOrVideo).commit()
        pictureOrVideoCallback?.invoke()
    }

    private var pictureOrVideoCallback: (() -> Unit)? = null

    fun pictureOrVideoChanged(callback: () -> Unit) {
        pictureOrVideoCallback = callback
    }
}