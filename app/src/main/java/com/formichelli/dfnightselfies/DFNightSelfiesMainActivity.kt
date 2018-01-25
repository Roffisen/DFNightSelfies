package com.formichelli.dfnightselfies

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import com.formichelli.dfnightselfies.util.Util

import com.google.android.gms.ads.MobileAds

open class DFNightSelfiesMainActivity : Activity() {
    private val fragment: DFNightSelfiesMainFragment = DFNightSelfiesMainFragment()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Util.setupWindow(this)

        MobileAds.initialize(applicationContext, getString(R.string.banner_ad_unit_id))

        fragmentManager
                .beginTransaction()
                .replace(android.R.id.content, fragment)
                .commit()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent) = fragment.onKeyDown(keyCode)

    override fun onKeyUp(keyCode: Int, event: KeyEvent) = fragment.onKeyUp(keyCode)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) = fragment.onRequestPermissionsResult(requestCode, permissions, grantResults)
}
