/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
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

import java.util.Locale;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.hardware.Camera.Parameters;
import android.graphics.Rect;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewPropertyAnimator;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;

import com.android.camera.CameraPreference.OnPreferenceChangedListener;
import com.android.camera.ui.CameraControls;
import com.android.camera.ui.CountdownTimerPopup;
import com.android.camera.ui.ListSubMenu;
import com.android.camera.ui.PieItem;
import com.android.camera.ui.ListMenu;
import com.android.camera.ui.RotateImageView;
import org.codeaurora.snapcam.R;
import android.widget.HorizontalScrollView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.Display;
import com.android.camera.util.CameraUtil;

public class CustomPhotoMenu extends MenuController
        implements ListMenu.Listener,
        CountdownTimerPopup.Listener,
        ListSubMenu.Listener {
    private static String TAG = "CustomPhotoMenu";

    private final String mSettingOff;

    private String[] mOtherKeys1;
    private String[] mOtherKeys2;
    private ListMenu mListMenu;
    private View mPreviewMenu;
    private static final int POPUP_NONE = 0;
    private static final int POPUP_FIRST_LEVEL = 1;
    private static final int POPUP_SECOND_LEVEL = 2;
    private static final int POPUP_IN_ANIMATION = 3;
    private static final int PREVIEW_MENU_NONE = 0;
    private static final int PREVIEW_MENU_IN_ANIMATION = 1;
    private static final int PREVIEW_MENU_ON = 2;
    private static final int MODE_SCENE = 0;
    private static final int MODE_FILTER = 1;
    private static final int DEVELOPER_MENU_TOUCH_COUNT = 10;
    private int mSceneStatus;
    private View mFlashSwitcher;
    private View mHdrSwitcher;
    private View mFrontBackSwitcher;
    private View mSceneModeSwitcher;
    private View mFilterModeSwitcher;
    private PhotoUI mUI;
    private int mPopupStatus;
    private int mPreviewMenuStatus;
    private ListSubMenu mListSubMenu;
    private CameraActivity mActivity;
    private boolean mHdrOn = false;
    private int privateCounter = 0;
    private static final int ANIMATION_DURATION = 300;
    private static final int CLICK_THRESHOLD = 200;
    private int previewMenuSize;

    public CustomPhotoMenu(CameraActivity activity, PhotoUI ui) {
        super(activity);
        mUI = ui;
        mSettingOff = activity.getString(R.string.setting_off_value);
        mActivity = activity;
        mFrontBackSwitcher = ui.getRootView().findViewById(R.id.front_back_switcher);
        mFlashSwitcher = ui.getRootView().findViewById(R.id.flash_switcher);
        mHdrSwitcher = ui.getRootView().findViewById(R.id.hdr_switcher);
        mSceneModeSwitcher = ui.getRootView().findViewById(R.id.scene_mode_switcher);
        mFilterModeSwitcher = ui.getRootView().findViewById(R.id.filter_mode_switcher);
    }

    public void initialize(PreferenceGroup group) {
        super.initialize(group);
        mListSubMenu = null;
        mListMenu = null;
        mPopupStatus = POPUP_NONE;
        mPreviewMenuStatus = POPUP_NONE;
        final Resources res = mActivity.getResources();
        Locale locale = res.getConfiguration().locale;
        // The order is from left to right in the menu.

        initSceneModeButton(mSceneModeSwitcher);
        initFilterModeButton(mFilterModeSwitcher);
        mHdrSwitcher.setVisibility(View.INVISIBLE);
        mFlashSwitcher.setVisibility(View.INVISIBLE);

        mFrontBackSwitcher.setVisibility(View.INVISIBLE);
        // HDR.
        if (group.findPreference(CameraSettings.KEY_CAMERA_HDR) != null) {
            mHdrSwitcher.setVisibility(View.VISIBLE);
            initSwitchItem(CameraSettings.KEY_CAMERA_HDR, mHdrSwitcher);
        } else {
            mHdrSwitcher.setVisibility(View.INVISIBLE);
        }

        mOtherKeys1 = new String[] {
                CameraSettings.KEY_SCENE_MODE,
                CameraSettings.KEY_RECORD_LOCATION,
                CameraSettings.KEY_PICTURE_SIZE,
                CameraSettings.KEY_JPEG_QUALITY,
                CameraSettings.KEY_TIMER,
                CameraSettings.KEY_CAMERA_SAVEPATH,
                CameraSettings.KEY_LONGSHOT,
                CameraSettings.KEY_COLOR_EFFECT,
                CameraSettings.KEY_FACE_DETECTION,
                CameraSettings.KEY_ISO,
                CameraSettings.KEY_EXPOSURE,
                CameraSettings.KEY_WHITE_BALANCE,
                CameraSettings.KEY_FLASH_MODE,
                CameraSettings.KEY_FOCUS_MODE,
                CameraSettings.KEY_REDEYE_REDUCTION
        };

        mOtherKeys2 = new String[] {
                CameraSettings.KEY_SCENE_MODE,
                CameraSettings.KEY_RECORD_LOCATION,
                CameraSettings.KEY_PICTURE_SIZE,
                CameraSettings.KEY_JPEG_QUALITY,
                CameraSettings.KEY_TIMER,
                CameraSettings.KEY_CAMERA_SAVEPATH,
                CameraSettings.KEY_LONGSHOT,
                CameraSettings.KEY_COLOR_EFFECT,
                CameraSettings.KEY_FACE_DETECTION,
                CameraSettings.KEY_ISO,
                CameraSettings.KEY_EXPOSURE,
                CameraSettings.KEY_WHITE_BALANCE,
                CameraSettings.KEY_FLASH_MODE,
                CameraSettings.KEY_FOCUS_MODE,
                CameraSettings.KEY_REDEYE_REDUCTION,
                CameraSettings.KEY_HISTOGRAM,
                CameraSettings.KEY_ZSL,
                CameraSettings.KEY_TIMER_SOUND_EFFECTS,
                CameraSettings.KEY_FACE_RECOGNITION,
                CameraSettings.KEY_TOUCH_AF_AEC,
                CameraSettings.KEY_SELECTABLE_ZONE_AF,
                CameraSettings.KEY_PICTURE_FORMAT,
                CameraSettings.KEY_SATURATION,
                CameraSettings.KEY_CONTRAST,
                CameraSettings.KEY_SHARPNESS,
                CameraSettings.KEY_AUTOEXPOSURE,
                CameraSettings.KEY_ANTIBANDING,
                CameraSettings.KEY_DENOISE,
                CameraSettings.KEY_ADVANCED_FEATURES,
                CameraSettings.KEY_AE_BRACKET_HDR
        };

        initSwitchItem(CameraSettings.KEY_CAMERA_ID, mFrontBackSwitcher);
        initSwitchItem(CameraSettings.KEY_FLASH_MODE, mFlashSwitcher);
    }

    @Override
    // Hit when an item in a popup gets selected
    public void onListPrefChanged(ListPreference pref) {
        animateFadeOut(mListSubMenu, 2);
        onSettingChanged(pref);
        ((ListMenu) mListMenu).resetHighlight();
    }

    public boolean handleBackKey() {
        if (mPreviewMenuStatus == PREVIEW_MENU_ON) {
            animateSlideOut(mPreviewMenu);
            return true;
        }
        if (mPopupStatus == POPUP_NONE)
            return false;
        if (mPopupStatus == POPUP_FIRST_LEVEL) {
            animateSlideOut(mListMenu, 1);
        } else if (mPopupStatus == POPUP_SECOND_LEVEL) {
            animateFadeOut(mListSubMenu, 2);
            ((ListMenu) mListMenu).resetHighlight();
        }
        return true;
    }

    public void closeSceneMode() {
        mUI.removeSceneModeMenu();
    }

    public void tryToCloseSubList() {
        if (mListMenu != null)
            ((ListMenu) mListMenu).resetHighlight();

        if (mPopupStatus == POPUP_SECOND_LEVEL) {
            mUI.dismissLevel2();
            mPopupStatus = POPUP_FIRST_LEVEL;
        }
    }

    private void animateFadeOut(final ListView v, final int level) {
        if (v == null || mPopupStatus == POPUP_IN_ANIMATION)
            return;
        mPopupStatus = POPUP_IN_ANIMATION;

        ViewPropertyAnimator vp = v.animate();
        vp.alpha(0f).setDuration(ANIMATION_DURATION);
        vp.setListener(new AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (level == 1) {
                    mUI.dismissLevel1();
                    initializePopup();
                    mPopupStatus = POPUP_NONE;
                    mUI.cleanupListview();
                }
                else if (level == 2) {
                    mUI.dismissLevel2();
                    mPopupStatus = POPUP_FIRST_LEVEL;
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (level == 1) {
                    mUI.dismissLevel1();
                    initializePopup();
                    mPopupStatus = POPUP_NONE;
                    mUI.cleanupListview();
                }
                else if (level == 2) {
                    mUI.dismissLevel2();
                    mPopupStatus = POPUP_FIRST_LEVEL;
                }

            }
        });
        vp.start();
    }

    private void animateSlideOut(final ListView v, final int level) {
        if (v == null || mPopupStatus == POPUP_IN_ANIMATION)
            return;
        mPopupStatus = POPUP_IN_ANIMATION;

        ViewPropertyAnimator vp = v.animate();
        vp.translationX(v.getX() - v.getWidth()).setDuration(ANIMATION_DURATION);
        vp.setListener(new AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (level == 1) {
                    mUI.dismissLevel1();
                    initializePopup();
                    mPopupStatus = POPUP_NONE;
                    mUI.cleanupListview();
                }
                else if (level == 2) {
                    mUI.dismissLevel2();
                    mPopupStatus = POPUP_FIRST_LEVEL;
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (level == 1) {
                    mUI.dismissLevel1();
                    initializePopup();
                    mPopupStatus = POPUP_NONE;
                    mUI.cleanupListview();
                }
                else if (level == 2) {
                    mUI.dismissLevel2();
                    mPopupStatus = POPUP_FIRST_LEVEL;
                }

            }
        });
        vp.start();
    }

    public void animateFadeIn(final ListView v) {
        ViewPropertyAnimator vp = v.animate();
        vp.alpha(0.85f).setDuration(ANIMATION_DURATION);
        vp.start();
    }

    public void animateSlideIn(final View v, int delta, boolean settingMenu) {
        int rotation = CameraUtil.getDisplayRotation(mActivity);
        boolean mIsDefaultToPortrait = CameraUtil.isDefaultToPortrait(mActivity);
        if (!mIsDefaultToPortrait) {
            rotation = (rotation + 90) % 360;
        }
        boolean portrait = (rotation == 0) || (rotation == 180);
        if (settingMenu)
            portrait = true;
        ViewPropertyAnimator vp = v.animate();
        if (portrait) {
            float dest = v.getX();
            v.setX(dest - delta);
            vp.translationX(dest).setDuration(ANIMATION_DURATION);
        }
        else {
            float dest = v.getY();
            v.setY(dest + delta);
            vp.translationY(dest).setDuration(ANIMATION_DURATION);
        }
        vp.start();
    }

    public void animateSlideOutPreviewMenu() {
        if (mPreviewMenu == null)
            return;
        animateSlideOut(mPreviewMenu);
    }

    private void animateSlideOut(final View v) {
        if (v == null || mPreviewMenuStatus == PREVIEW_MENU_IN_ANIMATION)
            return;
        mPreviewMenuStatus = PREVIEW_MENU_IN_ANIMATION;
        int rotation = CameraUtil.getDisplayRotation(mActivity);
        boolean mIsDefaultToPortrait = CameraUtil.isDefaultToPortrait(mActivity);
        if (!mIsDefaultToPortrait) {
            rotation = (rotation + 90) % 360;
        }
        boolean portrait = (rotation == 0) || (rotation == 180);
        ViewPropertyAnimator vp = v.animate();
        if (portrait) {
            vp.translationX(v.getX() - v.getWidth()).setDuration(ANIMATION_DURATION);

        } else {
            vp.translationY(v.getY() + v.getHeight()).setDuration(ANIMATION_DURATION);

        }
        vp.setListener(new AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                closeSceneMode();
                mPreviewMenuStatus = PREVIEW_MENU_NONE;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                closeSceneMode();
                mPreviewMenuStatus = PREVIEW_MENU_NONE;

            }
        });
        vp.start();
    }

    private void buttonSetEnabled(View v, boolean enable) {
        v.setEnabled(enable);
        if (v instanceof ViewGroup) {
            View v2 = ((ViewGroup) v).getChildAt(0);
            if (v2 != null)
                v2.setEnabled(enable);

        }

    }

    public boolean isOverMenu(MotionEvent ev) {
        if (mPopupStatus == POPUP_NONE || mPopupStatus == POPUP_IN_ANIMATION)
            return false;
        if (mUI.getMenuLayout() == null)
            return false;
        Rect rec = new Rect();
        mUI.getMenuLayout().getChildAt(0).getHitRect(rec);
        return rec.contains((int) ev.getX(), (int) ev.getY());
    }

    public boolean isOverPreviewMenu(MotionEvent ev) {
        if (mPreviewMenuStatus != PREVIEW_MENU_ON)
            return false;
        if (mUI.getPreviewMenuLayout() == null)
            return false;
        Rect rec = new Rect();
        mUI.getPreviewMenuLayout().getChildAt(0).getHitRect(rec);
        rec.top += (int) mUI.getPreviewMenuLayout().getY();
        rec.bottom += (int) mUI.getPreviewMenuLayout().getY();
        return rec.contains((int) ev.getX(), (int) ev.getY());
    }

    public boolean isMenuBeingShown() {
        return mPopupStatus != POPUP_NONE;
    }

    public boolean isMenuBeingAnimated() {
        return mPopupStatus == POPUP_IN_ANIMATION;
    }

    public boolean isPreviewMenuBeingShown() {
        return mPreviewMenuStatus == PREVIEW_MENU_ON;
    }

    public boolean isPreviewMenuBeingAnimated() {
        return mPreviewMenuStatus == PREVIEW_MENU_IN_ANIMATION;
    }

    public boolean sendTouchToPreviewMenu(MotionEvent ev) {
        return mUI.sendTouchToPreviewMenu(ev);
    }

    public boolean sendTouchToMenu(MotionEvent ev) {
        return mUI.sendTouchToMenu(ev);
    }

    @Override
    public void overrideSettings(final String... keyvalues) {
        for (int i = 0; i < keyvalues.length; i += 2) {
            if (keyvalues[i].equals(CameraSettings.KEY_FLASH_MODE)) {
                buttonSetEnabled(mFlashSwitcher, keyvalues[i + 1] == null);
            }
            if (keyvalues[i].equals(CameraSettings.KEY_SCENE_MODE)) {
                buttonSetEnabled(mSceneModeSwitcher, keyvalues[i + 1] == null);
            }
        }
        super.overrideSettings(keyvalues);
        if ((mListMenu == null))
            initializePopup();
        mListMenu.overrideSettings(keyvalues);
    }

    protected void initializePopup() {
        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        ListMenu popup1 = (ListMenu) inflater.inflate(
                R.layout.list_menu, null, false);

        popup1.setSettingChangedListener(this);

        String[] keys = mOtherKeys1;
        if (mActivity.isDeveloperMenuEnabled())
            keys = mOtherKeys2;
        popup1.initialize(mPreferenceGroup, keys);
        if (mActivity.isSecureCamera()) {
            // Prevent location preference from getting changed in secure camera
            // mode
            popup1.setPreferenceEnabled(CameraSettings.KEY_RECORD_LOCATION, false);
        }
        mListMenu = popup1;

        ListPreference pref = mPreferenceGroup.findPreference(
                CameraSettings.KEY_SCENE_MODE);
        String sceneMode = (pref != null) ? pref.getValue() : null;
        pref = mPreferenceGroup.findPreference(CameraSettings.KEY_FACE_DETECTION);
        String faceDetection = (pref != null) ? pref.getValue() : null;
        pref = mPreferenceGroup.findPreference(CameraSettings.KEY_ZSL);
        String zsl = (pref != null) ? pref.getValue() : null;
        if ((sceneMode != null) && !Parameters.SCENE_MODE_AUTO.equals(sceneMode)) {
            popup1.setPreferenceEnabled(CameraSettings.KEY_FOCUS_MODE, false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_AUTOEXPOSURE, false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_TOUCH_AF_AEC, false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_SATURATION, false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_CONTRAST, false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_SHARPNESS, false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_COLOR_EFFECT, false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_FLASH_MODE, false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_WHITE_BALANCE, false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_EXPOSURE, false);
        }
        if ((zsl != null) && Parameters.ZSL_ON.equals(zsl)) {
            popup1.setPreferenceEnabled(CameraSettings.KEY_FOCUS_MODE, false);
        }
        if ((faceDetection != null) && !Parameters.FACE_DETECTION_ON.equals(faceDetection)) {
            popup1.setPreferenceEnabled(CameraSettings.KEY_FACE_RECOGNITION, false);
        }

        pref = mPreferenceGroup.findPreference(CameraSettings.KEY_ADVANCED_FEATURES);
        String advancedFeatures = (pref != null) ? pref.getValue() : null;

        String ubiFocusOn = mActivity.getString(R.string.
                pref_camera_advanced_feature_value_ubifocus_on);
        String chromaFlashOn = mActivity.getString(R.string.
                pref_camera_advanced_feature_value_chromaflash_on);
        String optiZoomOn = mActivity.getString(R.string.
                pref_camera_advanced_feature_value_optizoom_on);

        if ((zsl != null) && Parameters.ZSL_OFF.equals(zsl)) {
            popup1.overrideSettings(CameraSettings.KEY_ADVANCED_FEATURES,
                    mActivity.getString(R.string.pref_camera_advanced_feature_default));

            popup1.setPreferenceEnabled(CameraSettings.KEY_ADVANCED_FEATURES, false);
            if (mHdrSwitcher.getVisibility() == View.VISIBLE) {
                buttonSetEnabled(mHdrSwitcher, true);
            }
        } else {
            if ((advancedFeatures != null) && (advancedFeatures.equals(ubiFocusOn) ||
                    advancedFeatures.equals(chromaFlashOn) ||
                    advancedFeatures.equals(optiZoomOn))) {
                popup1.setPreferenceEnabled(CameraSettings.KEY_FOCUS_MODE, false);
                popup1.setPreferenceEnabled(CameraSettings.KEY_FLASH_MODE, false);
                popup1.setPreferenceEnabled(CameraSettings.KEY_AE_BRACKET_HDR, false);
                popup1.setPreferenceEnabled(CameraSettings.KEY_REDEYE_REDUCTION, false);
                popup1.setPreferenceEnabled(CameraSettings.KEY_EXPOSURE, false);
                popup1.setPreferenceEnabled(CameraSettings.KEY_COLOR_EFFECT, false);
                popup1.setPreferenceEnabled(CameraSettings.KEY_TOUCH_AF_AEC, false);
                popup1.setPreferenceEnabled(CameraSettings.KEY_SCENE_MODE, false);

                setPreference(CameraSettings.KEY_CAMERA_HDR, mSettingOff);
                if (mHdrSwitcher.getVisibility() == View.VISIBLE) {
                    buttonSetEnabled(mHdrSwitcher, false);
                }
            } else {
                if (mHdrSwitcher.getVisibility() == View.VISIBLE) {
                    buttonSetEnabled(mHdrSwitcher, true);
                }
            }
        }

        pref = mPreferenceGroup.findPreference(CameraSettings.KEY_SCENE_MODE);
        if (pref != null) {
            if (notSame(pref, CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_AUTO)) {
                buttonSetEnabled(mFilterModeSwitcher, false);
            } else {
                buttonSetEnabled(mFilterModeSwitcher, true);
            }
        }

        if (mListener != null) {
            mListener.onSharedPreferenceChanged();
        }
    }

    public void initSwitchItem(final String prefKey, View switcher) {
        final IconListPreference pref =
                (IconListPreference) mPreferenceGroup.findPreference(prefKey);
        if (pref == null)
            return;

        int[] iconIds = pref.getLargeIconIds();
        int resid = -1;
        int index = pref.findIndexOfValue(pref.getValue());
        if (!pref.getUseSingleIcon() && iconIds != null) {
            // Each entry has a corresponding icon.
            resid = iconIds[index];
        } else {
            // The preference only has a single icon to represent it.
            resid = pref.getSingleIcon();
        }
        ImageView iv = (ImageView) ((FrameLayout) switcher).getChildAt(0);
        iv.setImageResource(resid);
        switcher.setVisibility(View.VISIBLE);
        mPreferences.add(pref);
        mPreferenceMap.put(pref, switcher);
        switcher.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                IconListPreference pref = (IconListPreference) mPreferenceGroup
                        .findPreference(prefKey);
                if (pref == null)
                    return;
                int index = pref.findIndexOfValue(pref.getValue());
                CharSequence[] values = pref.getEntryValues();
                index = (index + 1) % values.length;
                pref.setValueIndex(index);
                ImageView iv = (ImageView) ((FrameLayout) v).getChildAt(0);
                iv.setImageResource(((IconListPreference) pref).getLargeIconIds()[index]);
                if (prefKey.equals(CameraSettings.KEY_CAMERA_ID))
                    mListener.onCameraPickerClicked(index);
                reloadPreference(pref);
                onSettingChanged(pref);
            }
        });
    }

    public void initSceneModeButton(View button) {
        button.setVisibility(View.INVISIBLE);
        final IconListPreference pref = (IconListPreference) mPreferenceGroup
                .findPreference(CameraSettings.KEY_SCENE_MODE);
        if (pref == null)
            return;

        int[] iconIds = pref.getLargeIconIds();
        int resid = -1;
        // The preference only has a single icon to represent it.
        resid = pref.getSingleIcon();
        ImageView iv = (ImageView) ((FrameLayout) button).getChildAt(0);
        iv.setImageResource(resid);
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                addSceneMode();
                View view = mUI.getPreviewMenuLayout().getChildAt(0);
                animateSlideIn(view, previewMenuSize, false);
            }
        });
    }

    public void addModeBack() {
        if (mSceneStatus == MODE_SCENE) {
            addSceneMode();
        }
        if (mSceneStatus == MODE_FILTER) {
            addFilterMode();
        }
    }

    public void addSceneMode() {
        final IconListPreference pref = (IconListPreference) mPreferenceGroup
                .findPreference(CameraSettings.KEY_SCENE_MODE);
        if (pref == null)
            return;

        int rotation = CameraUtil.getDisplayRotation(mActivity);
        boolean mIsDefaultToPortrait = CameraUtil.isDefaultToPortrait(mActivity);
        if (!mIsDefaultToPortrait) {
            rotation = (rotation + 90) % 360;
        }
        WindowManager wm = (WindowManager) mActivity.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();

        CharSequence[] entries = pref.getEntries();

        int[] thumbnails = pref.getThumbnailIds();

        int gridRes = 0;
        boolean portrait = (rotation == 0) || (rotation == 180);
        int size = Math.min(display.getWidth(), display.getHeight()) * 30 / 100;
        if (portrait) {
            gridRes = R.layout.vertical_grid;
            size = Math.min(display.getWidth(), display.getHeight()) * 30 / 100;
        } else {
            gridRes = R.layout.horiz_grid;
        }
        previewMenuSize = size;
        mUI.hideUI();
        mPreviewMenuStatus = PREVIEW_MENU_ON;
        mSceneStatus = MODE_SCENE;

        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        FrameLayout basic = (FrameLayout) inflater.inflate(
                gridRes, null, false);

        mUI.dismissSceneModeMenu();
        LinearLayout previewMenuLayout = new LinearLayout(mActivity);
        mUI.setPreviewMenuLayout(previewMenuLayout);
        ViewGroup.LayoutParams params = null;
        if (portrait) {
            params = new ViewGroup.LayoutParams(size, LayoutParams.MATCH_PARENT);
            previewMenuLayout.setLayoutParams(params);
            ((ViewGroup) mUI.getRootView()).addView(previewMenuLayout);
        } else {
            params = new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, size);

            previewMenuLayout.setLayoutParams(params);
            ((ViewGroup) mUI.getRootView()).addView(previewMenuLayout);
            previewMenuLayout.setY(display.getHeight() - size);
        }
        basic.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        LinearLayout layout = (LinearLayout) basic.findViewById(R.id.layout);

        final View[] views = new View[entries.length];
        int init = pref.getCurrentIndex();
        for (int i = 0; i < entries.length; i++) {
            LinearLayout layout2 = (LinearLayout) inflater.inflate(
                    R.layout.scene_mode_view, null, false);

            ImageView imageView = (ImageView) layout2.findViewById(R.id.image);
            TextView label = (TextView) layout2.findViewById(R.id.label);
            final int j = i;

            layout2.setOnTouchListener(new View.OnTouchListener() {
                private long startTime;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        startTime = System.currentTimeMillis();

                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        if (System.currentTimeMillis() - startTime < CLICK_THRESHOLD) {
                            pref.setValueIndex(j);
                            onSettingChanged(pref);
                            for (View v1 : views) {
                                v1.setBackgroundResource(R.drawable.scene_mode_view_border);
                            }
                            View border = v.findViewById(R.id.border);
                            border.setBackgroundResource(R.drawable.scene_mode_view_border_selected);
                        }

                    }
                    return true;
                }
            });

            View border = layout2.findViewById(R.id.border);
            views[j] = border;
            if (i == init)
                border.setBackgroundResource(R.drawable.scene_mode_view_border_selected);
            imageView.setImageResource(thumbnails[i]);
            label.setText(entries[i]);
            layout.addView(layout2);
        }
        previewMenuLayout.addView(basic);
        mPreviewMenu = basic;
    }

    public void initFilterModeButton(View button) {
        button.setVisibility(View.INVISIBLE);
        final IconListPreference pref = (IconListPreference) mPreferenceGroup
                .findPreference(CameraSettings.KEY_COLOR_EFFECT);
        if (pref == null)
            return;

        int[] iconIds = pref.getLargeIconIds();
        int resid = -1;
        // The preference only has a single icon to represent it.
        resid = pref.getSingleIcon();
        ImageView iv = (ImageView) ((FrameLayout) button).getChildAt(0);
        iv.setImageResource(resid);
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                addFilterMode();
                View view = mUI.getPreviewMenuLayout().getChildAt(0);
                animateSlideIn(view, previewMenuSize, false);
            }
        });
    }

    public void addFilterMode() {
        final IconListPreference pref = (IconListPreference) mPreferenceGroup
                .findPreference(CameraSettings.KEY_COLOR_EFFECT);
        if (pref == null)
            return;

        int rotation = CameraUtil.getDisplayRotation(mActivity);
        boolean mIsDefaultToPortrait = CameraUtil.isDefaultToPortrait(mActivity);
        if (!mIsDefaultToPortrait) {
            rotation = (rotation + 90) % 360;
        }
        WindowManager wm = (WindowManager) mActivity.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        CharSequence[] entries = pref.getEntries();
        int gridRes = 0;
        boolean portrait = (rotation == 0) || (rotation == 180);
        int size = Math.min(display.getWidth(), display.getHeight()) * 35 / 100;
        if (portrait) {
            gridRes = R.layout.vertical_grid;
            size = Math.min(display.getWidth(), display.getHeight()) * 30 / 100;
        } else {
            gridRes = R.layout.horiz_grid;
        }
        previewMenuSize = size;
        mUI.hideUI();
        mPreviewMenuStatus = PREVIEW_MENU_ON;
        mSceneStatus = MODE_FILTER;

        int[] thumbnails = pref.getThumbnailIds();

        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        FrameLayout basic = (FrameLayout) inflater.inflate(
                gridRes, null, false);

        mUI.dismissSceneModeMenu();
        LinearLayout previewMenuLayout = new LinearLayout(mActivity);
        mUI.setPreviewMenuLayout(previewMenuLayout);
        ViewGroup.LayoutParams params = null;
        if (portrait) {
            params = new ViewGroup.LayoutParams(size, LayoutParams.MATCH_PARENT);
            previewMenuLayout.setLayoutParams(params);
            ((ViewGroup) mUI.getRootView()).addView(previewMenuLayout);
        } else {
            params = new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, size);
            previewMenuLayout.setLayoutParams(params);
            ((ViewGroup) mUI.getRootView()).addView(previewMenuLayout);
            previewMenuLayout.setY(display.getHeight() - size);
        }
        basic.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        LinearLayout layout = (LinearLayout) basic.findViewById(R.id.layout);
        final View[] views = new View[entries.length];
        int init = pref.getCurrentIndex();
        for (int i = 0; i < entries.length; i++) {
            LinearLayout layout2 = (LinearLayout) inflater.inflate(
                    R.layout.filter_mode_view, null, false);
            ImageView imageView = (ImageView) layout2.findViewById(R.id.image);
            final int j = i;

            layout2.setOnTouchListener(new View.OnTouchListener() {
                private long startTime;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        startTime = System.currentTimeMillis();
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        if (System.currentTimeMillis() - startTime < CLICK_THRESHOLD) {
                            pref.setValueIndex(j);
                            onSettingChanged(pref);
                            for (View v1 : views) {
                                v1.setBackground(null);
                            }
                            ImageView image = (ImageView) v.findViewById(R.id.image);
                            image.setBackgroundColor(0xff33b5e5);
                        }
                    }
                    return true;
                }
            });

            views[j] = imageView;
            if (i == init)
                imageView.setBackgroundColor(0xff33b5e5);
            TextView label = (TextView) layout2.findViewById(R.id.label);
            imageView.setImageResource(thumbnails[i]);
            label.setText(entries[i]);
            layout.addView(layout2);
        }
        previewMenuLayout.addView(basic);
        mPreviewMenu = basic;
    }

    public void openFirstLevel() {
        if (isMenuBeingShown() || CameraControls.isAnimating())
            return;
        if (mListMenu == null || mPopupStatus != POPUP_FIRST_LEVEL) {
            initializePopup();
            mPopupStatus = POPUP_FIRST_LEVEL;
        }
        mUI.showPopup(mListMenu, 1, true);

    }

    public void popupDismissed(boolean dismissAll) {
        if (!dismissAll && mPopupStatus == POPUP_SECOND_LEVEL) {
            initializePopup();
            mPopupStatus = POPUP_FIRST_LEVEL;
            mUI.showPopup(mListMenu, 1, false);
            if (mListMenu != null)
                mListMenu = null;

        } else {
            initializePopup();
        }

    }

    @Override
    // Hit when an item in the first-level popup gets selected, then bring up
    // the second-level popup
    public void onPreferenceClicked(ListPreference pref) {
        onPreferenceClicked(pref, 0);
    }

    public void onPreferenceClicked(ListPreference pref, int y) {
        if (!mActivity.isDeveloperMenuEnabled()) {
            if (pref.getKey().equals(CameraSettings.KEY_REDEYE_REDUCTION)) {
                privateCounter++;
                if (privateCounter >= DEVELOPER_MENU_TOUCH_COUNT) {
                    mActivity.enableDeveloperMenu();
                    SharedPreferences prefs = PreferenceManager
                            .getDefaultSharedPreferences(mActivity);
                    prefs.edit().putBoolean(CameraSettings.KEY_DEVELOPER_MENU, true).apply();
                    Toast toast = Toast.makeText(mActivity,
                            "Camera developer option is enabled now", Toast.LENGTH_SHORT);
                    toast.show();
                }
            } else {
                privateCounter = 0;
            }
        }

        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        ListSubMenu basic = (ListSubMenu) inflater.inflate(
                R.layout.list_sub_menu, null, false);
        basic.initialize(pref, y);
        basic.setSettingChangedListener(this);
        basic.setAlpha(0f);
        mListSubMenu = basic;
        mUI.removeLevel2();
        if (mPopupStatus == POPUP_SECOND_LEVEL) {
            mUI.showPopup(mListSubMenu, 2, false);
        } else {
            mUI.showPopup(mListSubMenu, 2, true);
        }
        mPopupStatus = POPUP_SECOND_LEVEL;
    }

    public void onListMenuTouched() {
        mUI.removeLevel2();
    }

    public void closeAllView() {
        if (mUI != null)
            mUI.removeLevel2();

        if (mListMenu != null) {
            animateSlideOut(mListMenu, 1);
        }
        animateSlideOutPreviewMenu();
    }

    public void closeView() {
        if (mUI != null)
            mUI.removeLevel2();

        if (mListMenu != null && mPopupStatus != POPUP_NONE)
            animateSlideOut(mListMenu, 1);
    }

    // Return true if the preference has the specified key but not the value.
    private static boolean notSame(ListPreference pref, String key, String value) {
        return (key.equals(pref.getKey()) && !value.equals(pref.getValue()));
    }

    private void setPreference(String key, String value) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        if (pref != null && !value.equals(pref.getValue())) {
            pref.setValue(value);
            reloadPreferences();
        }
    }

    @Override
    public void onSettingChanged(ListPreference pref) {
        // Reset the scene mode if HDR is set to on. Reset HDR if scene mode is
        // set to non-auto.
        if (notSame(pref, CameraSettings.KEY_CAMERA_HDR, mSettingOff)) {
            setPreference(CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_AUTO);
            setPreference(CameraSettings.KEY_ZSL, mSettingOff);
            Toast.makeText(mActivity, R.string.hdr_enable_message,
                    Toast.LENGTH_LONG).show();
            mHdrOn = true;
        } else if (notSame(pref, CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_AUTO)) {
            setPreference(CameraSettings.KEY_CAMERA_HDR, mSettingOff);
            if (mHdrOn) {
                Toast.makeText(mActivity, R.string.scene_enable_message,
                        Toast.LENGTH_LONG).show();
            }
            mHdrOn = false;
        }
        if (notSame(pref, CameraSettings.KEY_ZSL, mSettingOff)) {
            setPreference(CameraSettings.KEY_CAMERA_HDR, mSettingOff);
        }
        if (notSame(pref, CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_AUTO)) {
            buttonSetEnabled(mFilterModeSwitcher, false);
        } else {
            buttonSetEnabled(mFilterModeSwitcher, true);
        }
        super.onSettingChanged(pref);
    }

}
