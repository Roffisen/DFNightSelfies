package com.formichelli.dfnightselfies.takephotointent;

import android.os.Bundle;

import com.formichelli.dfnightselfies.DFNightSelfiesMainActivity;

public class DFNightSelfiesFromIntentActivity extends DFNightSelfiesMainActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupWindow();

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new DFNightSelfiesFromIntentFragment())
                .commit();
    }
}
