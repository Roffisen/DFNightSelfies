package com.formichelli.dfnightselfies.util

import android.content.Context
import android.util.Log
import android.widget.Toast

object LogHelper {
    fun log(context: Context, message: String, showToast: Boolean = false) {
        Log.e("DFNightSelfies", message)
        if (showToast)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
