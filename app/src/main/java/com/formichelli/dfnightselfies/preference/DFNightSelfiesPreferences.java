package com.formichelli.dfnightselfies.preference;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;

import com.formichelli.dfnightselfies.R;
import com.kizitonwose.colorpreference.ColorPreference;

public class DFNightSelfiesPreferences extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
    }


    protected void onResume() {
        super.onResume();

        for (String preferenceKey : getPreferenceScreen().getSharedPreferences().getAll().keySet()) {
            setSummary(preferenceKey);
        }

        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
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
            preference.setSummary(String.format("#%06X", (0xFFFFFF & cp.getValue())));
        }

    }
}
