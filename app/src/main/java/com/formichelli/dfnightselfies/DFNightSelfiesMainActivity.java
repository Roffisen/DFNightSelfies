package com.formichelli.dfnightselfies;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;

import com.google.android.gms.ads.MobileAds;

public class DFNightSelfiesMainActivity extends Activity {
    DFNightSelfiesMainFragment fragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupWindow();

        MobileAds.initialize(getApplicationContext(), getString(R.string.banner_ad_unit_id));

        fragment = new DFNightSelfiesMainFragment();

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment)
                .commit();
    }

    protected void setupWindow() {
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

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return fragment.onKeyDown(keyCode);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return fragment.onKeyUp(keyCode);
    }
}
