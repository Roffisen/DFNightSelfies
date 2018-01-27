package com.formichelli.dfnightselfies.util

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat

class PermissionManager(private val activity: Activity) {
    private val permissionToIdMap = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE)

    private var permissionsGranted = false

    fun checkPermissions(): Boolean {
        permissionToIdMap.forEach {
            if (ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions()
                return false
            }
        }

        permissionsGranted = true
        return true
    }

    private fun requestPermissions() = ActivityCompat.requestPermissions(activity, permissionToIdMap, 0)

    fun checkPermissionResult(grantResults: IntArray) = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
}