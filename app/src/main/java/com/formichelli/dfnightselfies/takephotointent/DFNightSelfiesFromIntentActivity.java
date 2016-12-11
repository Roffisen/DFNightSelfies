package com.formichelli.dfnightselfies.takephotointent;

import android.os.Bundle;

import com.formichelli.dfnightselfies.DFNightSelfiesMainActivity;
import com.formichelli.dfnightselfies.WindowUtil;

public class DFNightSelfiesFromIntentActivity extends DFNightSelfiesMainActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowUtil.setupWindow(this);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new DFNightSelfiesFromIntentFragment())
                .commit();
    }
}
