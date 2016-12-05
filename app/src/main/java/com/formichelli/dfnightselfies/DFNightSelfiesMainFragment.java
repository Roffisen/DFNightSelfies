package com.formichelli.dfnightselfies;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.media.MediaActionSound;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.formichelli.dfnightselfies.preference.DFNightSelfiesPreferences;
import com.formichelli.dfnightselfies.util.SingleMediaScanner;

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

import static android.app.Activity.RESULT_CANCELED;
import static android.content.Context.WINDOW_SERVICE;

@SuppressWarnings("deprecation")
public class DFNightSelfiesMainFragment extends Fragment implements View.OnClickListener {
    private final static String TAG = "DFNightSelfies";
    private final static double SCALE_FACTOR = 1.5;
    private final static int MAX_SCALE = 1;
    private final static int MIN_SCALE = -2;

    private int cameraFacing;
    SharedPreferences sharedPreferences;
    ImageButton settings;
    ImageButton gallery;
    ImageButton switchCamera;
    TextView countdown;
    LinearLayout photoActionButtons;
    View[] beforePhotoButtons;
    FrameLayout cameraPreview, shutterFrame;
    View mainLayout;
    CameraPreview cameraSurface;
    ImageView photoPreview;
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
    int countdownStart;
    boolean shouldPlaySound;

    Camera mCamera;

    static {
        permissionToIdMap = new HashMap<>();
        permissionToIdMap.put(Manifest.permission.CAMERA, 1);
        permissionToIdMap.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, 2);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dfnight_selfies_main, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        cameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;

        final Activity activity = getActivity();

        permissionGranted = false;

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);

        cameraPreview = (FrameLayout) activity.findViewById(R.id.camera_preview);
        photoPreview = (ImageView) activity.findViewById(R.id.photo_preview);

        mainLayout = getView();
        assert mainLayout != null;
        mainLayout.setOnClickListener(this);

        shutterFrame = (FrameLayout) activity.findViewById(R.id.shutter_frame);

        settings = (ImageButton) activity.findViewById(R.id.settings);
        settings.setOnClickListener(this);

        countdown = (TextView) activity.findViewById(R.id.countdown);
        countdown.setOnClickListener(this);

        gallery = (ImageButton) activity.findViewById(R.id.gallery);
        gallery.setOnClickListener(this);

        switchCamera = (ImageButton) activity.findViewById(R.id.switch_camera);
        switchCamera.setOnClickListener(this);

        beforePhotoButtons = new View[]{settings, gallery, switchCamera, countdown};

        photoActionButtons = getPhotoActionButtons();
        for (int i = 0; i < photoActionButtons.getChildCount(); i++)
            photoActionButtons.getChildAt(i).setOnClickListener(this);

        // The first time show a welcome dialog, the other times initialize camera as soon as the camera preview frame is ready
        if (sharedPreferences.getInt("lastRunVersion", 0) < 7) {
            new AlertDialog.Builder(activity).setTitle(R.string.welcome).setMessage(R.string.welcome_text).setNeutralButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    int currentVersion;
                    try {
                        currentVersion = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode;
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Window window = activity.getWindow();
            window.setStatusBarColor(color);
            window.setNavigationBarColor(color);
        }

        orientationEventListener = new OrientationEventListener(activity, SensorManager.SENSOR_DELAY_UI) {
            Display display = ((WindowManager) activity.getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
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

        showButtons(false);
    }

    protected LinearLayout getPhotoActionButtons() {
        return (LinearLayout) getActivity().findViewById(R.id.buttons);
    }

    @Override
    public void onStart() {
        super.onStart();

        if (photoActionButtons.getVisibility() == View.GONE) {
            if (mCamera == null) {
                if (checkPermissions()) {
                    initializeCamera();
                }
            }
        }

        orientationEventListener.enable();

        color = sharedPreferences.getInt(getString(R.string.color_picker_preference), -1);
        setBackgroundColor(color);

        countdownStart = Integer.valueOf(sharedPreferences.getString(getString(R.string.countdown_preference), "3"));
        countdown.setText(getString(R.string.countdown_string_format, countdownStart));

        shouldPlaySound = sharedPreferences.getBoolean(getString(R.string.shutter_sound_preference), false);
    }

    @Override
    public void onStop() {
        super.onStop();

        if (photoActionButtons.getVisibility() == View.GONE) {
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }
        }

        orientationEventListener.disable();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mCamera != null)
            mCamera.release();

        if (selfTimerFuture != null)
            selfTimerFuture.cancel(true);
    }

    private boolean initializeCamera() {
        if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            return false;
        }

        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }

        Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();
        for (int i = 0, l = Camera.getNumberOfCameras(); i < l; i++) {
            Camera.getCameraInfo(i, mCameraInfo);
            if (mCameraInfo.facing == cameraFacing) {
                try {
                    mCamera = Camera.open(i);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        mCamera.enableShutterSound(false);
                    }

                    initializePreviewSize();

                    cameraSurface = new CameraPreview(getActivity(), mCamera);
                    cameraPreview.removeAllViews();
                    cameraPreview.addView(cameraSurface);
                    return true;
                } catch (RuntimeException e) {
                    log("Can't open camera " + i + ": " + e.getLocalizedMessage());
                    return false;
                }
            }
        }

        return false;
    }

    private boolean checkPermissions() {
        for (final String permission : permissionToIdMap.keySet()) {
            if (ContextCompat.checkSelfPermission(getActivity(), permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions();
                return false;
            }
        }

        permissionGranted = true;
        return true;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(getActivity(), permissionToIdMap.keySet().toArray(new String[permissionToIdMap.size()]), 0);
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

    private void resizePreview(int scaleCount) {
        int cameraPreviewWidth = cameraPreview.getWidth();
        int cameraPreviewHeight = cameraPreview.getHeight();

        if (scaleCount + scale > MAX_SCALE) {
            // don't scale more than MAX_SCALE
            scaleCount = MAX_SCALE - scale;
        } else if (scaleCount + scale < MIN_SCALE) {
            // don't scale less than MIN_SCALE
            scaleCount = MIN_SCALE - scale;
        }

        if (scaleCount == 0) {
            return;
        }

        FrameLayout.LayoutParams cameraPreviewParams = (FrameLayout.LayoutParams) cameraPreview.getLayoutParams();
        cameraPreviewParams.width = (int) (cameraPreviewWidth * scaleFactor(scaleCount));
        cameraPreviewParams.height = (int) (cameraPreviewHeight * scaleFactor(scaleCount));
        cameraPreview.setLayoutParams(cameraPreviewParams);

        scale += scaleCount;
        sharedPreferences.edit().putInt("scaleFactor", scale).apply();
    }

    double scaleFactor(int scaleCount) {
        final boolean shouldInvert;
        if (scaleCount >= 0)
        {
            shouldInvert = false;
        }
        else
        {
            shouldInvert = true;
            scaleCount = -scaleCount;
        }

        double scaleFactor = 1;
        for (int i = 0; i < scaleCount; i++)
        {
            scaleFactor *= SCALE_FACTOR;
        }

        if (shouldInvert)
        {
            scaleFactor = 1 / scaleFactor;
        }

        return scaleFactor;
    }

    private void initializePreviewSize() {
        List<Camera.Size> sizes = mCamera.getParameters().getSupportedPreviewSizes();

        Display display = getActivity().getWindowManager().getDefaultDisplay();
        int cameraPreviewWidth = display.getWidth() / 3;
        int cameraPreviewHeight = display.getHeight() / 3;

        Camera.Size bestSize = getBestSize(sizes);

        boolean portrait = true;
        int displayOrientation = 0, cameraRotation = 0;
        switch (((WindowManager) getActivity().getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRotation()) {
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


        FrameLayout.LayoutParams photoPreviewParams = (FrameLayout.LayoutParams) photoPreview.getLayoutParams();
        photoPreviewParams.width = (int) (cameraPreviewParams.width * SCALE_FACTOR);
        photoPreviewParams.height = (int) (cameraPreviewParams.height * SCALE_FACTOR);
        photoPreview.setLayoutParams(photoPreviewParams);

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

        Camera.Size maxSize = sizes.get(0);
        int maxSizeValue = maxSize.width * maxSize.height;

        for (final Camera.Size size : sizes) {
            final int sizeValue = size.width * size.height;
            if (sizeValue > maxSizeValue) {
                maxSize = size;
                maxSizeValue = sizeValue;
            }
        }

        return maxSize;
    }

    private void exitWithError(int errorMessageId) {
        final AlertDialog errorDialog = new AlertDialog.Builder(getActivity())
                .setTitle("Error")
                .setMessage(getString(errorMessageId) + ".\n" + getString(R.string.application_will_terminate) + ".")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        getActivity().finish();
                    }
                }).create();
        errorDialog.show();
    }

    private void log(String message) {
        Log.e(TAG, message);
        Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
    }

    public void setBackgroundColor(int color) {
        shutterFrame.setBackgroundColor(color);
        mainLayout.setBackgroundColor(color);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Window window = getActivity().getWindow();
            window.setStatusBarColor(color);
            window.setNavigationBarColor(color);
        }
    }

    public boolean onKeyUp(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (photoActionButtons.getVisibility() == View.VISIBLE)
                    discard();
                else
                    getActivity().finish();
                return true;

            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (photoActionButtons.getVisibility() != View.VISIBLE)
                    resizePreview(-1);
                return true;

            case KeyEvent.KEYCODE_VOLUME_UP:
                if (photoActionButtons.getVisibility() != View.VISIBLE)
                    resizePreview(1);
                return true;
        }

        return false;
    }

    public boolean onKeyDown(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
                return true;
        }

        return false;
    }

    private void startPreview() {
        photoPreview.setVisibility(View.GONE);
        cameraSurface.setVisibility(View.VISIBLE);
        mCamera.startPreview();
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
                if (photoActionButtons.getVisibility() == GONE) {
                    startPreview();
                }
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
            if (shouldPlaySound) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    new MediaActionSound().play(MediaActionSound.SHUTTER_CLICK);
                }
            }

            shutterFrame.setVisibility(View.VISIBLE);
        }
    };

    protected Camera.PictureCallback getPictureCallback() {
        return new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                mCamera.stopPreview();
                shutterFrame.setVisibility(View.GONE);

                photoPreview.setImageBitmap(BitmapFactory.decodeByteArray(data, 0, data.length));
                photoPreview.setVisibility(View.VISIBLE);
                cameraSurface.setVisibility(View.GONE);

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

                mediaScanner = new SingleMediaScanner(getActivity(), pictureFile);
                showButtons(true);
            }
        };
    }

    protected void showButtons(boolean isPictureTaken) {
        int showIfPictureTaken = isPictureTaken ? View.VISIBLE : View.GONE;
        int showIfPictureNotTaken = isPictureTaken ? View.GONE : View.VISIBLE;

        photoActionButtons.setVisibility(showIfPictureTaken);
        for (View view : beforePhotoButtons) {
            view.setVisibility(showIfPictureNotTaken);
        }

        if (!isPictureTaken) {
            countdown.setText(getString(R.string.countdown_string_format, countdownStart));
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mainLayout) {
            takePicture();
            return;
        }

        switch (v.getId()) {
            case R.id.save:
                restartPreview();
                getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                break;

            case R.id.share:
                startShareIntent();
                break;

            case R.id.delete:
                discard();
                getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                break;

            case R.id.settings:
                openSettings();
                break;

            case R.id.countdown:
                startSelfTimer();
                break;

            case R.id.gallery:
                showGallery();
                break;

            case R.id.switch_camera:
                switchCamera();
                break;
        }
    }

    private void switchCamera() {
        if (cameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT)
        {
            cameraFacing = Camera.CameraInfo.CAMERA_FACING_BACK;
            switchCamera.setImageResource(R.drawable.camera_front);
        }
        else
        {
            cameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;
            switchCamera.setImageResource(R.drawable.camera_back);
        }

        initializeCamera();
    }

    private void showGallery() {
        Intent mIntent = new Intent();
        mIntent.setAction(Intent.ACTION_VIEW);
        mIntent.setType("image/*");
        startActivity(mIntent);
    }

    private void openSettings() {
        Intent i = new Intent(getActivity(), DFNightSelfiesPreferences.class);
        startActivity(i);
    }

    private void startSelfTimer() {
        if (takingPicture) {
            takingPicture = false;
            selfTimerFuture.cancel(true);
            showButtons(false);
        } else {
            takingPicture = true;
            selfTimerFuture = selfTimerScheduler.scheduleAtFixedRate(new CountDown(countdownStart), 0, 1, TimeUnit.SECONDS);

            for (View view : beforePhotoButtons) {
                if (view != countdown) {
                    view.setVisibility(View.GONE);
                }
            }
        }
    }

    private void startShareIntent() {
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, mediaScanner.getShareUri());
        shareIntent.setType("image/*");
        startActivityForResult(Intent.createChooser(shareIntent, getResources().getText(R.string.share)), 0);
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
    }

    private void takePicture() {
        if (takingPicture) {
            return;
        }

        takingPicture = true;
        mediaScanner = null;
        switch (((WindowManager) getActivity().getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_0: // portrait
                getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;

            case Surface.ROTATION_180:  // portrait (upside down)
                getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
                break;

            case Surface.ROTATION_90: // landscape (down at left)
                getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;

            case Surface.ROTATION_270: // landscape (down at right)
                getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                break;
        }
        mCamera.takePicture(shutterCallback, null, pictureTakenCallback);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_CANCELED)
            if (mCamera != null)
                restartPreview();
    }

    protected void restartPreview() {
        takingPicture = false;
        mediaScanner = null;
        showButtons(false);
        startPreview();
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
        if (getActivity().getContentResolver().delete(mediaScanner.getShareUri(), null, null) == 0)
            log("Cannot delete cached file: " + mediaScanner.getFile().getAbsolutePath());
        restartPreview();
    }

    private class CountDown implements Runnable {
        private int value;

        CountDown(final int start) {
            value = start;
        }

        @Override
        public void run() {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    countdown.setText(String.valueOf(value--));

                    if (value < 0) {
                        selfTimerFuture.cancel(true);
                        takingPicture = false;
                        takePicture();
                    }
                }
            });
        }
    }
}
