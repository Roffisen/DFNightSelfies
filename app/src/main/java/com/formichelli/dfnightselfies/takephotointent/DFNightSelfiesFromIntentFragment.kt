package com.formichelli.dfnightselfies.takephotointent

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.widget.LinearLayout
import com.formichelli.dfnightselfies.DFNightSelfiesMainFragment
import com.formichelli.dfnightselfies.R
import kotlinx.android.synthetic.main.buttons_intent.*
import java.io.ByteArrayOutputStream

/**
 * Manage request from IMAGE_CAPTURE intent
 */
class DFNightSelfiesFromIntentFragment : DFNightSelfiesMainFragment() {
    override fun getPhotoActionButtons(): LinearLayout = intentButtons

    override fun onClick(v: View) {
        when (v.id) {
            R.id.accept -> {
                val bitmap = bitmap ?: return
                val saveUri = activity.intent.extras.getParcelable<Uri>(MediaStore.EXTRA_OUTPUT) ?: return

                try {
                    val outputStream = activity.contentResolver.openOutputStream(saveUri) ?: return
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    outputStream.close()
                    activity.setResult(RESULT_OK)
                } catch (e: Exception) {
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    // If the intent doesn't contain an URI, send the bitmap as a Parcelable
                    // (it is a good idea to reduce its size to ~50k pixels before)
                    activity.setResult(RESULT_OK, Intent("inline-data").putExtra("data", stream.toByteArray()))
                }

                activity.finish()
            }
            R.id.discard -> {
                restartPreview()
            }
            else -> {
                super.onClick(v)
            }
        }
    }
}
