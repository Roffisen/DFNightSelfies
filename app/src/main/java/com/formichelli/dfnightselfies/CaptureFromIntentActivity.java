package com.formichelli.dfnightselfies;

import android.content.Intent;
import android.hardware.Camera;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import java.io.OutputStream;

/**
 * Manage request from IMAGE_CAPTURE intent
 */
@SuppressWarnings("deprecation")
public class CaptureFromIntentActivity extends DFNightSelfiesMainActivity {
    private byte[] lastData;

    @Override
    LinearLayout getButtons()
    {
        return (LinearLayout) findViewById(R.id.buttons_intent);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return false;
    }

    @Override
    Camera.PictureCallback getPictureCallback() {
        return new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                lastData = data;

                buttons.setVisibility(View.VISIBLE);
            }
        };
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.accept:
                Uri saveUri = getIntent().getExtras().getParcelable(MediaStore.EXTRA_OUTPUT);

                try {
                    final OutputStream outputStream = getContentResolver().openOutputStream(saveUri);
                    outputStream.write(lastData);
                    outputStream.close();
                    setResult(RESULT_OK);
                } catch (Exception e) {
                    // If the intent doesn't contain an URI, send the bitmap as a Parcelable
                    // (it is a good idea to reduce its size to ~50k pixels before)
                    setResult(RESULT_OK, new Intent("inline-data").putExtra("data", lastData));
                }

                finish();
                break;

            case R.id.discard:
                restartPreview();
                break;
        }
    }
}
