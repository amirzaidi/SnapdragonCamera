/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.camera;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceActivity;
import android.preference.SwitchPreference;

import org.codeaurora.snapcam.R;

import java.util.Map;
import java.util.Set;

public class SettingsActivity extends PreferenceActivity {
    private SettingsManager mSettingsManager;
    private SharedPreferences mSharedPreferences;

    private SharedPreferences.OnSharedPreferenceChangeListener mSharedPreferenceChangeListener
            = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                              String key) {
            Preference p = findPreference(key);
            String value;
            if (p instanceof SwitchPreference) {
                boolean checked = ((SwitchPreference) p).isChecked();
                value = checked ? "on" : "off";
            } else {
                value = ((ListPreference) p).getValue();
            }
            mSettingsManager.setValue(key, value);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSettingsManager = SettingsManager.getInstance();
        addPreferencesFromResource(R.xml.setting_menu_preferences);

        filterPreferences();
        initializePreferences();

        mSharedPreferences = getPreferenceManager().getSharedPreferences();
        mSharedPreferences.registerOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);
    }

    private void filterPreferences() {
        String[] categories = {"photo", "video", "general"};
        Set<String> set = mSettingsManager.getFilteredKeys();
        for (String key : set) {
            Preference p = findPreference(key);
            if (p == null) continue;

            for (int i = 0; i < categories.length; i++) {
                PreferenceGroup group = (PreferenceGroup) findPreference(categories[i]);
                if (group.removePreference(p)) break;
            }
        }
        ListPreference pictureSize = (ListPreference) findPreference(SettingsManager.KEY_PICTURE_SIZE);
        if (pictureSize != null) {
            pictureSize.setEntryValues(mSettingsManager.getEntryValues(SettingsManager.KEY_PICTURE_SIZE));
            pictureSize.setEntries(mSettingsManager.getEntries(SettingsManager.KEY_PICTURE_SIZE));
        }
    }

    private void initializePreferences() {
        ListPreference pref = (ListPreference) findPreference(SettingsManager.KEY_EXPOSURE);
        pref.setEntries(mSettingsManager.getExposureCompensationEntries());
        pref.setEntryValues(mSettingsManager.getExposureCompensationEntryValues());

        Map<String, SettingsManager.Values> map = mSettingsManager.getValuesMap();
        Set<Map.Entry<String, SettingsManager.Values>> set = map.entrySet();

        for (Map.Entry<String, SettingsManager.Values> entry : set) {
            String key = entry.getKey();
            Preference p = findPreference(key);
            if (p == null) continue;

            SettingsManager.Values values = entry.getValue();
            boolean disabled = values.overriddenValue != null;
            String value = disabled ? values.overriddenValue : values.value;
            if (p instanceof SwitchPreference) {
                ((SwitchPreference) p).setChecked(isOn(value));
            } else if (p instanceof ListPreference) {
                ((ListPreference) p).setValue(value);
            }
            if (disabled) p.setEnabled(false);
        }
    }

    private boolean isOn(String value) {
        return value.equals("on") || value.equals("enable");
    }

    @Override
    protected void onStop() {
        super.onStop();
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);
    }
}