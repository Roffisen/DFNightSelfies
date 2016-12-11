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

        WindowUtil.setupWindow(this);

        MobileAds.initialize(getApplicationContext(), getString(R.string.banner_ad_unit_id));

        fragment = new DFNightSelfiesMainFragment();

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment)
                .commit();
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
