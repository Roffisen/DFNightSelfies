package com.formichelli.dfnightselfies;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.rarepebble.colorpicker.ColorPickerView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("deprecation")
public class DFNightSelfiesMainActivity extends Activity implements View.OnClickListener, View.OnTouchListener {
    private final static String TAG = "DFNightSelfies";
    private final static double SCALE_FACTOR = 1.5;
    private final static int MAX_SCALE = 1;
    private final static int MIN_SCALE = -2;

    SharedPreferences sharedPreferences;
    ImageButton picker;
    ImageButton timer;
    LinearLayout buttons;
    FrameLayout cameraPreview, mainLayout, shutterFrame;
    CameraPreview cameraSurface;
    SingleMediaScanner mediaScanner;
    OrientationEventListener orientationEventListener;
    Camera.PictureCallback pictureTakenCallback = getPictureCallback();
    ScheduledExecutorService selfTimerScheduler = Executors.newSingleThreadScheduledExecutor();
    ScheduledFuture selfTimerFuture;
    private final static Map<String, Integer> permissionToIdMap;

    boolean takingPicture;
    private boolean permissionGranted;

    int scale;
    int color;

    float x, y;

    Camera mCamera;

    static {
        permissionToIdMap = new HashMap<>();
        permissionToIdMap.put(Manifest.permission.CAMERA, 1);
        permissionToIdMap.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, 2);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        permissionGranted = false;

        setupWindow();

        setContentView(R.layout.activity_dfnight_selfies_main);

        cameraPreview = (FrameLayout) findViewById(R.id.camera_preview);

        mainLayout = (FrameLayout) findViewById(R.id.main_layout);
        mainLayout.setOnTouchListener(this);

        shutterFrame = (FrameLayout) findViewById(R.id.shutter_frame);

        picker = (ImageButton) findViewById(R.id.picker);
        timer = (ImageButton) findViewById(R.id.timer);

        buttons = getButtons();
        for (int i = 0; i < buttons.getChildCount(); i++)
            buttons.getChildAt(i).setOnClickListener(this);

        // The first time show a welcome dialog, the other times initialize camera as soon as the camera preview frame is ready
        sharedPreferences = getSharedPreferences("DFNightSelfies", MODE_PRIVATE);
        if (sharedPreferences.getInt("lastRunVersion", 0) < 7) {
            new AlertDialog.Builder(DFNightSelfiesMainActivity.this).setTitle(R.string.welcome).setMessage(R.string.welcome_text).setNeutralButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    int currentVersion;
                    try {
                        currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                    } catch (PackageManager.NameNotFoundException e) {
                        currentVersion = 0;
                    }
                    sharedPreferences.edit().putInt("lastRunVersion", currentVersion).apply();

                    initializeCamera();
                }
            }).show();
        } else {
            cameraPreview.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (permissionGranted && mCamera == null) {
                        initializeCamera();
                    }
                }
            });
        }

        scale = sharedPreferences.getInt("scaleFactor", 0);

        color = sharedPreferences.getInt("color", Color.WHITE);
        mainLayout.setBackgroundColor(color);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Window window = getWindow();
            window.setStatusBarColor(color);
            window.setNavigationBarColor(color);
        }

        orientationEventListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_UI) {
            Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
            int lastRotation = display.getRotation();

            @Override
            public void onOrientationChanged(int orientation) {
                synchronized (this) {
                    int rotation = display.getRotation();
                    if (lastRotation == rotation)
                        return;

                    int displayOrientation = -1, cameraRotation = -1;

                    switch (rotation) {
                        case Surface.ROTATION_0: // portrait
                            if (lastRotation != Surface.ROTATION_180)
                                break;
                            displayOrientation = 90;
                            cameraRotation = 270;
                            break;

                        case Surface.ROTATION_180:  // portrait (upside down)
                            if (lastRotation != Surface.ROTATION_0)
                                break;

                            displayOrientation = 270;
                            cameraRotation = 90;
                            break;

                        case Surface.ROTATION_90: // landscape (down at right)
                            if (lastRotation != Surface.ROTATION_270)
                                break;

                            displayOrientation = 0;
                            cameraRotation = 0;
                            break;

                        case Surface.ROTATION_270: // landscape (down at left)
                            if (lastRotation != Surface.ROTATION_90)
                                break;

                            displayOrientation = 180;
                            cameraRotation = 180;
                            break;
                    }

                    lastRotation = rotation;

                    if (displayOrientation == -1)
                        return;

                    mCamera.setDisplayOrientation(displayOrientation);
                    Camera.Parameters mCameraParameters = mCamera.getParameters();
                    mCameraParameters.setRotation(cameraRotation);
                    mCamera.setParameters(mCameraParameters);
                }
            }
        };
    }

    LinearLayout getButtons() {
        return (LinearLayout) findViewById(R.id.buttons);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (buttons.getVisibility() == View.INVISIBLE) {
            if (mCamera == null) {
                if (checkPermissions()) {
                    initializeCamera();
                }
            }
        }

        orientationEventListener.enable();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (buttons.getVisibility() == View.INVISIBLE) {
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.release();
            }
        }

        orientationEventListener.disable();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mCamera != null)
            mCamera.release();

        if (selfTimerFuture != null)
            selfTimerFuture.cancel(true);
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
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            return false;
        }

        Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();
        for (int i = 0, l = Camera.getNumberOfCameras(); i < l; i++) {
            Camera.getCameraInfo(i, mCameraInfo);
            if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                try {
                    mCamera = Camera.open(i);

                    resizePreview();

                    cameraSurface = new CameraPreview(this, mCamera);
                    cameraPreview.removeAllViews();
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

    private boolean checkPermissions() {
        for (final String permission : permissionToIdMap.keySet()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions();
                return false;
            }
        }

        permissionGranted = true;
        return true;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, permissionToIdMap.keySet().toArray(new String[permissionToIdMap.size()]), 0);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        if (grantResults.length > 0) {
            for (final int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    exitWithError(R.string.cant_get_front_camera);
                    return;
                }
            }

            initializeCamera();
        } else {
            exitWithError(R.string.cant_get_front_camera);
        }
    }

    private void resizePreview(boolean enlarge) {
        int cameraPreviewWidth = cameraPreview.getWidth();
        int cameraPreviewHeight = cameraPreview.getHeight();

        if (enlarge) {
            if (scale == MAX_SCALE)
                return;
        } else {
            if (scale == MIN_SCALE)
                return;
        }

        FrameLayout.LayoutParams cameraPreviewParams = (FrameLayout.LayoutParams) cameraPreview.getLayoutParams();
        cameraPreviewParams.width = (int) (cameraPreviewWidth * scaleFactor(enlarge));
        cameraPreviewParams.height = (int) (cameraPreviewHeight * scaleFactor(enlarge));
        cameraPreview.setLayoutParams(cameraPreviewParams);

        scale += enlarge ? 1 : -1;
        sharedPreferences.edit().putInt("scaleFactor", scale).apply();
    }

    double scaleFactor(boolean enlarge) {
        return (enlarge ? SCALE_FACTOR : 1 / SCALE_FACTOR);
    }

    private void resizePreview() {
        List<Camera.Size> sizes = mCamera.getParameters().getSupportedPreviewSizes();

        Display display = getWindowManager().getDefaultDisplay();
        int cameraPreviewWidth = display.getWidth() / 3;
        int cameraPreviewHeight = display.getHeight() / 3;

        Camera.Size bestSize = getBestSize(sizes);

        boolean portrait = true;
        int displayOrientation = 0, cameraRotation = 0;
        switch (((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_0: // portrait
                portrait = true;
                displayOrientation = 90;
                cameraRotation = 270;
                break;

            case Surface.ROTATION_180:  // portrait (upside down)
                portrait = true;
                displayOrientation = 270;
                cameraRotation = 90;
                break;

            case Surface.ROTATION_90: // landscape (down at left)
                portrait = false;
                displayOrientation = 0;
                cameraRotation = 0;
                break;

            case Surface.ROTATION_270: // landscape (down at right)
                portrait = false;
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

        FrameLayout.LayoutParams cameraPreviewParams = (FrameLayout.LayoutParams) cameraPreview.getLayoutParams();
        if (portrait) {
            cameraPreviewParams.width = (int) (((double) cameraPreviewHeight) / bestSize.width * bestSize.height);
            cameraPreviewParams.height = cameraPreviewHeight;
        } else {
            cameraPreviewParams.width = cameraPreviewWidth;
            cameraPreviewParams.height = (int) (((double) cameraPreviewWidth) / bestSize.width * bestSize.height);
        }

        float scaleFactor = 1;
        int lastScale = scale;
        while (lastScale != 0) {
            if (lastScale > 0) {
                scaleFactor *= SCALE_FACTOR;
                lastScale--;
            } else {
                scaleFactor /= SCALE_FACTOR;
                lastScale++;
            }
        }

        cameraPreviewParams.width *= scaleFactor;
        cameraPreviewParams.height *= scaleFactor;
        cameraPreview.setLayoutParams(cameraPreviewParams);

        mCameraParameters = mCamera.getParameters();
        mCameraParameters.setPictureFormat(PixelFormat.JPEG);
        bestSize = getBestSize(mCameraParameters.getSupportedPreviewSizes());
        mCameraParameters.setPreviewSize(bestSize.width, bestSize.height);
        bestSize = getBestSize(mCameraParameters.getSupportedPictureSizes());
        mCameraParameters.setPictureSize(bestSize.width, bestSize.height);
        mCamera.setParameters(mCameraParameters);

    }

    private Camera.Size getBestSize(List<Camera.Size> sizes) {
        if (sizes.size() == 0)
            throw new IllegalStateException("No sizes available");

        return sizes.get(0);
    }

    private void exitWithError(int errorMessageId) {
        final AlertDialog errorDialog = new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(getString(errorMessageId) + ".\n" + getString(R.string.application_will_terminate) + ".")
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

    /**
     * A basic Camera preview class
     */
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

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            // The Surface has been created, now tell the camera where to draw the preview.
            try {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
            } catch (IOException e) {
                log("Error setting camera preview: " + e.getMessage());
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // camera release is managed by activity
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            // Surface can't change
        }
    }

    private Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {
        @Override
        public void onShutter() {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected void onPreExecute() {
                    super.onPreExecute();
                    shutterFrame.setVisibility(View.VISIBLE);
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    super.onPostExecute(aVoid);
                    shutterFrame.setVisibility(View.INVISIBLE);
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

    Camera.PictureCallback getPictureCallback() {
        return new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                final File pictureFile = getOutputMediaFile();
                if (pictureFile == null) {
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


                mediaScanner = new SingleMediaScanner(DFNightSelfiesMainActivity.this, pictureFile);
                showButtons(true);
            }
        };
    }

    void showButtons(boolean isPictureTaken) {
        int showIfPictureTaken = isPictureTaken ? View.VISIBLE : View.INVISIBLE;
        int showIfPictureNotTaken = isPictureTaken ? View.INVISIBLE : View.VISIBLE;

        buttons.setVisibility(showIfPictureTaken);
        picker.setVisibility(showIfPictureNotTaken);
        timer.setVisibility(showIfPictureNotTaken);
        if (!isPictureTaken) {
            picker.setImageResource(R.drawable.picker);
            timer.setImageResource(R.drawable.timer);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (takingPicture) {
            return false;
        }

        switch (event.getAction()) {
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
                takePicture();
                break;

            case R.id.save:
                restartPreview();
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                break;

            case R.id.share:
                startShareIntent();
                break;

            case R.id.delete:
                discard();
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                break;

            case R.id.picker:
                showColorPicker();
                break;

            case R.id.timer:
                startSelfTimer();
                break;
        }
    }

    private void startSelfTimer() {
        if (takingPicture) {
            return;
        }

        takingPicture = true;
        selfTimerFuture = selfTimerScheduler.scheduleAtFixedRate(new CountDown(3), 0, 1, TimeUnit.SECONDS);
    }

    private void startShareIntent() {
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, mediaScanner.shareUri);
        shareIntent.setType("image/*");
        startActivityForResult(Intent.createChooser(shareIntent, getResources().getText(R.string.share)), 0);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
    }

    private void showColorPicker() {
        if (takingPicture) {
            return;
        }

        final ColorPickerView picker = new ColorPickerView(this);
        picker.setAlpha(1);
        picker.showAlpha(false);
        picker.setColor(color);
        picker.setOriginalColor(color);
        new AlertDialog.Builder(this)
                .setTitle(null)
                .setView(picker)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //color = Color.parseColor("#" + Integer.toHexString(picker.getColor()));
                        color = picker.getColor();
                        sharedPreferences.edit().putInt("color", color).apply();
                        mainLayout.setBackgroundColor(color);

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            final Window window = getWindow();
                            window.setStatusBarColor(color);
                            window.setNavigationBarColor(color);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create()
                .show();
    }

    private void takePicture() {
        if (takingPicture) {
            return;
        }

        takingPicture = true;
        mediaScanner = null;
        switch (((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_0: // portrait
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;

            case Surface.ROTATION_180:  // portrait (upside down)
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
                break;

            case Surface.ROTATION_90: // landscape (down at left)
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;

            case Surface.ROTATION_270: // landscape (down at right)
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                break;
        }
        mCamera.takePicture(shutterCallback, null, pictureTakenCallback);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_CANCELED)
            if (mCamera != null)
                restartPreview();
    }

    void restartPreview() {
        takingPicture = false;
        mediaScanner = null;
        showButtons(false);
        mCamera.startPreview();
    }

    @Override
    public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (buttons.getVisibility() == View.VISIBLE)
                    discard();
                else
                    finish();
                return true;

            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (buttons.getVisibility() != View.VISIBLE)
                    resizePreview(false);
                return true;

            case KeyEvent.KEYCODE_VOLUME_UP:
                if (buttons.getVisibility() != View.VISIBLE)
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

    private File getOutputMediaFile() {
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

            // Create the file
            return new File(mediaStorageDir.getPath() + File.separator + "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg");
        } catch (IOException e) {
            return null;
        }
    }

    void discard() {
        if (getContentResolver().delete(mediaScanner.shareUri, null, null) == 0)
            log("Cannot delete cached file: " + mediaScanner.mFile.getAbsolutePath());
        restartPreview();
    }

    private class CountDown implements Runnable {
        private int value;

        public CountDown(final int start) {
            value = start;
        }

        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setIcon(value--);

                    if (value < 0) {
                        selfTimerFuture.cancel(true);
                        takingPicture = false;
                        takePicture();
                    }
                }
            });
        }

        private void setIcon(int value) {
            final int resourceId;
            switch (value) {
                case 0:
                    resourceId = R.drawable.transparent;
                    break;

                case 1:
                    resourceId = R.drawable.one;
                    break;

                case 2:
                    resourceId = R.drawable.two;
                    break;

                case 3:
                    resourceId = R.drawable.three;
                    picker.setImageResource(R.drawable.transparent);
                    break;

                default:
                    return;
            }

            timer.setImageResource(resourceId);
        }
    }
}
