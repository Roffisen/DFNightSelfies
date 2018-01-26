package com.formichelli.dfnightselfies.util

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Environment
import com.formichelli.dfnightselfies.R
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class BitmapManager(private val activity: Activity, private val sharedPreferences: SharedPreferences) {
    var bitmap: Bitmap? = null

    private fun rotationFix() = sharedPreferences.getBoolean(activity.getString(R.string.rotation_fix_preference), false)
    private fun saveToGallery() = sharedPreferences.getBoolean(activity.getString(R.string.save_to_gallery_preference), false)

    fun fromByteArray(data: ByteArray, cameraRotation: Int) {
        bitmap = rotate(BitmapFactory.decodeByteArray(data, 0, data.size), cameraRotation)
    }

    private fun rotate(bitmap: Bitmap, cameraRotation: Int): Bitmap {
        return if (rotationFix()) {
            val matrix = Matrix()
            matrix.setRotate(cameraRotation.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    fun saveToFile(mediaScanner: SingleMediaScanner) {
        val bitmap = bitmap ?: return

        val pictureFile = getOutputMediaFile()
        if (pictureFile == null) {
            Util.log(activity, "Error creating media file, check storage permissions")
            return
        }

        try {
            val fos = FileOutputStream(pictureFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.close()
            mediaScanner.scan(pictureFile)
        } catch (e: FileNotFoundException) {
            Util.log(activity, "File not found: " + e.message)
        } catch (e: IOException) {
            Util.log(activity, "Error accessing file: " + e.message)
        }
    }

    private fun getOutputMediaFile(): File? {
        try {
            if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED)
                throw IOException()

            val mediaStorageDir =
                    if (saveToGallery())
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                    else
                        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), activity.getString(R.string.save_to_gallery_folder))

            if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs())
                throw IOException()

            return File(mediaStorageDir.path + File.separator + "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg")
        } catch (e: IOException) {
            return null
        }
    }

}