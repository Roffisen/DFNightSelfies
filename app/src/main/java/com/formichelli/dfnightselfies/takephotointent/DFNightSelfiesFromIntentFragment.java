package com.formichelli.dfnightselfies.takephotointent;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.widget.LinearLayout;

import com.formichelli.dfnightselfies.DFNightSelfiesMainFragment;
import com.formichelli.dfnightselfies.R;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import static android.app.Activity.RESULT_OK;

/**
 * Manage request from IMAGE_CAPTURE intent
 */
@SuppressWarnings("deprecation")
public class DFNightSelfiesFromIntentFragment extends DFNightSelfiesMainFragment {
    @Override
    protected LinearLayout getPhotoActionButtons() {
        return (LinearLayout) getActivity().findViewById(R.id.buttons_intent);
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.accept:
                Uri saveUri = getActivity().getIntent().getExtras().getParcelable(MediaStore.EXTRA_OUTPUT);

                try {
                    final OutputStream outputStream = getActivity().getContentResolver().openOutputStream(saveUri);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                    outputStream.close();
                    getActivity().setResult(RESULT_OK);
                } catch (Exception e) {
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    // If the intent doesn't contain an URI, send the bitmap as a Parcelable
                    // (it is a good idea to reduce its size to ~50k pixels before)
                    getActivity().setResult(RESULT_OK, new Intent("inline-data").putExtra("data", stream.toByteArray()));
                }

                getActivity().finish();
                break;

            case R.id.discard:
                restartPreview();
                break;

            default:
                super.onClick(v);
                break;
        }
    }
}
