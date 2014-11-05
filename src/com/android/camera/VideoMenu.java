/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.camera;

import android.content.Context;
import android.view.LayoutInflater;

import com.android.camera.ui.AbstractSettingPopup;
import com.android.camera.ui.ListPrefSettingPopup;
import com.android.camera.ui.MoreSettingPopup;
import com.android.camera.ui.PieItem;
import com.android.camera.ui.PieItem.OnClickListener;
import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.TimeIntervalPopup;
import org.codeaurora.snapcam.R;

public class VideoMenu extends PieController
        implements MoreSettingPopup.Listener,
        ListPrefSettingPopup.Listener,
        TimeIntervalPopup.Listener {

    private static String TAG = "CAM_VideoMenu";

    private VideoUI mUI;
    private String[] mOtherKeys1;
    private String[] mOtherKeys2;

    private AbstractSettingPopup mPopup1;
    private AbstractSettingPopup mPopup2;

    private static final int POPUP_NONE = 0;
    private static final int POPUP_FIRST_LEVEL = 1;
    private static final int POPUP_SECOND_LEVEL = 2;
    private int mPopupStatus;
    private int popupNum;
    private CameraActivity mActivity;
    private String mPrevSavedVideoCDS;
    private boolean mIsVideoTNREnabled = false;
    private boolean mIsVideoCDSUpdated = false;

    public VideoMenu(CameraActivity activity, VideoUI ui, PieRenderer pie) {
        super(activity, pie);
        mUI = ui;
        mActivity = activity;
    }

    public void initialize(PreferenceGroup group) {
        super.initialize(group);
        mPopup1 = null;
        mPopup2 = null;
        popupNum = 0;
        mPopupStatus = POPUP_NONE;
        PieItem item = null;
        // settings popup
        mOtherKeys1 = new String[] {
                CameraSettings.KEY_DIS,
                CameraSettings.KEY_VIDEO_EFFECT,
                CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL,
                CameraSettings.KEY_VIDEO_QUALITY,
                CameraSettings.KEY_VIDEO_ENCODER,
                CameraSettings.KEY_AUDIO_ENCODER,
                CameraSettings.KEY_VIDEO_DURATION,
                CameraSettings.KEY_RECORD_LOCATION,
                CameraSettings.KEY_CAMERA_SAVEPATH
        };

       //settings popup
       mOtherKeys2 = new String[] {
                CameraSettings.KEY_COLOR_EFFECT,
                CameraSettings.KEY_VIDEO_HDR,
                CameraSettings.KEY_POWER_MODE,
                CameraSettings.KEY_WHITE_BALANCE,
                CameraSettings.KEY_VIDEO_HIGH_FRAME_RATE,
                CameraSettings.KEY_SEE_MORE,
                CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE,
                CameraSettings.KEY_VIDEO_ROTATION,
                CameraSettings.KEY_VIDEO_CDS_MODE,
                CameraSettings.KEY_VIDEO_TNR_MODE
       };

        PieItem item1 = makeItem(R.drawable.ic_settings_holo_light_01);
        item1.setLabel(mActivity.getResources().getString(R.string.camera_menu_more_label));
        item1.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(PieItem item) {
                if (mPopup1 == null || mPopupStatus != POPUP_FIRST_LEVEL) {
                    initializePopup();
                    mPopupStatus = POPUP_FIRST_LEVEL;
                }
                mUI.showPopup(mPopup1);
                popupNum = 1;
            }
        });
        mRenderer.addItem(item1);

        PieItem item2 = makeItem(R.drawable.ic_settings_holo_light_02);
        item2.setLabel(mActivity.getResources().getString(R.string.camera_menu_more_label));
        item2.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(PieItem item) {
                if (mPopup2 == null || mPopupStatus != POPUP_FIRST_LEVEL) {
                    initializePopup();
                    mPopupStatus = POPUP_FIRST_LEVEL;
                }
                mUI.showPopup(mPopup2);
                popupNum = 2;
            }
        });
        mRenderer.addItem(item2);

        // camera switcher
        if (group.findPreference(CameraSettings.KEY_CAMERA_ID) != null) {
            item = makeItem(R.drawable.ic_switch_back);
            IconListPreference lpref = (IconListPreference) group.findPreference(
                    CameraSettings.KEY_CAMERA_ID);
            item.setLabel(lpref.getLabel());
            item.setImageResource(mActivity,
                    ((IconListPreference) lpref).getIconIds()
                    [lpref.findIndexOfValue(lpref.getValue())]);

            final PieItem fitem = item;
            item.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(PieItem item) {
                    // Find the index of next camera.
                    ListPreference pref =
                            mPreferenceGroup.findPreference(CameraSettings.KEY_CAMERA_ID);
                    if (pref != null) {
                        int index = pref.findIndexOfValue(pref.getValue());
                        CharSequence[] values = pref.getEntryValues();
                        index = (index + 1) % values.length;
                        int newCameraId = Integer.parseInt((String) values[index]);
                        fitem.setImageResource(mActivity,
                                ((IconListPreference) pref).getIconIds()[index]);
                        fitem.setLabel(pref.getLabel());
                        mListener.onCameraPickerClicked(newCameraId);
                    }
                }
            });
            mRenderer.addItem(item);
        }
    }

    @Override
    public void reloadPreferences() {
        super.reloadPreferences();
        if (mPopup1 != null) {
            mPopup1.reloadPreference();
        }
        if (mPopup2 != null) {
            mPopup2.reloadPreference();
        }
    }

    public void setPreference(String key, String value) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        if (pref != null && !value.equals(pref.getValue())) {
            pref.setValue(value);
            reloadPreferences();
        }
    }

    public void overrideCDSMode() {
        if (mPopup2 != null) {
            ListPreference pref_tnr = mPreferenceGroup.
                    findPreference(CameraSettings.KEY_VIDEO_TNR_MODE);
            ListPreference pref_cds = mPreferenceGroup.
                    findPreference(CameraSettings.KEY_VIDEO_CDS_MODE);
            String tnr = (pref_tnr != null) ? pref_tnr.getValue() : null;
            String cds = (pref_cds != null) ? pref_cds.getValue() : null;

            if (mPrevSavedVideoCDS == null && cds != null) {
                mPrevSavedVideoCDS = cds;
            }

            if ((tnr != null) && !mActivity.getString(R.string.
                    pref_camera_video_tnr_default).equals(tnr)) {
                ((MoreSettingPopup) mPopup2).setPreferenceEnabled(
                        CameraSettings.KEY_VIDEO_CDS_MODE,false);
                ((MoreSettingPopup) mPopup2).overrideSettings(
                        CameraSettings.KEY_VIDEO_CDS_MODE,
                        mActivity.getString(R.string.pref_camera_video_cds_value_off));
                mIsVideoTNREnabled = true;
                if (!mIsVideoCDSUpdated) {
                    if (cds != null) {
                        mPrevSavedVideoCDS = cds;
                    }
                    mIsVideoCDSUpdated = true;
                }
            } else if (tnr != null) {
                ((MoreSettingPopup) mPopup2).setPreferenceEnabled(
                        CameraSettings.KEY_VIDEO_CDS_MODE,true);
                if (mIsVideoTNREnabled) {
                    ((MoreSettingPopup) mPopup2).overrideSettings(
                            CameraSettings.KEY_VIDEO_CDS_MODE, mPrevSavedVideoCDS);
                    mIsVideoTNREnabled = false;
                    mIsVideoCDSUpdated = false;
                }
            }
        }
    }

    @Override
    public void overrideSettings(final String ... keyvalues) {
        overrideCDSMode();
        super.overrideSettings(keyvalues);
        if (((mPopup1 == null) && (mPopup2 == null)) || mPopupStatus != POPUP_FIRST_LEVEL) {
            mPopupStatus = POPUP_FIRST_LEVEL;
            initializePopup();
        }
        ((MoreSettingPopup) mPopup1).overrideSettings(keyvalues);
        ((MoreSettingPopup) mPopup2).overrideSettings(keyvalues);
    }

    @Override
    // Hit when an item in the second-level popup gets selected
    public void onListPrefChanged(ListPreference pref) {
        if (mPopup1 != null && mPopup2 != null) {
            if (mPopupStatus == POPUP_SECOND_LEVEL) {
                mUI.dismissPopup(true);
                mPopup1.reloadPreference();
                mPopup2.reloadPreference();
            }
        }
        super.onSettingChanged(pref);
    }

    protected void initializePopup() {
        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        MoreSettingPopup popup1 = (MoreSettingPopup) inflater.inflate(
                R.layout.more_setting_popup, null, false);
        popup1.setSettingChangedListener(this);
        popup1.initialize(mPreferenceGroup, mOtherKeys1);
        if (mActivity.isSecureCamera()) {
            // Prevent location preference from getting changed in secure camera mode
            popup1.setPreferenceEnabled(CameraSettings.KEY_RECORD_LOCATION, false);
        }
        mPopup1 = popup1;

        MoreSettingPopup popup2 = (MoreSettingPopup) inflater.inflate(
                R.layout.more_setting_popup, null, false);
        popup2.setSettingChangedListener(this);
        popup2.initialize(mPreferenceGroup, mOtherKeys2);
        mPopup2 = popup2;
        overrideCDSMode();
    }

    public void popupDismissed(boolean topPopupOnly) {
        // if the 2nd level popup gets dismissed
        if (mPopupStatus == POPUP_SECOND_LEVEL) {
            initializePopup();
            mPopupStatus = POPUP_FIRST_LEVEL;
            if (topPopupOnly) {
                if(popupNum == 1) mUI.showPopup(mPopup1);
                else if(popupNum == 2) mUI.showPopup(mPopup2);
            }
        } else {
            initializePopup();
        }
    }

    @Override
    // Hit when an item in the first-level popup gets selected, then bring up
    // the second-level popup
    public void onPreferenceClicked(ListPreference pref) {
        if (mPopupStatus != POPUP_FIRST_LEVEL) return;

        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        if (CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL.equals(pref.getKey())) {
            TimeIntervalPopup timeInterval = (TimeIntervalPopup) inflater.inflate(
                    R.layout.time_interval_popup, null, false);
            timeInterval.initialize((IconListPreference) pref);
            timeInterval.setSettingChangedListener(this);
            mUI.dismissPopup(true);
            mPopup1 = timeInterval;
        } else {
            ListPrefSettingPopup basic = (ListPrefSettingPopup) inflater.inflate(
                    R.layout.list_pref_setting_popup, null, false);
            basic.initialize(pref);
            basic.setSettingChangedListener(this);
            mUI.dismissPopup(true);
            mPopup1 = basic;
        }
        mUI.showPopup(mPopup1);
        mPopupStatus = POPUP_SECOND_LEVEL;
    }

}
