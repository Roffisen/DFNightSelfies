package com.formichelli.dfnightselfies;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@SuppressWarnings("deprecation")
public class DFNightSelfiesMainActivity extends Activity implements View.OnClickListener, View.OnTouchListener {
    private final static String TAG = "DFNightSelfies";

    LinearLayout cameraPreviewHeightController, mainLayout, buttons;
    FrameLayout cameraPreview, whiteFrame;
    SurfaceView cameraSurface;

    int maxHeight = 0, maxWidth = 0;

    float x, y;

    String cacheFileURI;

    Camera mCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupWindow();

        setContentView(R.layout.activity_dfnight_selfies_main);

        cameraPreviewHeightController = (LinearLayout) findViewById(R.id.vertically_centred_layout);

        whiteFrame = (FrameLayout) findViewById(R.id.white_frame);
        whiteFrame.setOnClickListener(null);
        whiteFrame.setOnTouchListener(null);

        cameraPreview = (FrameLayout) findViewById(R.id.camera_preview);
        cameraPreview.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (mCamera == null) {
                    if (!initializeCamera())
                        exitWithError(0);

                    Toast.makeText(DFNightSelfiesMainActivity.this, getString(R.string.tap_the_screen), Toast.LENGTH_LONG).show();
                }
            }
        });

        mainLayout = (LinearLayout) findViewById(R.id.main_layout);
        mainLayout.setOnClickListener(this);
        mainLayout.setOnTouchListener(this);

        buttons = (LinearLayout) findViewById(R.id.buttons);
        for (int i = 0; i < buttons.getChildCount(); i++)
            buttons.getChildAt(i).setOnClickListener(this);
    }


    @Override
    protected void onStart() {
        super.onStart();

        if (buttons.getVisibility() != View.VISIBLE)
            if (mCamera != null)
                if (!initializeCamera())
                    exitWithError(0);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (buttons.getVisibility() != View.VISIBLE)
            if (mCamera != null)
                mCamera.stopPreview();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mCamera != null) {
            mCamera.release();
            cameraPreview.removeAllViews();
        }
    }

    private void setupWindow() {
        Window w = getWindow();

        // hide title
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // hide statusbar if not on lollipop
        if (android.os.Build.VERSION.SDK_INT < 21)
            w.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // keep screen on
        w.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // set brightness to maximum
        WindowManager.LayoutParams windowAttributes = w.getAttributes();
        windowAttributes.screenBrightness = 1;
        w.setAttributes(windowAttributes);
    }

    private boolean initializeCamera() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA))
            return false;

        Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();
        for (int i = 0, l = Camera.getNumberOfCameras(); i < l; i++) {
            Camera.getCameraInfo(i, mCameraInfo);
            if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                try {
                    mCamera = Camera.open(i);

                    resizePreview();

                    cameraSurface = new CameraPreview(this, mCamera);
                    cameraPreview.addView(cameraSurface);
                    return true;
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    log("Can't open camera " + i + ": " + e.getLocalizedMessage());
                    return false;
                }
            }
        }

        return false;
    }

    private void resizePreview(boolean enlarge) {
        int cameraPreviewWidth = cameraPreview.getWidth();
        int cameraPreviewHeight = cameraPreview.getHeight();

        if (enlarge && cameraPreviewHeight * 2 > maxHeight && cameraPreviewWidth * 2 > maxWidth)
            return;

        LinearLayout.LayoutParams cameraPreviewParams = (LinearLayout.LayoutParams) cameraPreview.getLayoutParams();
        cameraPreviewParams.width = (int) (cameraPreviewWidth * scaleFactor(enlarge));
        cameraPreviewParams.height = (int) (cameraPreviewHeight * scaleFactor(enlarge));
        cameraPreviewParams.weight = 0;
        cameraPreview.setLayoutParams(cameraPreviewParams);
        cameraPreviewHeightController.setLayoutParams(cameraPreviewParams);
    }

    double scaleFactor(boolean enlarge) {
        return (enlarge ? 1.5 : 1 / 1.5);
    }

    private void resizePreview() {
        List<Camera.Size> sizes = mCamera.getParameters().getSupportedPreviewSizes();

        int cameraPreviewWidth = cameraPreview.getWidth();
        int cameraPreviewHeight = cameraPreview.getHeight();

        maxWidth = cameraPreviewWidth * 2;
        maxHeight = cameraPreviewHeight * 2;

        Camera.Size bestSize = getBestSize(sizes);

        LinearLayout.LayoutParams cameraPreviewParams = null;
        switch (((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_0: // portrait
            case Surface.ROTATION_180: // portrait (upside down)
                cameraPreviewParams = (LinearLayout.LayoutParams) cameraPreview.getLayoutParams();
                cameraPreviewParams.width = (int) (((double) cameraPreviewHeight) / bestSize.width * bestSize.height);
                cameraPreviewParams.height = cameraPreview.getHeight();
                break;

            case Surface.ROTATION_90: // landscape (down at left)
            case Surface.ROTATION_270: // landscape (down at right)
                cameraPreviewParams = (LinearLayout.LayoutParams) cameraPreviewHeightController.getLayoutParams();
                cameraPreviewParams.width = cameraPreviewWidth;
                cameraPreviewParams.height = (int) (((double) cameraPreviewWidth) / bestSize.width * bestSize.height);
                break;
        }

        if (cameraPreviewParams != null) {
            cameraPreviewParams.weight = 0;
            cameraPreview.setLayoutParams(cameraPreviewParams);
            cameraPreviewHeightController.setLayoutParams(cameraPreviewParams);
        }
    }

    private Camera.Size getBestSize(List<Camera.Size> sizes) {
        if (sizes.size() == 0)
            throw new IllegalStateException("No sizes available");

        return sizes.get(0);
        /*
        int cameraPreviewHeight = cameraPreview.getHeight();

        Camera.Size bestSize = sizes.get(0);
        int bestDiff = bestSize.width - cameraPreviewHeight;
        if (bestDiff < 0)
            bestDiff = -bestDiff;

        for (int i = 1; i < sizes.size(); i++) {
            Camera.Size thisSize = sizes.get(i);

            int thisDiff = thisSize.width - cameraPreviewHeight;
            if (thisDiff < 0)
                thisDiff = -thisDiff;

            if (thisDiff < bestDiff) {
                bestSize = thisSize;
                bestDiff = thisDiff;
            }
        }

        return bestSize;
        */
    }

    private void exitWithError(int errorMessageId) {
        final AlertDialog errorDialog = new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(getString(errorMessageId) + "\n" + getString(R.string.application_will_terminate))
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
                    }
                }).create();
        errorDialog.show();
    }

    private void log(String message) {
        Log.e(TAG, message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /** A basic Camera preview class */
    public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
        private SurfaceHolder mHolder;
        private Camera mCamera;

        public CameraPreview(Context context, Camera camera) {
            super(context);
            mCamera = camera;

            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            mHolder = getHolder();
            mHolder.addCallback(this);
            // deprecated setting, but required on Android versions prior to 3.0
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        public void surfaceCreated(SurfaceHolder holder) {
            // The Surface has been created, now tell the camera where to draw the preview.
            try {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
            } catch (IOException e) {
                log("Error setting camera preview: " + e.getMessage());
            }
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            // empty. Take care of releasing the Camera preview in your activity.
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            // If your preview can change or rotate, take care of those events here.
            // Make sure to stop the preview before resizing or reformatting it.

            if (mHolder.getSurface() == null){
                // preview surface does not exist
                return;
            }

            // stop preview before making changes
            try {
                mCamera.stopPreview();
            } catch (Exception e){
                // ignore: tried to stop a non-existent preview
            }

            int displayOrientation = 0, cameraRotation = 0;
            switch (((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRotation()) {
                case Surface.ROTATION_0: // portrait
                    displayOrientation = 90;
                    cameraRotation = 270;
                    break;

                case Surface.ROTATION_90: // landscape (down at left)
                    displayOrientation = 0;
                    cameraRotation = 0;
                    break;

                case Surface.ROTATION_180: // portrait (upside donw)
                    displayOrientation = 270;
                    cameraRotation = 90;
                    break;

                case Surface.ROTATION_270: // landscape (down at right)
                    displayOrientation = 180;
                    cameraRotation = 180;
                    break;
            }

            // rotate preview
            mCamera.setDisplayOrientation(displayOrientation);

            // rotate taken photo
            Camera.Parameters mCameraParameters = mCamera.getParameters();
            mCameraParameters.setRotation(cameraRotation);
            mCamera.setParameters(mCameraParameters);

            // set preview size and make any resize, rotate or
            // reformatting changes here

            // start preview with new settings
            try {
                mCamera.setPreviewDisplay(mHolder);
                mCamera.startPreview();

            } catch (Exception e){
                log("Error starting camera preview: " + e.getMessage());
            }
        }
    }

    private Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {
        @Override
        public void onShutter() {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected void onPreExecute() {
                    super.onPreExecute();
                    whiteFrame.setVisibility(View.VISIBLE);
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    super.onPostExecute(aVoid);
                    whiteFrame.setVisibility(View.INVISIBLE);
                }

                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        Thread.sleep(200);
                    } catch (Exception e) {
                        // Nothing to do
                    }

                    return null;
                }
            }.execute();
        }
    };

    private Camera.PictureCallback pictureTakenCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            final File pictureFile = getOutputMediaFile();
            if (pictureFile == null){
                log("Error creating media file, check storage permissions");
                return;
            }

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }
            buttons.setVisibility(View.VISIBLE);
        }

    };

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                x = event.getX();
                y = event.getY();
                break;

            case MotionEvent.ACTION_UP:
                if (Math.abs(event.getY() - y) < 150) {
                    if (Math.abs(event.getX() - x) > 150) {
                        if (event.getX() < x) { // right to left swipe
                            Intent mIntent = new Intent();
                            mIntent.setAction(Intent.ACTION_VIEW);
                            mIntent.setType("image/*");
                            startActivity(mIntent);
                            return true;
                        }
                    }
                }
                break;
        }

        return false;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.main_layout:
                cacheFileURI = null;
                mCamera.takePicture(shutterCallback, null, pictureTakenCallback);
                mainLayout.setOnClickListener(null);
                mainLayout.setOnTouchListener(null);
                break;

            case R.id.save:
                new SingleMediaScanner(this, new File(cacheFileURI));
                restartPreview();
                break;

            case R.id.share:
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.putExtra(Intent.EXTRA_STREAM, cacheFileURI);
                shareIntent.setType("image/jpeg");
                startActivity(Intent.createChooser(shareIntent, getResources().getText(R.string.send_to)));
                break;

            case R.id.discard:
                if (!new File(cacheFileURI).delete())
                    log("Cannot delete cached file: " + cacheFileURI);
                restartPreview();
                break;
        }
    }

    private void restartPreview() {
        cacheFileURI = null;
        mainLayout.setOnClickListener(this);
        mainLayout.setOnTouchListener(this);
        buttons.setVisibility(View.INVISIBLE);
        mCamera.startPreview();

    }

    @Override
    public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                resizePreview(false);
                return true;

            case KeyEvent.KEYCODE_VOLUME_UP:
                resizePreview(true);
                return true;
        }

        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
                return true;
        }

        return false;
    }

    private File getOutputMediaFile(){
        try {
            // To be safe, you should check that the SDCard is mounted
            // using Environment.getExternalStorageState() before doing this.

            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
                throw new IOException();

            File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "DFNightSelfies");
            // This location works best if you want the created images to be shared
            // between applications and persist after your app has been uninstalled.

            // Create the storage directory if it does not exist
            if (!mediaStorageDir.exists())
                if (!mediaStorageDir.mkdirs())
                    throw new IOException();

            // Create a media file name
            cacheFileURI = mediaStorageDir.getPath() + File.separator + "IMG_"+ new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".jpg";

            return new File(cacheFileURI);
        } catch(IOException e) {
            return null;
        }
    }
}
