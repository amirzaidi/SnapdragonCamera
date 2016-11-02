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
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.util.Log;
import android.widget.Toast;

import org.codeaurora.snapcam.R;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class SettingsActivity extends PreferenceActivity {
    private SettingsManager mSettingsManager;
    private SharedPreferences mSharedPreferences;
    private boolean mDeveloperMenuEnabled;
    private int privateCounter = 0;
    private final int DEVELOPER_MENU_TOUCH_COUNT = 10;

    private SharedPreferences.OnSharedPreferenceChangeListener mSharedPreferenceChangeListener
            = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                              String key) {
            Preference p = findPreference(key);
            if (p == null) return;
            String value;
            if (p instanceof SwitchPreference) {
                boolean checked = ((SwitchPreference) p).isChecked();
                value = checked ? "on" : "off";
                mSettingsManager.setValue(key, value);
            } else if (p instanceof ListPreference){
                value = ((ListPreference) p).getValue();
                mSettingsManager.setValue(key, value);
            }
            if (key.equals(SettingsManager.KEY_VIDEO_QUALITY)) {
                updatePreference(SettingsManager.KEY_VIDEO_HIGH_FRAME_RATE);
            }
            List<String> list = mSettingsManager.getDependentKeys(key);
            if (list != null) {
                for (String dependentKey : list) {
                    updatePreferenceButton(dependentKey);
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSettingsManager = SettingsManager.getInstance();
        if (mSettingsManager == null) {
            finish();
            return;
        }
        addPreferencesFromResource(R.xml.setting_menu_preferences);

        mSharedPreferences = getPreferenceManager().getSharedPreferences();
        mDeveloperMenuEnabled = mSharedPreferences.getBoolean(SettingsManager.KEY_DEVELOPER_MENU, false);

        filterPreferences();
        initializePreferences();

        mSharedPreferences.registerOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);

        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            PreferenceCategory category = (PreferenceCategory) getPreferenceScreen().getPreference(i);
            for (int j = 0; j < category.getPreferenceCount(); j++) {
                Preference pref = category.getPreference(j);
                pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (!mDeveloperMenuEnabled) {
                            if (preference.getKey().equals("version_info")) {
                                privateCounter++;
                                if (privateCounter >= DEVELOPER_MENU_TOUCH_COUNT) {
                                    mDeveloperMenuEnabled = true;
                                    mSharedPreferences.edit().putBoolean(SettingsManager.KEY_DEVELOPER_MENU, true).apply();
                                    Toast.makeText(SettingsActivity.this, "Camera developer option is enabled now", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                privateCounter = 0;
                            }
                        }
                        return false;
                    }

                });
            }
        }

    }

    private void filterPreferences() {
        String[] categories = {"photo", "video", "general"};
        Set<String> set = mSettingsManager.getFilteredKeys();
        if (!mDeveloperMenuEnabled) {
            set.add(SettingsManager.KEY_MONO_PREVIEW);
            set.add(SettingsManager.KEY_MONO_ONLY);
            set.add(SettingsManager.KEY_CLEARSIGHT);
        }

        for (String key : set) {
            Preference p = findPreference(key);
            if (p == null) continue;

            for (int i = 0; i < categories.length; i++) {
                PreferenceGroup group = (PreferenceGroup) findPreference(categories[i]);
                if (group.removePreference(p)) break;
            }
        }
    }

    private void initializePreferences() {
        updatePreference(SettingsManager.KEY_PICTURE_SIZE);
        updatePreference(SettingsManager.KEY_VIDEO_QUALITY);
        updatePreference(SettingsManager.KEY_EXPOSURE);
        updatePreference(SettingsManager.KEY_VIDEO_HIGH_FRAME_RATE);

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
                ListPreference pref = (ListPreference) p;
                pref.setValue(value);
                if (pref.getEntryValues().length == 1) {
                    pref.setEnabled(false);
                }
            }
            if (disabled) p.setEnabled(false);
        }

        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            int index = versionName.indexOf(' ');
            versionName = versionName.substring(0, index);
            findPreference("version_info").setSummary(versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void updatePreferenceButton(String key) {
        ListPreference pref = (ListPreference) findPreference(key);
        if (pref != null) {
            if (pref.getEntryValues().length == 1) {
                pref.setEnabled(false);
            } else {
                pref.setEnabled(true);
            }
        }
    }

    private void updatePreference(String key) {
        ListPreference pref = (ListPreference) findPreference(key);
        if (pref != null) {
            pref.setEntries(mSettingsManager.getEntries(key));
            pref.setEntryValues(mSettingsManager.getEntryValues(key));
            pref.setValueIndex(mSettingsManager.getValueIndex(key));
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