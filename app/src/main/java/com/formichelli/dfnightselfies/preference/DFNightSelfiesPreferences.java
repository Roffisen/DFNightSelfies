package com.formichelli.dfnightselfies.preference;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.ViewGroup;

import com.formichelli.dfnightselfies.R;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.kizitonwose.colorpreference.ColorPreference;

@SuppressWarnings("deprecation")
public class DFNightSelfiesPreferences extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        addAd();
    }

    protected void onResume() {
        super.onResume();

        for (String preferenceKey : getPreferenceScreen().getSharedPreferences().getAll().keySet()) {
            setSummary(preferenceKey);
        }

        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    private void addAd() {
        // Create the adView
        AdView adView = new AdView(this);
        adView.setAdSize(AdSize.SMART_BANNER);
        adView.setAdUnitId(getString(R.string.banner_ad_unit_id));

        ((ViewGroup) findViewById(android.R.id.list).getParent().getParent().getParent()).addView(adView, 0);

        // Initiate a generic request to load it with an ad
        adView.loadAd(new AdRequest.Builder().addTestDevice("5B5C0102B231DC20553952DAC00561A6").build());
    }

    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        setSummary(key);
    }

    private void setSummary(final String preferenceKey) {
        final Preference preference = findPreference(preferenceKey);

        if (preference instanceof CheckBoxPreference) {
            CheckBoxPreference cbp = (CheckBoxPreference) preference;
            preference.setSummary(getString(cbp.isChecked() ? R.string.enabled : R.string.disabled));
        } else if (preference instanceof ListPreference) {
            ListPreference lp = (ListPreference) preference;
            preference.setSummary(getString(R.string.countdown_preference_summary, Integer.valueOf(lp.getValue())));
        } else if (preference instanceof ColorPreference) {
            ColorPreference cp = (ColorPreference) preference;
            preference.setSummary(intToHexColor(cp));
        }
    }

    private String intToHexColor(ColorPreference cp) {
        return String.format("#%06X", (0xFFFFFF & cp.getValue()));
    }
}
