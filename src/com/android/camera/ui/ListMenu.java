/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.android.camera.ListPreference;
import com.android.camera.PreferenceGroup;
import com.android.camera.SettingsManager;

import org.codeaurora.snapcam.R;

import java.util.ArrayList;
import java.util.List;

/* A popup window that contains several camera settings. */
public class ListMenu extends ListView
        implements ListMenuItem.Listener,
        AdapterView.OnItemClickListener,
        ListSubMenu.Listener {
    @SuppressWarnings("unused")
    private static final String TAG = "ListMenu";
    private int mHighlighted = -1;
    private Listener mListener;
    private SettingsManager mSettingsManager;
    private ArrayList<ListPreference> mListItem = new ArrayList<ListPreference>();

    // Keep track of which setting items are disabled
    // e.g. White balance will be disabled when scene mode is set to non-auto
    private boolean[] mEnabled;
    private boolean mForCamera2 = false;

    @Override
    public void onListPrefChanged(ListPreference pref) {
        // listen from ListSubMenu
        if (mListener != null) {
            mListener.onSettingChanged(pref);
        }
        if (mSettingsManager != null) {
            mSettingsManager.onSettingChanged(pref);
        }
    }

    static public interface Listener {
        public void onSettingChanged(ListPreference pref);

        public void onPreferenceClicked(ListPreference pref);

        public void onPreferenceClicked(ListPreference pref, int y);

        public void onListMenuTouched();
    }

    static public interface SettingsListener {
        // notify SettingsManager
        public void onSettingChanged(ListPreference pref);
    }

    private class MoreSettingAdapter extends ArrayAdapter<ListPreference> {
        LayoutInflater mInflater;
        String mOnString;
        String mOffString;

        MoreSettingAdapter() {
            super(ListMenu.this.getContext(), 0, mListItem);
            Context context = getContext();
            mInflater = LayoutInflater.from(context);
            mOnString = context.getString(R.string.setting_on);
            mOffString = context.getString(R.string.setting_off);
        }

        private int getSettingLayoutId(ListPreference pref) {
            return R.layout.list_menu_item;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ListPreference pref = mListItem.get(position);
            int viewLayoutId = getSettingLayoutId(pref);
            ListMenuItem view = (ListMenuItem) convertView;

            view = (ListMenuItem)
                    mInflater.inflate(viewLayoutId, parent, false);

            view.initialize(pref); // no init for restore one
            view.setSettingChangedListener(ListMenu.this);
            if (position >= 0 && position < mEnabled.length) {
                view.setEnabled(mEnabled[position]);
                if (mForCamera2 && !mEnabled[position]) {
                    view.overrideSettings(mSettingsManager.getValue(pref.getKey()));
                }
            } else {
                Log.w(TAG, "Invalid input: enabled list length, " + mEnabled.length
                        + " position " + position);
            }
            if (position == mHighlighted)
                view.setBackgroundColor(getContext().getResources()
                        .getColor(R.color.setting_color));
            return view;
        }

        @Override
        public boolean isEnabled(int position) {
            if (position >= 0 && position < mEnabled.length) {
                return mEnabled[position];
            }
            return true;
        }
    }

    public void setSettingsManager(SettingsManager settingsManager) {
        mSettingsManager = settingsManager;
    }

    public void setSettingChangedListener(Listener listener) {
        mListener = listener;
    }

    public ListMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void initializeForCamera2(String[] keys) {
        mForCamera2 = true;
        PreferenceGroup group = mSettingsManager.getPreferenceGroup();
        List<String> disabledList = mSettingsManager.getDisabledList();
        // Prepare the setting items.
        for (int i = 0; i < keys.length; ++i) {
            ListPreference pref = group.findPreference(keys[i]);
            if (pref != null)
                mListItem.add(pref);
        }

        ArrayAdapter<ListPreference> mListItemAdapter = new MoreSettingAdapter();
        setAdapter(mListItemAdapter);
        setOnItemClickListener(this);
        setSelector(android.R.color.transparent);
        // Initialize mEnabled
        mEnabled = new boolean[mListItem.size()];
        for (int i = 0; i < mEnabled.length; i++) {
            mEnabled[i] = true;
        }

        for (String s: disabledList) {
            setPreferenceEnabled(s, false);
        }
    }

    public void initialize(PreferenceGroup group, String[] keys) {
        // Prepare the setting items.
        for (int i = 0; i < keys.length; ++i) {
            ListPreference pref = group.findPreference(keys[i]);
            if (pref != null)
                mListItem.add(pref);
        }

        ArrayAdapter<ListPreference> mListItemAdapter = new MoreSettingAdapter();
        setAdapter(mListItemAdapter);
        setOnItemClickListener(this);
        setSelector(android.R.color.transparent);
        // Initialize mEnabled
        mEnabled = new boolean[mListItem.size()];
        for (int i = 0; i < mEnabled.length; i++) {
            mEnabled[i] = true;
        }
    }

    // When preferences are disabled, we will display them grayed out. Users
    // will not be able to change the disabled preferences, but they can still
    // see
    // the current value of the preferences
    public void setPreferenceEnabled(String key, boolean enable) {
        int count = mEnabled == null ? 0 : mEnabled.length;
        for (int j = 0; j < count; j++) {
            ListPreference pref = mListItem.get(j);
            if (pref != null && key.equals(pref.getKey())) {
                mEnabled[j] = enable;
                break;
            }
        }
    }

    public void onSettingChanged(ListPreference pref) {
        if (mListener != null) {
            mListener.onSettingChanged(pref);
        }
    }

    // Scene mode can override other camera settings (ex: flash mode).
    public void overrideSettings(final String... keyvalues) {
        int count = mEnabled == null ? 0 : mEnabled.length;
        for (int i = 0; i < keyvalues.length; i += 2) {
            String key = keyvalues[i];
            String value = keyvalues[i + 1];
            for (int j = 0; j < count; j++) {
                ListPreference pref = mListItem.get(j);
                if (pref != null && key.equals(pref.getKey())) {
                    // Change preference
                    if (value != null)
                        pref.setValue(value);
                    // If the preference is overridden, disable the preference
                    boolean enable = value == null;
                    mEnabled[j] = enable;
                    int offset = getFirstVisiblePosition();
                    if (offset >= 0) {
                        int indexInView = j - offset;
                        if (getChildCount() > indexInView && indexInView >= 0) {
                            getChildAt(indexInView).setEnabled(enable);
                        }
                    }
                }
            }
        }
        reloadPreference();
    }

    public void resetHighlight() {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View v = getChildAt(i);
            v.setBackground(null);
        }
        mHighlighted = -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_MOVE) {
            mListener.onListMenuTouched();
            resetHighlight();
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
            long id) {
        if (mListener != null) {
            resetHighlight();
            ListPreference pref = mListItem.get(position);
            mHighlighted = position;
            view.setBackgroundColor(getContext().getResources().getColor(R.color.setting_color));
            mListener.onPreferenceClicked(pref, (int) view.getY());
        }

    }

    public void reloadPreference() {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            ListPreference pref = mListItem.get(i);
            if (pref != null) {
                ListMenuItem listMenuItem =
                        (ListMenuItem) getChildAt(i);
                listMenuItem.reloadPreference();
            }
        }
    }
}
