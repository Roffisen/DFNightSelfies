package com.formichelli.dfnightselfies.util

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat

class PermissionManager(private val activity: Activity) {
    private val permissionToIdMap = mutableMapOf(
            Manifest.permission.CAMERA to 1,
            Manifest.permission.WRITE_EXTERNAL_STORAGE to 2
    )

    private var permissionsGranted = false

    fun checkPermissions(): Boolean {
        permissionToIdMap.keys.forEach {
            if (ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions()
                return false
            }
        }

        permissionsGranted = true
        return true
    }

    private fun requestPermissions() = ActivityCompat.requestPermissions(activity, permissionToIdMap.keys.toTypedArray(), 0)

    fun checkPermissionResult(grantResults: IntArray) = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
}