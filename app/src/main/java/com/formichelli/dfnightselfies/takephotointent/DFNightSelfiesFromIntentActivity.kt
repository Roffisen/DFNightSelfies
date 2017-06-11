package com.formichelli.dfnightselfies.takephotointent

import android.os.Bundle

import com.formichelli.dfnightselfies.DFNightSelfiesMainActivity
import com.formichelli.dfnightselfies.WindowUtil

class DFNightSelfiesFromIntentActivity : DFNightSelfiesMainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowUtil.setupWindow(this)

        fragmentManager.beginTransaction()
                .replace(android.R.id.content, DFNightSelfiesFromIntentFragment())
                .commit()
    }
}
