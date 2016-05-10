/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
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

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Log;
import android.util.Size;

import com.android.camera.ui.ListMenu;

import org.codeaurora.snapcam.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SettingsManager implements ListMenu.SettingsListener {
    public static final int RESOURCE_TYPE_THUMBNAIL = 0;
    public static final int RESOURCE_TYPE_LARGEICON = 1;
    public static final String KEY_CAMERA_SAVEPATH = "pref_camera2_savepath_key";
    public static final String KEY_RECORD_LOCATION = "pref_camera2_recordlocation_key";
    public static final String KEY_JPEG_QUALITY = "pref_camera2_jpegquality_key";
    public static final String KEY_FOCUS_MODE = "pref_camera2_focusmode_key";
    public static final String KEY_FLASH_MODE = "pref_camera2_flashmode_key";
    public static final String KEY_WHITE_BALANCE = "pref_camera2_whitebalance_key";
    public static final String KEY_CAMERA2 = "pref_camera2_camera2_key";
    public static final String KEY_DUAL_CAMERA = "pref_camera2_dual_camera_key";
    public static final String KEY_MONO_PREVIEW = "pref_camera2_mono_preview_key";
    public static final String KEY_CLEARSIGHT = "pref_camera2_clearsight_key";
    public static final String KEY_FILTER_MODE = "pref_camera2_filter_mode_key";
    public static final String KEY_COLOR_EFFECT = "pref_camera2_coloreffect_key";
    public static final String KEY_SCENE_MODE = "pref_camera2_scenemode_key";
    public static final String KEY_REDEYE_REDUCTION = "pref_camera2_redeyereduction_key";
    public static final String KEY_CAMERA_ID = "pref_camera2_id_key";
    public static final String KEY_PICTURE_SIZE = "pref_camera2_picturesize_key";
    public static final String KEY_ISO = "pref_camera2_iso_key";
    public static final String KEY_EXPOSURE = "pref_camera2_exposure_key";
    public static final String KEY_TIMER = "pref_camera2_timer_key";
    public static final String KEY_LONGSHOT = "pref_camera2_longshot_key";
    private static final String TAG = "SnapCam_SettingsManager";
    private static final List<CameraCharacteristics> mCharacteristics = new ArrayList<>();
    private static final int NOT_FOUND = -1;

    private static SettingsManager sInstance;

    private ArrayList<Listener> mListeners;
    private Map<String, Values> mValuesMap;
    private Context mContext;
    private PreferenceGroup mPreferenceGroup;
    private ComboPreferences mPreferences;
    private Map<String, List<String>> mDependendsOnMap;

    private SettingsManager(Context context) {
        mListeners = new ArrayList<>();
        mValuesMap = new HashMap<>();
        mDependendsOnMap = new HashMap<>();
        mContext = context;
        mPreferences = new ComboPreferences(mContext);
        CameraSettings.upgradeGlobalPreferences(mPreferences.getGlobal(), mContext);

        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIdList = manager.getCameraIdList();
            for (int i = 0; i < cameraIdList.length; i++) {
                //TODO: determine front/back/bayer/mono and their ids
                String cameraId = cameraIdList[i];
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);
                mCharacteristics.add(i, characteristics);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public static SettingsManager createInstance(Context context) {
        if (sInstance == null) {
            sInstance = new SettingsManager(context);
        }
        return sInstance;
    }

    public static SettingsManager getInstance() {
        return sInstance;
    }

    public List<String> getDisabledList() {
        List<String> list = new ArrayList<>();
        Set<String> keySet = mValuesMap.keySet();
        for (String key : keySet) {
            Values value = mValuesMap.get(key);
            if (value.overriddenValue != null) {
                list.add(key);
            }
        }
        return list;
    }

    @Override
    public void onSettingChanged(ListPreference pref) {
        String key = pref.getKey();
        List changed = checkDependencyAndUpdate(key);
        notifyListeners(changed);
    }

    public void init() {
        Log.d(TAG, "SettingsManager init");
        int cameraId = CameraSettings.getInitialCameraId(mPreferences);
        setLocalIdAndInitialize(cameraId);
    }

    public void reinit(int cameraId) {
        Log.d(TAG, "SettingsManager reinit " + cameraId);
        setLocalIdAndInitialize(cameraId);
    }

    private void setLocalIdAndInitialize(int cameraId) {
        mPreferences.setLocalId(mContext, cameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());

        PreferenceInflater inflater = new PreferenceInflater(mContext);
        mPreferenceGroup =
                (PreferenceGroup) inflater.inflate(R.xml.capture_preferences);

        filterPreferences(cameraId);
        initDepedencyTable();
        initializeValueMap();
    }

    private void initDepedencyTable() {
        for (int i = 0; i < mPreferenceGroup.size(); i++) {
            ListPreference pref = (ListPreference) mPreferenceGroup.get(i);
            String baseKey = pref.getKey();
            CharSequence[] dependencyList = pref.getDependencyList();
            if (dependencyList != null) {
                for (int j = 0; j < dependencyList.length; j++) {
                    String key = dependencyList[j].toString();
                    pref = mPreferenceGroup.findPreference(key);
                    if (pref == null) continue; //filtered?
                    List list = mDependendsOnMap.get(key);
                    if (list == null) {
                        list = new ArrayList<String>();
                    }
                    list.add(baseKey);
                    mDependendsOnMap.put(key, list);
                }
            }
        }
    }

    private void initializeValueMap() {
        List<String> processLater = new ArrayList<String>();
        for (int i = 0; i < mPreferenceGroup.size(); i++) {
            ListPreference pref = (ListPreference) mPreferenceGroup.get(i);
            String key = pref.getKey();
            if (mDependendsOnMap.get(key) != null) {
                processLater.add(key);
                continue;
            }
            Values values = new Values(pref.getValue(), null);
            mValuesMap.put(pref.getKey(), values);
        }

        for (String keyToProcess : processLater) {
            List<String> dependsOnList = mDependendsOnMap.get(keyToProcess);
            boolean active = true;
            for (String s : dependsOnList) {
                if (isOptionOn(s)) active = false;
                break;
            }
            ListPreference pref = mPreferenceGroup.findPreference(keyToProcess);
            Values values = new Values(pref.getValue(), null);
            if (!active) {
                values.overriddenValue = pref.getOffValue();
            }
            mValuesMap.put(keyToProcess, values);
        }
    }

    private List<SettingState> checkDependencyAndUpdate(String changedPrefKey) {
        ListPreference changedPref = mPreferenceGroup.findPreference(changedPrefKey);
        if (changedPref == null) return null;

        String key = changedPref.getKey();
        String value = changedPref.getValue();
        boolean turnedOff = value.equals(changedPref.getOffValue());
        List<SettingState> changed = new ArrayList();
        Values values = new Values(value, null);
        mValuesMap.put(key, values);
        changed.add(new SettingState(key, values));
        CharSequence[] dependencyList = changedPref.getDependencyList();

        if (!turnedOff) { // turned on
            if (dependencyList != null) {
                for (int j = 0; j < dependencyList.length; j++) {
                    key = dependencyList[j].toString();
                    ListPreference pref = mPreferenceGroup.findPreference(key);
                    if (pref == null) continue;
                    values = mValuesMap.get(key);
                    if (values != null && values.overriddenValue != null) continue;
                    Values newValue = new Values(pref.getValue(), pref.getOffValue());
                    mValuesMap.put(key, newValue);
                    changed.add(new SettingState(key, newValue));
                }
            }
        } else {
            if (dependencyList != null) {
                for (int j = 0; j < dependencyList.length; j++) {
                    key = dependencyList[j].toString();
                    List<String> dependsOnList = mDependendsOnMap.get(key);
                    if (dependsOnList == null) continue;
                    boolean active = true;
                    for (String s : dependsOnList) {
                        if (isOptionOn(s)) active = false;
                        break;
                    }
                    if (active) {
                        values = mValuesMap.get(key);
                        values.overriddenValue = null;
                        mValuesMap.put(key, values);
                        changed.add(new SettingState(key, values));
                    }
                }
            }
        }
        return changed;
    }

    public void registerListener(Listener listener) {
        mListeners.add(listener);
    }

    private void notifyListeners(List<SettingState> changes) {
        for (Listener listener : mListeners) {
            listener.onSettingsChanged(changes);
        }
    }

    public boolean isCamera2On() {
        return mPreferences.getString(KEY_CAMERA2, "disable").equals("enable");
    }

    public String getValue(String key) {
        Values values = mValuesMap.get(key);
        if (values == null) return null;
        if (values.overriddenValue == null) return values.value;
        else return values.overriddenValue;
    }

    public int getValueIndex(String key) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        String value = getValue(key);
        if (value == null) return -1;
        return pref.findIndexOfValue(value);
    }

    public boolean isOverriden(String key) {
        Values values = mValuesMap.get(key);
        return values.overriddenValue != null;
    }

    public void setValue(String key, String value) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        pref.setValue(value);
        updateMapAndNotify(pref);
    }

    public void setValueIndex(String key, int index) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        pref.setValueIndex(index);
        updateMapAndNotify(pref);
    }

    private void updateMapAndNotify(ListPreference pref) {
        String key = pref.getKey();
        Values values = mValuesMap.get(key);
        values.overriddenValue = null;
        values.value = pref.getValue();
        mValuesMap.put(key, values);
        List changed = checkDependencyAndUpdate(key);
        notifyListeners(changed);
    }

    private boolean isOptionOn(String key) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        Values values = mValuesMap.get(key);
        return (values.overriddenValue == null && !pref.getValue().equals(pref.getOffValue()));
    }

    public PreferenceGroup getPreferenceGroup() {
        return mPreferenceGroup;
    }

    public CharSequence[] getEntries(String key) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        return pref.getEntries();
    }

    public int[] getResource(String key, int type) {
        IconListPreference pref = (IconListPreference) mPreferenceGroup.findPreference(key);
        switch (type) {
            case RESOURCE_TYPE_THUMBNAIL:
                return pref.getThumbnailIds();
            case RESOURCE_TYPE_LARGEICON:
                return pref.getLargeIconIds();
        }
        return null;
    }

    private void filterPreferences(int cameraId) {
        // filter unsupported preferences
        ListPreference whiteBalance = mPreferenceGroup.findPreference(KEY_WHITE_BALANCE);
        ListPreference flashMode = mPreferenceGroup.findPreference(KEY_FLASH_MODE);
        ListPreference colorEffect = mPreferenceGroup.findPreference(KEY_COLOR_EFFECT);
        ListPreference sceneMode = mPreferenceGroup.findPreference(KEY_SCENE_MODE);
        ListPreference cameraIdPref = mPreferenceGroup.findPreference(KEY_CAMERA_ID);
        ListPreference pictureSize = mPreferenceGroup.findPreference(KEY_PICTURE_SIZE);

        if (whiteBalance != null) {
            CameraSettings.filterUnsupportedOptions(mPreferenceGroup,
                    whiteBalance, getSupportedWhiteBalanceModes(cameraId));
        }
        if (flashMode != null) {
            CameraSettings.filterUnsupportedOptions(mPreferenceGroup,
                    flashMode, getSupportedFlashModes(cameraId));
        }

        if (colorEffect != null) {
            CameraSettings.filterUnsupportedOptions(mPreferenceGroup,
                    colorEffect, getSupportedColorEffects(cameraId));
        }

        if (sceneMode != null) {
            CameraSettings.filterUnsupportedOptions(mPreferenceGroup,
                    sceneMode, getSupportedSceneModes(cameraId));
        }

        if (cameraIdPref != null) buildCameraId();

        if (pictureSize != null) {
            CameraSettings.filterUnsupportedOptions(mPreferenceGroup,
                    pictureSize, getSupportedPictureSize(cameraId));
        }
    }

    private void buildCameraId() {
        int numOfCameras = mCharacteristics.size();
        if (numOfCameras < 2) {
            removePreference(mPreferenceGroup, KEY_CAMERA_ID);
        }

        CharSequence[] entryValues = new CharSequence[numOfCameras];
        //TODO: Modify this after bayer/mono/front/back determination is done
        entryValues[0] = "" + CaptureModule.BAYER_ID;
        entryValues[1] = "" + CaptureModule.FRONT_ID;
        ListPreference cameraIdPref = mPreferenceGroup.findPreference(KEY_CAMERA_ID);
        cameraIdPref.setEntryValues(entryValues);
    }

    private boolean removePreference(PreferenceGroup group, String key) {
        for (int i = 0, n = group.size(); i < n; i++) {
            CameraPreference child = group.get(i);
            if (child instanceof PreferenceGroup) {
                if (removePreference((PreferenceGroup) child, key)) {
                    return true;
                }
            }
            if (child instanceof ListPreference &&
                    ((ListPreference) child).getKey().equals(key)) {
                group.removePreference(i);
                return true;
            }
        }
        return false;
    }

    public float getMaxZoom(int id) {
        return mCharacteristics.get(id).get(CameraCharacteristics
                .SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
    }

    public Rect getSensorActiveArraySize(int id) {
        return mCharacteristics.get(id).get(CameraCharacteristics
                .SENSOR_INFO_ACTIVE_ARRAY_SIZE);
    }

    public float getMaxZoom(List<Integer> ids) {
        float zoomMax = Float.MAX_VALUE;
        for (int id : ids) {
            zoomMax = Math.min(getMaxZoom(id), zoomMax);
        }
        return zoomMax;
    }

    public boolean isZoomSupported(int id) {
        return mCharacteristics.get(id).get(CameraCharacteristics
                .SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) > 1f;
    }

    public boolean isAutoFocusSupported(List<Integer> ids) {
        for (int id : ids) {
            if (!isAutoFocusSupported(id))
                return false;
        }
        return true;
    }


    public boolean isZoomSupported(List<Integer> ids) {
        for (int id : ids) {
            if (!isZoomSupported(id))
                return false;
        }
        return true;
    }

    public boolean isAutoFocusSupported(int id) {
        Integer maxAfRegions = mCharacteristics.get(id).get(
                CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
        return maxAfRegions != null && maxAfRegions > 0;
    }

    public boolean isFlashSupported(int id) {
        return mCharacteristics.get(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) &&
                mValuesMap.get(KEY_FLASH_MODE) != null;
    }

    private List<String> getSupportedPictureSize(int cameraId) {
        StreamConfigurationMap map = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        List<String> res = new ArrayList<>();
        for (int i = 0; i < sizes.length; i++) {
            res.add(sizes[i].toString());
        }
        return res;
    }

    private List<String> getSupportedWhiteBalanceModes(int cameraId) {
        int[] whiteBalanceModes = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .CONTROL_AWB_AVAILABLE_MODES);
        List<String> modes = new ArrayList<>();
        for (int mode : whiteBalanceModes) {
            modes.add("" + mode);
        }
        return modes;
    }

    private List<String> getSupportedSceneModes(int cameraId) {
        int[] sceneModes = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .CONTROL_AVAILABLE_SCENE_MODES);
        List<String> modes = new ArrayList<>();
        modes.add("0"); // need special case handle for auto scene mode
        for (int mode : sceneModes) {
            modes.add("" + mode);
        }
        return modes;
    }

    private List<String> getSupportedFlashModes(int cameraId) {
        int[] flashModes = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .CONTROL_AE_AVAILABLE_MODES);
        List<String> modes = new ArrayList<>();
        for (int mode : flashModes) {
            modes.add("" + mode);
        }
        return modes;
    }

    public List<String> getSupportedColorEffects(int cameraId) {
        int[] flashModes = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .CONTROL_AVAILABLE_EFFECTS);
        List<String> modes = new ArrayList<>();
        for (int mode : flashModes) {
            modes.add("" + mode);
        }
        return modes;
    }

    public interface Listener {
        void onSettingsChanged(List<SettingState> settings);
    }

    static class Values {
        String value;
        String overriddenValue;

        Values(String value, String overriddenValue) {
            this.value = value;
            this.overriddenValue = overriddenValue;
        }
    }

    static class SettingState {
        String key;
        Values values;

        SettingState(String key, Values values) {
            this.key = key;
            this.values = values;
        }
    }

}
