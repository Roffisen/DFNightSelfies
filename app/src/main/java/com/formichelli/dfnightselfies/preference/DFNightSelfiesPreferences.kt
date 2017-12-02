@file:Suppress("DEPRECATION")

package com.formichelli.dfnightselfies.preference

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import android.preference.CheckBoxPreference
import android.preference.ListPreference
import android.preference.PreferenceActivity
import android.preference.SwitchPreference
import android.view.View
import android.view.ViewGroup
import com.formichelli.dfnightselfies.R
import com.formichelli.dfnightselfies.WindowUtil
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.kizitonwose.colorpreference.ColorPreference

class DFNightSelfiesPreferences : PreferenceActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    public override fun onCreate(savedInstanceState: Bundle?) {
        WindowUtil.setupWindow(this)

        super.onCreate(savedInstanceState)

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences)

        addAd()
    }

    override fun onResume() {
        super.onResume()

        preferenceScreen.sharedPreferences.all.keys.forEach {
            setSummary(it)
        }

        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    private fun addAd() {
        // Create the adView
        val adView = AdView(this)
        adView.adSize = AdSize.SMART_BANNER
        adView.adUnitId = getString(R.string.banner_ad_unit_id)

        (findViewById<View>(android.R.id.list).parent.parent.parent as ViewGroup).addView(adView, 0)

        // Initiate a generic request to load it with an ad
        adView.loadAd(AdRequest.Builder().addTestDevice("5B5C0102B231DC20553952DAC00561A6").build())
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) = setSummary(key)

    private fun setSummary(preferenceKey: String) {
        val preference = findPreference(preferenceKey)

        when (preference) {
            is CheckBoxPreference -> {
                if (preferenceKey == getString(R.string.save_to_gallery_preference)) {
                    val path = if (preferenceScreen.sharedPreferences.getBoolean(getString(R.string.save_to_gallery_preference), false)) {
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath
                    } else {
                        Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_PICTURES).absolutePath + "/" + getString(R.string.save_to_gallery_folder)
                    }

                    preference.setSummary(getString(R.string.save_to_gallery_summary, Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_PICTURES).toString() + path))
                } else {
                    preference.setSummary(getString(if (preference.isChecked) R.string.enabled else R.string.disabled))
                }
            }
            is ListPreference -> {
                preference.setSummary(preference.value)
            }
            is ColorPreference -> {
                preference.setSummary(intToHexColor(preference))
            }
            is SwitchPreference -> {
                if (preferenceKey == getString(R.string.take_with_volume_preference)) {
                    val isEnabled = preferenceScreen.sharedPreferences.getBoolean(getString(R.string.take_with_volume_preference), false)
                    preference.setSummary(getString(if (isEnabled) R.string.take_with_volume_summary_true else R.string.take_with_volume_summary_false))
                } else {
                    preference.setSummary(getString(if (preference.isChecked) R.string.enabled else R.string.disabled))
                }
            }
        }
    }

    private fun intToHexColor(cp: ColorPreference) = String.format("#%06X", 0xFFFFFF and cp.value)
}
