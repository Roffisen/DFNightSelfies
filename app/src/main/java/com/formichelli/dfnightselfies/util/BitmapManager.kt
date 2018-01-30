package com.formichelli.dfnightselfies.util

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.formichelli.dfnightselfies.preference.PreferenceManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class BitmapManager(private val activity: Activity, private val preferenceManager: PreferenceManager) {
    var bitmap: Bitmap? = null

    fun fromByteArray(data: ByteArray, cameraRotation: Int): Bitmap {
        val bitmap = rotate(BitmapFactory.decodeByteArray(data, 0, data.size), cameraRotation)
        this.bitmap = bitmap
        return bitmap
    }

    private fun rotate(bitmap: Bitmap, cameraRotation: Int): Bitmap {
        return if (preferenceManager.rotationFix) {
            val matrix = Matrix()
            matrix.setRotate(cameraRotation.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    fun saveToFile(bitmap: Bitmap): File? =
            try {
                val outFile = File(Util.getOutputFilePath(activity, true, preferenceManager.saveToGallery))
                val fos = FileOutputStream(outFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                fos.close()
                outFile
            } catch (e: IOException) {
                Util.log(activity, "Error creating media file, check storage permissions")
                null
            }
}