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
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;

import com.android.camera.imageprocessor.filter.OptizoomFilter;
import com.android.camera.ui.ListMenu;
import com.android.camera.util.SettingTranslation;

import org.codeaurora.snapcam.R;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SettingsManager implements ListMenu.SettingsListener {
    public static final int RESOURCE_TYPE_THUMBNAIL = 0;
    public static final int RESOURCE_TYPE_LARGEICON = 1;
    public static final int SCENE_MODE_NIGHT_INT = 5;

    // Custom-Scenemodes start from 100
    public static final int SCENE_MODE_DUAL_INT = 100;
    public static final int SCENE_MODE_OPTIZOOM_INT = 101;
    public static final String SCENE_MODE_DUAL_STRING = "100";
    public static final String KEY_CAMERA_SAVEPATH = "pref_camera2_savepath_key";
    public static final String KEY_RECORD_LOCATION = "pref_camera2_recordlocation_key";
    public static final String KEY_JPEG_QUALITY = "pref_camera2_jpegquality_key";
    public static final String KEY_FOCUS_MODE = "pref_camera2_focusmode_key";
    public static final String KEY_FLASH_MODE = "pref_camera2_flashmode_key";
    public static final String KEY_WHITE_BALANCE = "pref_camera2_whitebalance_key";
    public static final String KEY_MAKEUP = "pref_camera2_makeup_key";
    public static final String KEY_CAMERA2 = "pref_camera2_camera2_key";
    public static final String KEY_MONO_ONLY = "pref_camera2_mono_only_key";
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
    public static final String KEY_VIDEO_DURATION = "pref_camera2_video_duration_key";
    public static final String KEY_VIDEO_QUALITY = "pref_camera2_video_quality_key";
    public static final String KEY_VIDEO_ENCODER = "pref_camera2_videoencoder_key";
    public static final String KEY_AUDIO_ENCODER = "pref_camera2_audioencoder_key";
    public static final String KEY_DIS = "pref_camera2_dis_key";
    public static final String KEY_NOISE_REDUCTION = "pref_camera2_noise_reduction_key";
    public static final String KEY_VIDEO_FLASH_MODE = "pref_camera2_video_flashmode_key";
    public static final String KEY_VIDEO_ROTATION = "pref_camera2_video_rotation_key";
    public static final String KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL =
            "pref_camera2_video_time_lapse_frame_interval_key";
    public static final String KEY_FACE_DETECTION = "pref_camera2_facedetection_key";
    public static final String KEY_AUTO_VIDEOSNAP_SIZE = "pref_camera2_videosnap_key";
    private static final String TAG = "SnapCam_SettingsManager";

    private static SettingsManager sInstance;
    private ArrayList<CameraCharacteristics> mCharacteristics;
    private ArrayList<Listener> mListeners;
    private Map<String, Values> mValuesMap;
    private Context mContext;
    private PreferenceGroup mPreferenceGroup;
    private ComboPreferences mPreferences;
    private Map<String, Set<String>> mDependendsOnMap;
    private boolean mIsMonoCameraPresent = false;
    private boolean mIsFrontCameraPresent = false;
    private JSONObject mDependency;

    private SettingsManager(Context context) {
        mListeners = new ArrayList<>();
        mCharacteristics = new ArrayList<>();
        mContext = context;
        mPreferences = ComboPreferences.get(mContext);
        if (mPreferences == null) {
            mPreferences = new ComboPreferences(mContext);
        }
        CameraSettings.upgradeGlobalPreferences(mPreferences.getGlobal(), mContext);

        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIdList = manager.getCameraIdList();
            for (int i = 0; i < cameraIdList.length; i++) {
                String cameraId = cameraIdList[i];
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);
                Byte monoOnly = 0;
                try {
                    monoOnly = characteristics.get(CaptureModule.MetaDataMonoOnlyKey);
                }catch(Exception e) {
                }
                if (monoOnly == 1) {
                    CaptureModule.MONO_ID = i;
                    mIsMonoCameraPresent = true;
                }
                int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    CaptureModule.FRONT_ID = i;
                    mIsFrontCameraPresent = true;
                }
                mCharacteristics.add(i, characteristics);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        mDependency = parseJson("dependency.json");
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

    public void destroyInstance() {
        if (sInstance != null) {
            sInstance = null;
        }
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
        if (changed == null) return;
        notifyListeners(changed);
    }

    public void init() {
        Log.d(TAG, "SettingsManager init");
        int cameraId = getInitialCameraId(mPreferences);
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
        mValuesMap = new HashMap<>();
        mDependendsOnMap = new HashMap<>();
        filterPreferences(cameraId);
        initDepedencyTable();
        initializeValueMap();
    }

    private void initDepedencyTable() {
        for (int i = 0; i < mPreferenceGroup.size(); i++) {
            ListPreference pref = (ListPreference) mPreferenceGroup.get(i);
            String baseKey = pref.getKey();
            String value = pref.getValue();

            JSONObject dependency = getDependencyList(baseKey, value);
            if (dependency != null) {
                Iterator<String> keys = dependency.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    pref = mPreferenceGroup.findPreference(key);
                    if (pref == null) continue; //filtered?
                    Set set = mDependendsOnMap.get(key);
                    if (set == null) {
                        set = new HashSet<>();
                    }
                    set.add(baseKey);
                    mDependendsOnMap.put(key, set);
                }
            }
        }
    }

    private void initializeValueMap() {
        List<String> processLater = new ArrayList<String>();
        for (int i = 0; i < mPreferenceGroup.size(); i++) {
            ListPreference pref = (ListPreference) mPreferenceGroup.get(i);
            String key = pref.getKey();
            Set<String> set = mDependendsOnMap.get(key);
            if (set != null && set.size() != 0) {
                processLater.add(key);
            }
            Values values = new Values(pref.getValue(), null);
            mValuesMap.put(pref.getKey(), values);
        }
        for (String keyToProcess : processLater) {
            Set<String> dependsOnSet = mDependendsOnMap.get(keyToProcess);
            String dependentKey = dependsOnSet.iterator().next();
            String value = getValue(dependentKey);
            JSONObject dependencyList = getDependencyList(dependentKey, value);

            String newValue = null;
            try {
                newValue = dependencyList.getString(keyToProcess);
            } catch (JSONException e) {
                e.printStackTrace();
                continue;
            }
            Values values = new Values(getValue(keyToProcess), newValue);
            mValuesMap.put(keyToProcess, values);
        }
    }

    private List<SettingState> checkDependencyAndUpdate(String changedPrefKey) {
        ListPreference changedPref = mPreferenceGroup.findPreference(changedPrefKey);
        if (changedPref == null) return null;

        String value = changedPref.getValue();
        String prevValue = getValue(changedPrefKey);
        if (value.equals(prevValue)) return null;

        List<SettingState> changed = new ArrayList();
        Values values = new Values(value, null);
        mValuesMap.put(changedPrefKey, values);
        changed.add(new SettingState(changedPrefKey, values));

        JSONObject map = getDependencyMapForKey(changedPrefKey);
        if (map == null || getDependencyKey(map, value).equals(getDependencyKey(map,prevValue)))
            return changed;

        Set<String> turnOn = new HashSet<>();
        Set<String> turnOff = new HashSet<>();

        JSONObject dependencyList = getDependencyList(changedPrefKey, value);
        JSONObject originalDependencyList = getDependencyList(changedPrefKey, prevValue);

        Iterator<String> it = originalDependencyList.keys();
        while (it.hasNext()) {
            turnOn.add(it.next());
        }
        it = dependencyList.keys();
        while (it.hasNext()) {
            turnOff.add(it.next());
        }
        it = originalDependencyList.keys();
        while (it.hasNext()) {
            turnOff.remove(it.next());
        }
        it = dependencyList.keys();
        while (it.hasNext()) {
            turnOn.remove(it.next());
        }

        for (String keyToTurnOn: turnOn) {
            Set<String> dependsOnSet = mDependendsOnMap.get(keyToTurnOn);
            if (dependsOnSet == null || dependsOnSet.size() == 0) continue;

                values = mValuesMap.get(keyToTurnOn);
                if (values == null) continue;
                values.overriddenValue = null;
                mValuesMap.put(keyToTurnOn, values);
                changed.add(new SettingState(keyToTurnOn, values));
        }

        for (String keyToTurnOff: turnOff) {
            ListPreference pref = mPreferenceGroup.findPreference(keyToTurnOff);
            if (pref == null) continue;
            values = mValuesMap.get(keyToTurnOff);
            if (values == null) continue;
            if (values != null && values.overriddenValue != null) continue;
            String newValue = null;
            try {
                newValue = dependencyList.getString(keyToTurnOff);
            } catch (JSONException e) {
                e.printStackTrace();
                continue;
            }
            if (newValue == null) continue;

            Values newValues = new Values(pref.getValue(), newValue);
            mValuesMap.put(keyToTurnOff, newValues);
            changed.add(new SettingState(keyToTurnOff, newValues));
        }
            updateBackDependency(changedPrefKey, turnOn, turnOff);
        return changed;
    }

    private void updateBackDependency(String key, Set<String> remove, Set<String> add) {
        for (CharSequence c : remove) {
            String currentKey = c.toString();
            Set<String> dependsOnSet = mDependendsOnMap.get(currentKey);
            if (dependsOnSet != null) dependsOnSet.remove(key);
        }
        for (CharSequence c : add) {
            String currentKey = c.toString();
            Set<String> dependsOnSet = mDependendsOnMap.get(currentKey);
            if (dependsOnSet == null) {
                dependsOnSet = new HashSet<>();
                mDependendsOnMap.put(currentKey, dependsOnSet);
            }
            dependsOnSet.add(key);
        }
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
        List changed = checkDependencyAndUpdate(key);
        if (changed == null) return;
        notifyListeners(changed);
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

    public int getInitialCameraId(SharedPreferences pref) {
        String value = pref.getString(SettingsManager.KEY_CAMERA_ID, "0");
        int frontBackId = Integer.parseInt(value);
        if (frontBackId == CaptureModule.FRONT_ID) return frontBackId;
        String monoOnly = pref.getString(SettingsManager.KEY_MONO_ONLY, "off");
        if (monoOnly.equals("off")) return frontBackId;
        else return CaptureModule.MONO_ID;
    }

    private void filterPreferences(int cameraId) {
        // filter unsupported preferences
        ListPreference whiteBalance = mPreferenceGroup.findPreference(KEY_WHITE_BALANCE);
        ListPreference flashMode = mPreferenceGroup.findPreference(KEY_FLASH_MODE);
        ListPreference colorEffect = mPreferenceGroup.findPreference(KEY_COLOR_EFFECT);
        ListPreference sceneMode = mPreferenceGroup.findPreference(KEY_SCENE_MODE);
        ListPreference cameraIdPref = mPreferenceGroup.findPreference(KEY_CAMERA_ID);
        ListPreference pictureSize = mPreferenceGroup.findPreference(KEY_PICTURE_SIZE);
        ListPreference exposure = mPreferenceGroup.findPreference(KEY_EXPOSURE);
        ListPreference iso = mPreferenceGroup.findPreference(KEY_ISO);
        ListPreference clearsight = mPreferenceGroup.findPreference(KEY_CLEARSIGHT);
        ListPreference monoPreview = mPreferenceGroup.findPreference(KEY_MONO_PREVIEW);
        ListPreference monoOnly = mPreferenceGroup.findPreference(KEY_MONO_ONLY);
        ListPreference redeyeReduction = mPreferenceGroup.findPreference(KEY_REDEYE_REDUCTION);
        ListPreference videoQuality = mPreferenceGroup.findPreference(KEY_VIDEO_QUALITY);
        ListPreference videoEncoder = mPreferenceGroup.findPreference(KEY_VIDEO_ENCODER);
        ListPreference audioEncoder = mPreferenceGroup.findPreference(KEY_AUDIO_ENCODER);
        ListPreference noiseReduction = mPreferenceGroup.findPreference(KEY_NOISE_REDUCTION);
        ListPreference videoFlash = mPreferenceGroup.findPreference(KEY_VIDEO_FLASH_MODE);
        ListPreference faceDetection = mPreferenceGroup.findPreference(KEY_FACE_DETECTION);

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
            CameraSettings.filterSimilarPictureSize(mPreferenceGroup, pictureSize);
        }

        if (exposure != null) buildExposureCompensation(cameraId);

        if (iso != null) {
            CameraSettings.filterUnsupportedOptions(mPreferenceGroup,
                    iso, getSupportedIso(cameraId));
        }

        if (iso != null) {
            CameraSettings.filterUnsupportedOptions(mPreferenceGroup,
                    videoQuality, getSupportedVideoSize(cameraId));
        }

        if (!mIsMonoCameraPresent) {
            if (clearsight != null) removePreference(mPreferenceGroup, KEY_CLEARSIGHT);
            if (monoPreview != null) removePreference(mPreferenceGroup, KEY_MONO_PREVIEW);
            if (monoOnly != null) removePreference(mPreferenceGroup, KEY_MONO_ONLY);
        }

        if (redeyeReduction != null) {
            CameraSettings.filterUnsupportedOptions(mPreferenceGroup,
                    redeyeReduction, getSupportedRedeyeReduction(cameraId));
        }

        if (videoEncoder != null) {
            CameraSettings.filterUnsupportedOptions(mPreferenceGroup, videoEncoder,
                    getSupportedVideoEncoders(videoEncoder.getEntryValues()));
        }

        if (audioEncoder != null) {
            CameraSettings.filterUnsupportedOptions(mPreferenceGroup, audioEncoder,
                    getSupportedAudioEncoders(audioEncoder.getEntryValues()));
        }

        if (noiseReduction != null) {
            CameraSettings.filterUnsupportedOptions(mPreferenceGroup, noiseReduction,
                    getSupportedNoiseReductionModes(cameraId));
        }

        if (videoFlash != null) {
            if (!isFlashAvailable(cameraId))
                removePreference(mPreferenceGroup, KEY_VIDEO_FLASH_MODE);
        }

        if (faceDetection != null) {
            if (!isFaceDetectionSupported(cameraId))
                removePreference(mPreferenceGroup, KEY_FACE_DETECTION);
        }
    }

    private void buildExposureCompensation(int cameraId) {
        Range<Integer> range = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .CONTROL_AE_COMPENSATION_RANGE);
        int max = range.getUpper();
        int min = range.getLower();
        if (min == 0 && max == 0) {
            removePreference(mPreferenceGroup, KEY_EXPOSURE);
            return;
        }
        ListPreference pref = mPreferenceGroup.findPreference(KEY_EXPOSURE);
        Rational rational = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .CONTROL_AE_COMPENSATION_STEP);
        double step = rational.doubleValue();
        int increment = 1;
        while ((max - min) / increment > 10) {
            increment++;
        }
        int start = min;
        if (start < 0) {
            while (Math.abs(start) % increment != 0) {
                start++;
            }
        }
        int size = 0;
        for (int i = start; i <= max; i += increment) size++;
        CharSequence entries[] = new CharSequence[size];
        CharSequence entryValues[] = new CharSequence[size];
        int count = 0;
        for (int i = start; i <= max; i += increment, count++) {
            entryValues[count] = Integer.toString(i);
            StringBuilder builder = new StringBuilder();
            if (i > 0) builder.append('+');
            DecimalFormat format = new DecimalFormat("#.##");
            entries[count] = builder.append(format.format(i * step)).toString();
        }
        pref.setEntries(entries);
        pref.setEntryValues(entryValues);
    }

    private void buildCameraId() {
        int numOfCameras = mCharacteristics.size();
        if (!mIsFrontCameraPresent) {
            removePreference(mPreferenceGroup, KEY_CAMERA_ID);
            return;
        }

        CharSequence[] entryValues = new CharSequence[numOfCameras];
        CharSequence[] entries = new CharSequence[numOfCameras];
        //TODO: Modify this after bayer/mono/front/back determination is done
        entryValues[0] = "" + CaptureModule.BAYER_ID;
        entries[0] = "BACK";
        if (mIsFrontCameraPresent) {
            entryValues[1] = "" + CaptureModule.FRONT_ID;
            entries[1] = "FRONT";
        }
        ListPreference cameraIdPref = mPreferenceGroup.findPreference(KEY_CAMERA_ID);
        cameraIdPref.setEntryValues(entryValues);
        cameraIdPref.setEntries(entries);
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

    public boolean isAutoFocusRegionSupported(List<Integer> ids) {
        for (int id : ids) {
            if (!isAutoFocusRegionSupported(id))
                return false;
        }
        return true;
    }

    public boolean isAutoExposureRegionSupported(List<Integer> ids) {
        for (int id : ids) {
            if (!isAutoExposureRegionSupported(id))
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

    public boolean isAutoExposureRegionSupported(int id) {
        Integer maxAERegions = mCharacteristics.get(id).get(
                CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
        return maxAERegions != null && maxAERegions > 0;
    }

    public boolean isAutoFocusRegionSupported(int id) {
        Integer maxAfRegions = mCharacteristics.get(id).get(
                CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
        return maxAfRegions != null && maxAfRegions > 0;
    }

    public boolean isFixedFocus(int id) {
        Float focusDistance = mCharacteristics.get(id).get(CameraCharacteristics
                .LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        if (focusDistance == null || focusDistance == 0) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isFaceDetectionSupported(int id) {
        int[] faceDetection = mCharacteristics.get(id).get
                (CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
        for (int value: faceDetection) {
            if (value == CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE)
                return true;
        }
        return false;
    }

    public boolean isFacingFront(int id) {
        int facing = mCharacteristics.get(id).get(CameraCharacteristics.LENS_FACING);
        return facing == CameraCharacteristics.LENS_FACING_FRONT;
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
        if (sizes != null) {
            for (int i = 0; i < sizes.length; i++) {
                res.add(sizes[i].toString());
            }
        }

        Size[] highResSizes = map.getHighResolutionOutputSizes(ImageFormat.JPEG);
        if (highResSizes != null) {
            for (int i = 0; i < highResSizes.length; i++) {
                res.add(highResSizes[i].toString());
            }
        }

        return res;
    }

    public Size[] getSupportedOutputSize(int cameraId, int format) {
        StreamConfigurationMap map = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        return map.getOutputSizes(format);
    }

    public Size[] getSupportedOutputSize(int cameraId, Class cl) {
        StreamConfigurationMap map = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        return map.getOutputSizes(cl);
    }

    private List<String> getSupportedVideoSize(int cameraId) {
        StreamConfigurationMap map = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map.getOutputSizes(MediaRecorder.class);
        List<String> res = new ArrayList<>();
        for (int i = 0; i < sizes.length; i++) {
            res.add(sizes[i].toString());
        }
        return res;
    }

    private List<String> getSupportedRedeyeReduction(int cameraId) {
        int[] flashModes = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .CONTROL_AE_AVAILABLE_MODES);
        List<String> modes = new ArrayList<>();
        for (int i = 0; i < flashModes.length; i++) {
            if (flashModes[i] == CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE) {
                modes.add("disable");
                modes.add("enable");
                break;
            }
        }
        return modes;
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
        if (mIsMonoCameraPresent) modes.add(SCENE_MODE_DUAL_STRING); // need special case handle for dual mode
        if (OptizoomFilter.isSupportedStatic()) modes.add(SCENE_MODE_OPTIZOOM_INT + ""); // need special case handle for dual mode
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

    private boolean isFlashAvailable(int cameraId) {
        return mCharacteristics.get(cameraId).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
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

    private List<String> getSupportedIso(int cameraId) {
        Range<Integer> range = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .SENSOR_INFO_SENSITIVITY_RANGE);
        int max = range.getUpper();
        int value = 50;
        List<String> supportedIso = new ArrayList<>();
        supportedIso.add("auto");
        while (value <= max) {
            if (range.contains(value)) {
                supportedIso.add("" + value);
            }
            value += 50;
        }
        return supportedIso;
    }

    private static List<String> getSupportedVideoEncoders(CharSequence[] strings) {
        ArrayList<String> supported = new ArrayList<>();
        for (CharSequence cs: strings) {
            String s = cs.toString();
            int value = SettingTranslation.getVideoEncoder(s.toString());
            if (value != SettingTranslation.NOT_FOUND) supported.add(s.toString());
        }
        return supported;
    }

    private static List<String> getSupportedAudioEncoders(CharSequence[] strings) {
        ArrayList<String> supported = new ArrayList<>();
        for (CharSequence cs: strings) {
            String s = cs.toString();
            int value = SettingTranslation.getAudioEncoder(s);
            if (value != SettingTranslation.NOT_FOUND) supported.add(s);
        }
        return supported;
    }

    public List<String> getSupportedNoiseReductionModes(int cameraId) {
        int[] noiseReduction = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES);
        List<String> modes = new ArrayList<>();
        for (int mode : noiseReduction) {
            String str = SettingTranslation.getNoiseReduction(mode);
            if (str != null) modes.add(str);
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

    private JSONObject parseJson(String fileName) {
        String json;
        try {
            InputStream is = mContext.getAssets().open(fileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
            return new JSONObject(json);
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private JSONObject getDependencyMapForKey(String key) {
        if (mDependency == null) return null;
        try {
            return mDependency.getJSONObject(key);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private JSONObject getDependencyList(String key, String value) {
        JSONObject dependencyMap = getDependencyMapForKey(key);
        if (dependencyMap == null) return null;
        if (!dependencyMap.has(value)) value = "default";
        value = getDependencyKey(dependencyMap, value);
        try {
            return dependencyMap.getJSONObject(value);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String getDependencyKey(JSONObject dependencyMap, String value) {
        if (!dependencyMap.has(value)) value = "default";
        return value;
    }
}
