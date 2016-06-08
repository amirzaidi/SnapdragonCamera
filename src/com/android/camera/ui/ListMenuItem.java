/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2011 The Android Open Source Project
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
import android.view.accessibility.AccessibilityEvent;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ImageView;

import com.android.camera.ListPreference;
import com.android.camera.IconListPreference;
import org.codeaurora.snapcam.R;

/**
 * A one-line camera setting could be one of three types: knob, switch or
 * restore preference button. The setting includes a title for showing the
 * preference title which is initialized in the SimpleAdapter. A knob also
 * includes (ex: Picture size), a previous button, the current value (ex: 5MP),
 * and a next button. A switch, i.e. the preference RecordLocationPreference,
 * has only two values on and off which will be controlled in a switch button.
 * Other setting popup window includes several InLineSettingItem items with
 * different types if possible.
 */
public class ListMenuItem extends RelativeLayout {
    private static final String TAG = "ListMenuItem";
    private Listener mListener;
    protected ListPreference mPreference;
    protected int mIndex;
    // Scene mode can override the original preference value.
    protected String mOverrideValue;
    protected TextView mTitle;
    private TextView mEntry;
    private ImageView mIcon;

    static public interface Listener {
        public void onSettingChanged(ListPreference pref);
    }

    public ListMenuItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mEntry = (TextView) findViewById(R.id.current_setting);
        mIcon = (ImageView) findViewById(R.id.list_image);
    }

    protected void setTitle(ListPreference preference) {
        mTitle = ((TextView) findViewById(R.id.title));
        mTitle.setText(preference.getTitle());
    }

    protected void setIcon(ListPreference preference) {
        if (preference instanceof IconListPreference) {
            int resId = ((IconListPreference) preference).getSingleIcon();
            mIcon.setImageResource(resId);
        }

    }

    public void initialize(ListPreference preference) {
        setTitle(preference);
        if (preference == null)
            return;
        setIcon(preference);
        mPreference = preference;
        reloadPreference();
    }

    protected void updateView() {
        if (mOverrideValue == null) {
            mEntry.setText(mPreference.getEntry());
        } else {
            int index = mPreference.findIndexOfValue(mOverrideValue);
            if (index != -1) {
                mEntry.setText(mPreference.getEntries()[index]);
            } else {
                // Avoid the crash if camera driver has bugs.
                Log.e(TAG, "Fail to find override value=" + mOverrideValue);
                mPreference.print();
            }
        }
    }

    protected boolean changeIndex(int index) {
        if (index >= mPreference.getEntryValues().length || index < 0)
            return false;
        mIndex = index;
        mPreference.setValueIndex(mIndex);
        if (mListener != null) {
            mListener.onSettingChanged(mPreference);
        }
        updateView();
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
        return true;
    }

    // The value of the preference may have changed. Update the UI.
    public void reloadPreference() {
        mIndex = mPreference.findIndexOfValue(mPreference.getValue());
        updateView();
    }

    public void setSettingChangedListener(Listener listener) {
        mListener = listener;
    }

    public void overrideSettings(String value) {
        mOverrideValue = value;
        updateView();
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        event.getText().add(mPreference.getTitle() + mPreference.getEntry());
        return true;
    }

    @Override
    public void setEnabled(boolean enable) {
        super.setEnabled(enable);
        if (enable)
            setAlpha(1f);
        else
            setAlpha(0.3f);
        if (mTitle != null) {
            mTitle.setEnabled(enable);
            if (enable)
                setAlpha(1f);
            else
                setAlpha(0.3f);
        }
        if (mEntry != null) {
            mEntry.setEnabled(enable);
            if (enable)
                setAlpha(1f);
            else
                setAlpha(0.3f);
        }
    }

    public void setEnabled(boolean enable, String value) {
        super.setEnabled(enable);
        if (enable)
            setAlpha(1f);
        else
            setAlpha(0.3f);
        if (mTitle != null) {
            mTitle.setEnabled(enable);
            if (enable)
                setAlpha(1f);
            else
                setAlpha(0.3f);
        }
        if (mEntry != null) {
            mEntry.setEnabled(enable);
            if (enable)
                setAlpha(1f);
            else
                setAlpha(0.3f);
        }
    }
}
