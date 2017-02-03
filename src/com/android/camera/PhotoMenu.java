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

import java.util.HashSet;
import java.util.Locale;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.hardware.Camera.Parameters;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.TextUtils;
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
import com.android.camera.TsMakeupManager.MakeupLevelListener;
import com.android.camera.app.CameraApp;
import com.android.camera.ui.CameraControls;
import com.android.camera.ui.CountdownTimerPopup;
import com.android.camera.ui.ListSubMenu;
import com.android.camera.ui.ListMenu;
import com.android.camera.ui.ModuleSwitcher;
import com.android.camera.ui.RotateLayout;
import com.android.camera.ui.RotateImageView;
import com.android.camera.ui.RotateTextToast;
import org.codeaurora.snapcam.R;
import android.widget.HorizontalScrollView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.Display;
import com.android.camera.util.CameraUtil;
import java.util.Locale;

public class PhotoMenu extends MenuController
        implements ListMenu.Listener,
        CountdownTimerPopup.Listener,
        ListSubMenu.Listener {
    private static String TAG = "PhotoMenu";

    private final String mSettingOff;
    private final String mSettingOn;

    private String[] mOtherKeys1;
    private String[] mOtherKeys2;
    private ListMenu mListMenu;
    private View mPreviewMenu;
    private static final int POPUP_NONE = 0;
    private static final int POPUP_FIRST_LEVEL = 1;
    private static final int POPUP_SECOND_LEVEL = 2;
    private static final int POPUP_IN_ANIMATION_SLIDE = 3;
    private static final int POPUP_IN_ANIMATION_FADE = 4;
    private static final int POPUP_IN_MAKEUP    = 5;
    private static final int PREVIEW_MENU_NONE = 0;
    private static final int PREVIEW_MENU_IN_ANIMATION = 1;
    private static final int PREVIEW_MENU_ON = 2;
    private static final int MODE_SCENE = 0;
    private static final int MODE_FILTER = 1;
    private static final int MODE_MAKEUP = 2;
    private static final int DEVELOPER_MENU_TOUCH_COUNT = 10;
    private int mSceneStatus;
    private View mHdrSwitcher;
    private View mTsMakeupSwitcher;
    private View mFrontBackSwitcher;
    private View mSceneModeSwitcher;
    private View mFilterModeSwitcher;
    private View mCameraSwitcher;
    private View mSettingMenu;
    private View mPreviewThumbnail;
    private PhotoUI mUI;
    private int mPopupStatus;
    private int mPreviewMenuStatus;
    private ListSubMenu mListSubMenu;
    private CameraActivity mActivity;
    private String mPrevSavedCDS;
    private boolean mIsTNREnabled = false;
    private boolean mIsCDSUpdated = false;
    private int privateCounter = 0;
    private static final int ANIMATION_DURATION = 300;
    private static final int CLICK_THRESHOLD = 200;
    private int previewMenuSize;
    private TsMakeupManager mTsMakeupManager;
    private MakeupLevelListener mMakeupListener;
    private MakeupHandler mHandler = new MakeupHandler();
    private static final int MAKEUP_MESSAGE_ID = 0;
    private HashSet<View> mWasVisibleSet = new HashSet<View>();

    public PhotoMenu(CameraActivity activity, PhotoUI ui, MakeupLevelListener makeupListener) {
        super(activity);
        mUI = ui;
        mSettingOff = activity.getString(R.string.setting_off_value);
        mSettingOn = activity.getString(R.string.setting_on_value);
        mActivity = activity;
        mFrontBackSwitcher = ui.getRootView().findViewById(R.id.front_back_switcher);
        mHdrSwitcher = ui.getRootView().findViewById(R.id.hdr_switcher);
        mTsMakeupSwitcher = ui.getRootView().findViewById(R.id.ts_makeup_switcher);
        mSceneModeSwitcher = ui.getRootView().findViewById(R.id.scene_mode_switcher);
        mFilterModeSwitcher = ui.getRootView().findViewById(R.id.filter_mode_switcher);
        mMakeupListener = makeupListener;
        mSettingMenu = ui.getRootView().findViewById(R.id.menu);
        mCameraSwitcher = ui.getRootView().findViewById(R.id.camera_switcher);
        mPreviewThumbnail = ui.getRootView().findViewById(R.id.preview_thumb);
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

        if(TsMakeupManager.HAS_TS_MAKEUP) {
            if(mTsMakeupManager != null) {
                mTsMakeupManager.removeAllViews();
                mTsMakeupManager = null;
            }
            if(mTsMakeupManager == null) {
                mTsMakeupManager = new TsMakeupManager(mActivity, this, mUI, mPreferenceGroup, mTsMakeupSwitcher);
                mTsMakeupManager.setMakeupLevelListener(mMakeupListener);
            }
        }

        initSceneModeButton(mSceneModeSwitcher);
        initFilterModeButton(mFilterModeSwitcher);
        if(TsMakeupManager.HAS_TS_MAKEUP) {
            initMakeupModeButton(mTsMakeupSwitcher);
        } else {
            mHdrSwitcher.setVisibility(View.INVISIBLE);
        }

        mFrontBackSwitcher.setVisibility(View.INVISIBLE);
        if(!TsMakeupManager.HAS_TS_MAKEUP) {
            // HDR.
            if (group.findPreference(CameraSettings.KEY_CAMERA_HDR) != null) {
                mHdrSwitcher.setVisibility(View.VISIBLE);
                initSwitchItem(CameraSettings.KEY_CAMERA_HDR, mHdrSwitcher);
            } else {
                mHdrSwitcher.setVisibility(View.INVISIBLE);
            }
        }

        mOtherKeys1 = new String[] {
                CameraSettings.KEY_SELFIE_FLASH,
                CameraSettings.KEY_FLASH_MODE,
                CameraSettings.KEY_RECORD_LOCATION,
                CameraSettings.KEY_PICTURE_SIZE,
                CameraSettings.KEY_JPEG_QUALITY,
                CameraSettings.KEY_TIMER,
                CameraSettings.KEY_CAMERA_SAVEPATH,
                CameraSettings.KEY_LONGSHOT,
                CameraSettings.KEY_FACE_DETECTION,
                CameraSettings.KEY_ISO,
                CameraSettings.KEY_EXPOSURE,
                CameraSettings.KEY_WHITE_BALANCE,
                CameraSettings.KEY_QC_CHROMA_FLASH,
                CameraSettings.KEY_REDEYE_REDUCTION,
                CameraSettings.KEY_SELFIE_MIRROR,
                CameraSettings.KEY_SHUTTER_SOUND
        };

        mOtherKeys2 = new String[] {
                CameraSettings.KEY_SELFIE_FLASH,
                CameraSettings.KEY_FLASH_MODE,
                CameraSettings.KEY_RECORD_LOCATION,
                CameraSettings.KEY_PICTURE_SIZE,
                CameraSettings.KEY_JPEG_QUALITY,
                CameraSettings.KEY_TIMER,
                CameraSettings.KEY_CAMERA_SAVEPATH,
                CameraSettings.KEY_LONGSHOT,
                CameraSettings.KEY_FACE_DETECTION,
                CameraSettings.KEY_ISO,
                CameraSettings.KEY_EXPOSURE,
                CameraSettings.KEY_WHITE_BALANCE,
                CameraSettings.KEY_QC_CHROMA_FLASH,
                CameraSettings.KEY_FOCUS_MODE,
                CameraSettings.KEY_REDEYE_REDUCTION,
                CameraSettings.KEY_AUTO_HDR,
                CameraSettings.KEY_HDR_MODE,
                CameraSettings.KEY_HDR_NEED_1X,
                CameraSettings.KEY_CDS_MODE,
                CameraSettings.KEY_TNR_MODE,
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
                CameraSettings.KEY_AE_BRACKET_HDR,
                CameraSettings.KEY_INSTANT_CAPTURE,
                CameraSettings.KEY_BOKEH_MODE,
                CameraSettings.KEY_BOKEH_MPO,
                CameraSettings.KEY_MANUAL_EXPOSURE,
                CameraSettings.KEY_MANUAL_WB,
                CameraSettings.KEY_MANUAL_FOCUS,
                CameraSettings.KEY_SELFIE_MIRROR,
                CameraSettings.KEY_SHUTTER_SOUND
        };

        initSwitchItem(CameraSettings.KEY_CAMERA_ID, mFrontBackSwitcher);
    }

    protected class MakeupHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MAKEUP_MESSAGE_ID:
                    mTsMakeupManager.showMakeupView();
                    mUI.adjustOrientation();
                    break;
            }
        }
    }

    @Override
    // Hit when an item in a popup gets selected
    public void onListPrefChanged(ListPreference pref) {
        onSettingChanged(pref);
        closeView();
    }

    public boolean handleBackKey() {
        if(TsMakeupManager.HAS_TS_MAKEUP && mTsMakeupManager.isShowMakeup()) {
            mTsMakeupManager.dismissMakeupUI();
            closeMakeupMode(true);
            mTsMakeupManager.resetMakeupUIStatus();
            mPopupStatus = POPUP_NONE;
            mPreviewMenuStatus = PREVIEW_MENU_NONE;
            return true;
        }
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

    public void closeMakeupMode(boolean isMakeup) {
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
        if (v == null || mPopupStatus == POPUP_IN_ANIMATION_FADE)
            return;
        mPopupStatus = POPUP_IN_ANIMATION_FADE;

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
        if (v == null || mPopupStatus == POPUP_IN_ANIMATION_SLIDE)
            return;
        mPopupStatus = POPUP_IN_ANIMATION_SLIDE;

        ViewPropertyAnimator vp = v.animate();
        if (View.LAYOUT_DIRECTION_RTL == TextUtils
                .getLayoutDirectionFromLocale(Locale.getDefault())) {
            switch (mUI.getOrientation()) {
                case 0:
                    vp.translationXBy(v.getWidth());
                    break;
                case 90:
                    vp.translationYBy(-2 * v.getHeight());
                    break;
                case 180:
                    vp.translationXBy(-2 * v.getWidth());
                    break;
                case 270:
                    vp.translationYBy(v.getHeight());
                    break;
            }
        } else {
            switch (mUI.getOrientation()) {
                case 0:
                    vp.translationXBy(-v.getWidth());
                    break;
                case 90:
                    vp.translationYBy(2 * v.getHeight());
                    break;
                case 180:
                    vp.translationXBy(2 * v.getWidth());
                    break;
                case 270:
                    vp.translationYBy(-v.getHeight());
                    break;
            }
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
                if (level == 1) {
                    mUI.dismissLevel1();
                    initializePopup();
                    mPopupStatus = POPUP_NONE;
                    mUI.cleanupListview();
                } else if (level == 2) {
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
                } else if (level == 2) {
                    mUI.dismissLevel2();
                    mPopupStatus = POPUP_FIRST_LEVEL;
                }

            }
        });
        vp.setDuration(ANIMATION_DURATION).start();
    }

    public void animateFadeIn(final ListView v) {
        ViewPropertyAnimator vp = v.animate();
        vp.alpha(0.85f).setDuration(ANIMATION_DURATION);
        vp.start();
    }

    public void animateSlideIn(final View v, int delta, boolean forcePortrait) {
        int orientation = mUI.getOrientation();
        if (!forcePortrait)
            orientation = 0;

        ViewPropertyAnimator vp = v.animate();
        float dest;
        if (View.LAYOUT_DIRECTION_RTL == TextUtils
                .getLayoutDirectionFromLocale(Locale.getDefault())) {
            switch (orientation) {
                case 0:
                    dest = v.getX();
                    v.setX(-(dest - delta));
                    vp.translationX(dest);
                    break;
                case 90:
                    dest = v.getY();
                    v.setY(-(dest + delta));
                    vp.translationY(dest);
                    break;
                case 180:
                    dest = v.getX();
                    v.setX(-(dest + delta));
                    vp.translationX(dest);
                    break;
                case 270:
                    dest = v.getY();
                    v.setY(-(dest - delta));
                    vp.translationY(dest);
                    break;
            }
        } else {
            switch (orientation) {
                case 0:
                    dest = v.getX();
                    v.setX(dest - delta);
                    vp.translationX(dest);
                    break;
                case 90:
                    dest = v.getY();
                    v.setY(dest + delta);
                    vp.translationY(dest);
                    break;
                case 180:
                    dest = v.getX();
                    v.setX(dest + delta);
                    vp.translationX(dest);
                    break;
                case 270:
                    dest = v.getY();
                    v.setY(dest - delta);
                    vp.translationY(dest);
                    break;
            }
        }
        vp.setDuration(ANIMATION_DURATION).start();
    }

    public void animateSlideOutPreviewMenu() {
        if(TsMakeupManager.HAS_TS_MAKEUP && mTsMakeupManager.isShowMakeup()) {
            mPreviewMenuStatus = PREVIEW_MENU_NONE;
            mTsMakeupManager.dismissMakeupUI();
            closeMakeupMode(true);
            mTsMakeupManager.resetMakeupUIStatus();
        }

        if (mPreviewMenu == null)
            return;
        animateSlideOut(mPreviewMenu);
    }

    private void animateSlideOut(final View v) {
        if (v == null || mPreviewMenuStatus == PREVIEW_MENU_IN_ANIMATION)
            return;
        mPreviewMenuStatus = PREVIEW_MENU_IN_ANIMATION;

        ViewPropertyAnimator vp = v.animate();
        if (View.LAYOUT_DIRECTION_RTL == TextUtils
                .getLayoutDirectionFromLocale(Locale.getDefault())) {
            vp.translationXBy(v.getWidth()).setDuration(ANIMATION_DURATION);
        } else {
            vp.translationXBy(-v.getWidth()).setDuration(ANIMATION_DURATION);
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
        if (mPopupStatus == POPUP_NONE
                || mPopupStatus == POPUP_IN_ANIMATION_SLIDE
                || mPopupStatus == POPUP_IN_ANIMATION_FADE)
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
        if (View.LAYOUT_DIRECTION_RTL == TextUtils
                .getLayoutDirectionFromLocale(Locale.getDefault())) {
            rec.left = mUI.getRootView().getWidth() - (rec.right-rec.left);
            rec.right = mUI.getRootView().getWidth();
        }
        rec.top += (int) mUI.getPreviewMenuLayout().getY();
        rec.bottom += (int) mUI.getPreviewMenuLayout().getY();
        return rec.contains((int) ev.getX(), (int) ev.getY());
    }

    public boolean isMenuBeingShown() {
        return mPopupStatus != POPUP_NONE;
    }

    public boolean isMenuBeingAnimated() {
        return mPopupStatus == POPUP_IN_ANIMATION_SLIDE || mPopupStatus == POPUP_IN_ANIMATION_FADE;
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
        if (mListMenu != null) {
            ListPreference pref_tnr = mPreferenceGroup.findPreference(CameraSettings.KEY_TNR_MODE);
            ListPreference pref_cds = mPreferenceGroup.findPreference(CameraSettings.KEY_CDS_MODE);

            String tnr = (pref_tnr != null) ? pref_tnr.getValue() : null;
            String cds = (pref_cds != null) ? pref_cds.getValue() : null;

            if (mPrevSavedCDS == null && cds != null) {
                mPrevSavedCDS = cds;
            }

            if ((tnr != null) && !mActivity.getString(R.string.
                    pref_camera_tnr_default).equals(tnr)) {
                mListMenu.setPreferenceEnabled(CameraSettings.KEY_CDS_MODE, false);
                mListMenu.overrideSettings(CameraSettings.KEY_CDS_MODE,
                        mActivity.getString(R.string.pref_camera_cds_value_off));
                mIsTNREnabled = true;
                if (!mIsCDSUpdated) {
                    if (cds != null) {
                        mPrevSavedCDS = cds;
                    }
                    mIsCDSUpdated = true;
                }
            } else if (tnr != null) {
                mListMenu.setPreferenceEnabled(CameraSettings.KEY_CDS_MODE, true);
                if (mIsTNREnabled && mPrevSavedCDS != cds) {
                    mListMenu.overrideSettings(CameraSettings.KEY_CDS_MODE, mPrevSavedCDS);
                    mIsTNREnabled = false;
                    mIsCDSUpdated = false;
                }
            }
        }
        for (int i = 0; i < keyvalues.length; i += 2) {
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
        updateFilterModeIcon(pref, mPreferenceGroup.findPreference(CameraSettings.KEY_CAMERA_HDR));
        String sceneMode = (pref != null) ? pref.getValue() : null;
        pref = mPreferenceGroup.findPreference(CameraSettings.KEY_FACE_DETECTION);
        String faceDetection = (pref != null) ? pref.getValue() : null;
        pref = mPreferenceGroup.findPreference(CameraSettings.KEY_ZSL);
        String zsl = (pref != null) ? pref.getValue() : null;
        pref = mPreferenceGroup.findPreference(CameraSettings.KEY_AUTO_HDR);
        String autohdr = (pref != null) ? pref.getValue() : null;
        if (((sceneMode != null) && !Parameters.SCENE_MODE_AUTO.equals(sceneMode))
                || ((autohdr != null) && autohdr.equals("enable"))) {
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
            popup1.setPreferenceEnabled(CameraSettings.KEY_QC_CHROMA_FLASH, false);
        }
        if ((autohdr != null) && autohdr.equals("enable")) {
            popup1.setPreferenceEnabled(CameraSettings.KEY_SCENE_MODE, false);
        }
        if ((zsl != null) && Parameters.ZSL_ON.equals(zsl)) {
            popup1.setPreferenceEnabled(CameraSettings.KEY_FOCUS_MODE, false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_MANUAL_EXPOSURE, false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_MANUAL_WB, false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_MANUAL_FOCUS, false);
        }
        if ((faceDetection != null) && !Parameters.FACE_DETECTION_ON.equals(faceDetection)) {
            popup1.setPreferenceEnabled(CameraSettings.KEY_FACE_RECOGNITION, false);
        }
        popup1.setPreferenceEnabled(CameraSettings.KEY_ZSL, !mUI.isCountingDown());

        pref = mPreferenceGroup.findPreference(CameraSettings.KEY_ADVANCED_FEATURES);
        String advancedFeatures = (pref != null) ? pref.getValue() : null;

        String ubiFocusOn = mActivity.getString(R.string.
                pref_camera_advanced_feature_value_ubifocus_on);
        String reFocusOn = mActivity.getString(R.string.
                pref_camera_advanced_feature_value_refocus_on);
        String chromaFlashOn = mActivity.getString(R.string.
                pref_camera_advanced_feature_value_chromaflash_on);
        String optiZoomOn = mActivity.getString(R.string.
                pref_camera_advanced_feature_value_optizoom_on);
        String fssrOn = mActivity.getString(R.string.
                pref_camera_advanced_feature_value_FSSR_on);
        String truePortraitOn = mActivity.getString(R.string.
                pref_camera_advanced_feature_value_trueportrait_on);
        String multiTouchFocusOn = mActivity.getString(R.string.
                pref_camera_advanced_feature_value_multi_touch_focus_on);

        if ((zsl != null) && Parameters.ZSL_OFF.equals(zsl)) {
            popup1.overrideSettings(CameraSettings.KEY_ADVANCED_FEATURES,
                    mActivity.getString(R.string.pref_camera_advanced_feature_default));

            popup1.setPreferenceEnabled(CameraSettings.KEY_ADVANCED_FEATURES, false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_INSTANT_CAPTURE, false);

            if(!TsMakeupManager.HAS_TS_MAKEUP) {
                if (mHdrSwitcher.getVisibility() == View.VISIBLE) {
                    buttonSetEnabled(mHdrSwitcher, true);
                }
            }
        } else {
            if ((advancedFeatures != null) && (advancedFeatures.equals(ubiFocusOn) ||
                    advancedFeatures.equals(chromaFlashOn) ||
                    advancedFeatures.equals(reFocusOn) ||
                    advancedFeatures.equals(optiZoomOn) ||
                    advancedFeatures.equals(fssrOn) ||
                    advancedFeatures.equals(truePortraitOn) ||
                    advancedFeatures.equals(multiTouchFocusOn))) {
                popup1.setPreferenceEnabled(CameraSettings.KEY_FOCUS_MODE, false);
                popup1.setPreferenceEnabled(CameraSettings.KEY_FLASH_MODE, false);
                popup1.setPreferenceEnabled(CameraSettings.KEY_AE_BRACKET_HDR, false);
                popup1.setPreferenceEnabled(CameraSettings.KEY_REDEYE_REDUCTION, false);
                popup1.setPreferenceEnabled(CameraSettings.KEY_EXPOSURE, false);
                popup1.setPreferenceEnabled(CameraSettings.KEY_COLOR_EFFECT, false);
                popup1.setPreferenceEnabled(CameraSettings.KEY_TOUCH_AF_AEC, false);
                popup1.setPreferenceEnabled(CameraSettings.KEY_SCENE_MODE, false);
                popup1.setPreferenceEnabled(CameraSettings.KEY_INSTANT_CAPTURE, false);
                setPreference(CameraSettings.KEY_CAMERA_HDR, mSettingOff);
                if(!TsMakeupManager.HAS_TS_MAKEUP) {
                    if (mHdrSwitcher.getVisibility() == View.VISIBLE) {
                        buttonSetEnabled(mHdrSwitcher, false);
                    }
                }
            } else {
                if(!TsMakeupManager.HAS_TS_MAKEUP) {
                    if (mHdrSwitcher.getVisibility() == View.VISIBLE) {
                        buttonSetEnabled(mHdrSwitcher, true);
                    }
                }
            }
        }

        if ((autohdr != null) && autohdr.equals("enable")) {
            mHdrSwitcher.setVisibility(View.GONE);
            mUI.getCameraControls().removeFromViewList(mHdrSwitcher);
        } else {
            mHdrSwitcher.setVisibility(View.VISIBLE);
        }

        if (mListener != null) {
            mListener.onSharedPreferenceChanged();
        }
    }

    private void updateFilterModeIcon(ListPreference scenePref, ListPreference hdrPref) {
        if (scenePref == null || hdrPref == null) return;
        if ((notSame(scenePref, CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_AUTO))
                || (notSame(hdrPref, CameraSettings.KEY_CAMERA_HDR, mSettingOff))) {
            buttonSetEnabled(mFilterModeSwitcher, false);
            changeFilterModeControlIcon("none");
        } else if (same(scenePref, CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_AUTO)
                && (same(hdrPref, CameraSettings.KEY_CAMERA_HDR, mSettingOff)
                    || !hdrPref.getKey().equals(CameraSettings.KEY_CAMERA_HDR))) {
            //mFilterModeSwitcher can be enabled only when scene mode is set to auto
            // and HDR is set to off,
            buttonSetEnabled(mFilterModeSwitcher, true);
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
        ((ImageView) switcher).setImageResource(resid);
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
                if (prefKey.equals(CameraSettings.KEY_CAMERA_ID)) {
                    // Hide the camera control while switching the camera.
                    // The camera control will be added back when
                    // onCameraPickerClicked is completed
                    mUI.hideUI();
                }
                int index = pref.findIndexOfValue(pref.getValue());
                CharSequence[] values = pref.getEntryValues();
                index = (index + 1) % values.length;
                pref.setValueIndex(index);
                ((ImageView) v).setImageResource(
                        ((IconListPreference) pref).getLargeIconIds()[index]);
                if (prefKey.equals(CameraSettings.KEY_CAMERA_ID))
                    mListener.onCameraPickerClicked(index);
                reloadPreference(pref);
                onSettingChanged(pref);
            }
        });
    }

    public void initMakeupModeButton(View button) {
        if(!TsMakeupManager.HAS_TS_MAKEUP) {
            return;
        }
        button.setVisibility(View.INVISIBLE);
        final IconListPreference pref = (IconListPreference) mPreferenceGroup
                .findPreference(CameraSettings.KEY_TS_MAKEUP_UILABLE);
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
        ImageView iv = (ImageView) mTsMakeupSwitcher;
        iv.setImageResource(resid);

        button.setVisibility(View.VISIBLE);

        String makeupOn = pref.getValue();
        Log.d(TAG, "PhotoMenu.initMakeupModeButton():current init makeupOn is " + makeupOn);

        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ListPreference faceDetectPref = mPreferenceGroup.findPreference(CameraSettings.KEY_FACE_DETECTION);
                String faceDetection = (faceDetectPref != null) ? faceDetectPref.getValue() : null;
                Log.d(TAG, "initMakeupModeButton().onClick(): faceDetection is " + faceDetection);
                if ((faceDetection != null) && Parameters.FACE_DETECTION_OFF.equals(faceDetection)) {
                    showAlertDialog(faceDetectPref);
                } else {
                    toggleMakeupSettings();
                }
            }
        });
    }

    private void initMakeupMenu() {
        if(!TsMakeupManager.HAS_TS_MAKEUP) {
            return;
        }
        mPopupStatus = POPUP_NONE;
        mHandler.removeMessages(MAKEUP_MESSAGE_ID);
        mSceneStatus = MODE_MAKEUP;
        mPreviewMenuStatus = PREVIEW_MENU_ON;
        mHandler.sendEmptyMessageDelayed(MAKEUP_MESSAGE_ID, ANIMATION_DURATION);
    }

    private void showAlertDialog(final ListPreference faceDetectPref) {
        if(mActivity.isFinishing()) {
            return;
        }
        new AlertDialog.Builder(mActivity)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setMessage(R.string.text_tsmakeup_alert_msg)
            .setPositiveButton(R.string.text_tsmakeup_alert_continue, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    toggleMakeupSettings();

                    faceDetectPref.setValue(Parameters.FACE_DETECTION_ON);
                    onSettingChanged(faceDetectPref);
                }
            })
            .setNegativeButton(R.string.text_tsmakeup_alert_quit, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            })
            .show();
    }

    private void toggleMakeupSettings() {
        mUI.hideUI();
        initMakeupMenu();
    }

    private void closeMakeup() {
        if(TsMakeupManager.HAS_TS_MAKEUP) {
            if(mTsMakeupManager.isShowMakeup()) {
                mTsMakeupManager.hideMakeupUI();
                closeMakeupMode(false);
                mPreviewMenuStatus = PREVIEW_MENU_NONE;
            } else {
                mTsMakeupManager.hideMakeupUI();
            }
        }
    }

    public void initSceneModeButton(View button) {
        button.setVisibility(View.INVISIBLE);
        final IconListPreference pref = (IconListPreference) mPreferenceGroup
                .findPreference(CameraSettings.KEY_SCENE_MODE);
        if (pref == null)
            return;
        updateSceneModeIcon(pref);
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                addSceneMode();
                ViewGroup menuLayout = mUI.getPreviewMenuLayout();
                if (menuLayout != null) {
                    View view = menuLayout.getChildAt(0);
                    mUI.adjustOrientation();
                    animateSlideIn(view, previewMenuSize, false);
                }
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
        CharSequence[] entryValues = pref.getEntryValues();

        int[] thumbnails = pref.getThumbnailIds();

        Resources r = mActivity.getResources();
        int height = (int) (r.getDimension(R.dimen.scene_mode_height) + 2
                * r.getDimension(R.dimen.scene_mode_padding) + 1);
        int width = (int) (r.getDimension(R.dimen.scene_mode_width) + 2
                * r.getDimension(R.dimen.scene_mode_padding) + 1);

        int gridRes = 0;
        boolean portrait = (rotation == 0) || (rotation == 180);
        int size = height;
        if (portrait) {
            gridRes = R.layout.vertical_grid;
            size = width;
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
            RotateLayout layout2 = (RotateLayout) inflater.inflate(
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
                            updateSceneModeIcon(pref);
                            for (View v1 : views) {
                                v1.setBackgroundResource(R.drawable.scene_mode_view_border);
                            }
                            View border = v.findViewById(R.id.border);
                            border.setBackgroundResource(R.drawable.scene_mode_view_border_selected);
                            animateSlideOutPreviewMenu();
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

            // ASD only available when developer options are enabled.
            if(entryValues[i].equals("asd")) {
                layout2.setVisibility(mActivity.isDeveloperMenuEnabled()?View.VISIBLE:View.GONE);
            } else if(entryValues[i].equals("hdr")) {
                ListPreference autoHdrPref = mPreferenceGroup.findPreference(CameraSettings.KEY_AUTO_HDR);
                if (autoHdrPref != null && autoHdrPref.getValue().equalsIgnoreCase("enable")) {
                    layout2.setVisibility(View.GONE);
                }
            } else if(CameraApp.mIsLowMemoryDevice &&
                    (entryValues[i].equals(mActivity.getResources().getString(R.string.pref_camera_advanced_feature_value_refocus_on))
                            ||
                     entryValues[i].equals(mActivity.getResources().getString(R.string.pref_camera_advanced_feature_value_optizoom_on)))) {
                    layout2.setVisibility(View.GONE);
            }
        }
        previewMenuLayout.addView(basic);
        mPreviewMenu = basic;
    }

    public void updateSceneModeIcon(IconListPreference pref) {
        int[] thumbnails = pref.getThumbnailIds();
        int ind = pref.getCurrentIndex();
        if (ind == -1)
            ind = 0;
        ((ImageView) mSceneModeSwitcher).setImageResource(thumbnails[ind]);
    }

    public void initFilterModeButton(View button) {
        button.setVisibility(View.INVISIBLE);
        final IconListPreference pref = (IconListPreference) mPreferenceGroup
                .findPreference(CameraSettings.KEY_COLOR_EFFECT);
        if (pref == null || pref.getValue() == null)
            return;
        changeFilterModeControlIcon(pref.getValue());
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                closeMakeup();

                addFilterMode();
                ViewGroup menuLayout = mUI.getPreviewMenuLayout();
                if (menuLayout != null) {
                    View view = mUI.getPreviewMenuLayout().getChildAt(0);
                    mUI.adjustOrientation();
                    animateSlideIn(view, previewMenuSize, false);
                }
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

        Resources r = mActivity.getResources();
        int height = (int) (r.getDimension(R.dimen.filter_mode_height) + 2
                * r.getDimension(R.dimen.filter_mode_padding) + 1);
        int width = (int) (r.getDimension(R.dimen.filter_mode_width) + 2
                * r.getDimension(R.dimen.filter_mode_padding) + 1);

        int gridRes = 0;
        boolean portrait = (rotation == 0) || (rotation == 180);
        int size = height;
        if (portrait) {
            gridRes = R.layout.vertical_grid;
            size = width;
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
            RotateLayout layout2 = (RotateLayout) inflater.inflate(
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
                            changeFilterModeControlIcon(pref.getValue());
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

    private void changeFilterModeControlIcon(String value) {
        if(!value.equals("")) {
            if(value.equalsIgnoreCase("none")) {
                value = "Off";
            } else {
                value = "On";
            }
            final IconListPreference pref = (IconListPreference) mPreferenceGroup
                    .findPreference(CameraSettings.KEY_FILTER_MODE);
            pref.setValue(value);
            int index = pref.getCurrentIndex();
            ImageView iv = (ImageView) mFilterModeSwitcher;
            iv.setImageResource(((IconListPreference) pref).getLargeIconIds()[index]);
        }
    }

    public void openFirstLevel() {
        if (isMenuBeingShown() || CameraControls.isAnimating()) {
            return;
        }
        if(TsMakeupManager.HAS_TS_MAKEUP) {
            if(mTsMakeupManager.isShowMakeup()) {
                mTsMakeupManager.dismissMakeupUI();
                closeMakeupMode(false);
                mPreviewMenuStatus = PREVIEW_MENU_NONE;
            } else {
                mTsMakeupManager.dismissMakeupUI();
            }
            mTsMakeupManager.resetMakeupUIStatus();
        }
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
                    RotateTextToast.makeText(mActivity,
                            "Camera developer option is enabled now", Toast.LENGTH_SHORT).show();
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
        mPopupStatus = POPUP_FIRST_LEVEL;
    }

    public void removeAllView() {
        if (mUI != null)
            mUI.removeLevel2();

        if (mListMenu != null) {
            mUI.dismissLevel1();
            mPopupStatus = POPUP_NONE;
        }
        closeSceneMode();
        mPreviewMenuStatus = PREVIEW_MENU_NONE;
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

    // Return true if the preference has the specified key and the value.
    private static boolean same(ListPreference pref, String key, String value) {
        return (key.equals(pref.getKey()) && value.equals(pref.getValue()));
    }

    public void setPreference(String key, String value) {
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
        if (same(pref, CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_HDR)) {
            ListPreference hdrPref =
                    mPreferenceGroup.findPreference(CameraSettings.KEY_CAMERA_HDR);
            if (hdrPref != null && same(hdrPref, CameraSettings.KEY_CAMERA_HDR, mSettingOff)) {
                setPreference(CameraSettings.KEY_CAMERA_HDR, mSettingOn);
            }
        } else if (notSame(pref, CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_HDR)) {
            ListPreference hdrPref =
                    mPreferenceGroup.findPreference(CameraSettings.KEY_CAMERA_HDR);
            if (hdrPref != null && notSame(hdrPref, CameraSettings.KEY_CAMERA_HDR, mSettingOff)) {
                setPreference(CameraSettings.KEY_CAMERA_HDR, mSettingOff);
            }
        } else if (same(pref, CameraSettings.KEY_CAMERA_HDR, mSettingOff)) {
            ListPreference scenePref =
                    mPreferenceGroup.findPreference(CameraSettings.KEY_SCENE_MODE);
            if (scenePref != null && notSame(scenePref, CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_AUTO)) {
                setPreference(CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_AUTO);
            }
            updateSceneModeIcon((IconListPreference) scenePref);
            updateFilterModeIcon(scenePref, pref);
        } else if (same(pref, CameraSettings.KEY_CAMERA_HDR, mSettingOn)) {
            ListPreference scenePref =
                    mPreferenceGroup.findPreference(CameraSettings.KEY_SCENE_MODE);
            if (scenePref != null && notSame(scenePref, CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_HDR)) {
                setPreference(CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_HDR);
            }
            updateSceneModeIcon((IconListPreference) scenePref);
        } else if (notSame(pref,CameraSettings.KEY_AE_BRACKET_HDR,"Off")) {
            RotateTextToast.makeText(mActivity,
                           R.string.flash_aebracket_message,Toast.LENGTH_SHORT).show();
            setPreference(CameraSettings.KEY_FLASH_MODE,Parameters.FLASH_MODE_OFF);
        } else if (notSame(pref,CameraSettings.KEY_FLASH_MODE,"Off")) {
            ListPreference aePref =
                      mPreferenceGroup.findPreference(CameraSettings.KEY_AE_BRACKET_HDR);
            if (notSame(aePref,CameraSettings.KEY_AE_BRACKET_HDR,"Off")) {
               RotateTextToast.makeText(mActivity,
                              R.string.flash_aebracket_message,Toast.LENGTH_SHORT).show();
            }
        } else if (notSame(pref, CameraSettings.KEY_LONGSHOT, mSettingOff)) {
            ListPreference advancefeaturePref =
                    mPreferenceGroup.findPreference(CameraSettings.KEY_ADVANCED_FEATURES);
            if (advancefeaturePref != null) {
                if (notSame(advancefeaturePref, CameraSettings.KEY_ADVANCED_FEATURES,
                        mActivity.getString(R.string.pref_camera_advanced_feature_default))) {
                    RotateTextToast.makeText(mActivity, R.string.longshot_enable_message,
                            Toast.LENGTH_LONG).show();
                }
                setPreference(CameraSettings.KEY_ADVANCED_FEATURES,
                        mActivity.getString(R.string.pref_camera_advanced_feature_default));
            }
        } else if (notSame(pref, CameraSettings.KEY_ADVANCED_FEATURES,
                mActivity.getString(R.string.pref_camera_advanced_feature_default))) {
            ListPreference longshotPref =
                    mPreferenceGroup.findPreference(CameraSettings.KEY_LONGSHOT);
            if (longshotPref != null ) {
                if (notSame(longshotPref, CameraSettings.KEY_LONGSHOT, mSettingOff)) {
                    RotateTextToast.makeText(mActivity, R.string.advance_feature_enable_msg,
                            Toast.LENGTH_LONG).show();
                }
                setPreference(CameraSettings.KEY_LONGSHOT, mSettingOff);
            }
        }

        String refocusOn = mActivity.getString(R.string
                .pref_camera_advanced_feature_value_refocus_on);
        if (notSame(pref, CameraSettings.KEY_SCENE_MODE, refocusOn)) {
            ListPreference lp = mPreferenceGroup
                    .findPreference(CameraSettings.KEY_ADVANCED_FEATURES);
            if (lp != null && refocusOn.equals(lp.getValue())) {
                setPreference(CameraSettings.KEY_ADVANCED_FEATURES,
                        mActivity.getString(R.string.pref_camera_advanced_feature_default));
            }
        }

        String optizoomOn = mActivity.getString(R.string
                .pref_camera_advanced_feature_value_optizoom_on);
        if (notSame(pref, CameraSettings.KEY_SCENE_MODE, optizoomOn)) {
            ListPreference lp = mPreferenceGroup
                    .findPreference(CameraSettings.KEY_ADVANCED_FEATURES);
            if (lp != null && optizoomOn.equals(lp.getValue())) {
                setPreference(CameraSettings.KEY_ADVANCED_FEATURES,
                        mActivity.getString(R.string.pref_camera_advanced_feature_default));
            }
        }

        String chromaFlashOn = mActivity.getString(R.string.
                pref_camera_advanced_feature_value_chromaflash_on);
        if (notSame(pref, CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_AUTO)) {
            ListPreference lp = mPreferenceGroup
                    .findPreference(CameraSettings.KEY_ADVANCED_FEATURES);
            if (lp != null && chromaFlashOn.equals(lp.getValue())) {
                setPreference(CameraSettings.KEY_QC_CHROMA_FLASH, mSettingOff);
                setPreference(CameraSettings.KEY_ADVANCED_FEATURES,
                        mActivity.getString(R.string.pref_camera_advanced_feature_default));
            }
        }

        if (notSame(pref, CameraSettings.KEY_SCENE_MODE, "auto")) {
            setPreference(CameraSettings.KEY_COLOR_EFFECT,
                    mActivity.getString(R.string.pref_camera_coloreffect_default));
        }

        String stillMoreOn = mActivity.getString(R.string.
                pref_camera_advanced_feature_value_stillmore_on);
        if (same(pref, CameraSettings.KEY_ADVANCED_FEATURES, stillMoreOn)) {
           setPreference(CameraSettings.KEY_FLASH_MODE, Parameters.FLASH_MODE_OFF);
        }

        ListPreference autoHdrPref = mPreferenceGroup.findPreference(CameraSettings.KEY_AUTO_HDR);
        if (autoHdrPref != null && autoHdrPref.getValue().equalsIgnoreCase("enable")) {
            mHdrSwitcher.setVisibility(View.GONE);
            mUI.getCameraControls().removeFromViewList(mHdrSwitcher);
        } else {
            mHdrSwitcher.setVisibility(View.VISIBLE);
        }
        updateFilterModeIcon(pref, pref);

        if (same(pref, CameraSettings.KEY_RECORD_LOCATION, "on")) {
            mActivity.requestLocationPermission();
        }

        super.onSettingChanged(pref);
    }

    public int getOrientation() {
        return mUI == null ? 0 : mUI.getOrientation();
    }

    public void hideTopMenu(boolean hide) {
        if (hide) {
            mSceneModeSwitcher.setVisibility(View.GONE);
            mFilterModeSwitcher.setVisibility(View.GONE);
            mFrontBackSwitcher.setVisibility(View.GONE);
            mTsMakeupSwitcher.setVisibility(View.GONE);
        } else {
            mSceneModeSwitcher.setVisibility(View.VISIBLE);
            mFilterModeSwitcher.setVisibility(View.VISIBLE);
            mFrontBackSwitcher.setVisibility(View.VISIBLE);
            mTsMakeupSwitcher.setVisibility(View.VISIBLE);
        }
    }

    public void hideCameraControls(boolean hide) {
        final int status = (hide) ? View.INVISIBLE : View.VISIBLE;
        mSettingMenu.setVisibility(status);
        mFrontBackSwitcher.setVisibility(status);
        if (TsMakeupManager.HAS_TS_MAKEUP) {
            mTsMakeupSwitcher.setVisibility(status);
        } else {
            mHdrSwitcher.setVisibility(status);
        }
        mSceneModeSwitcher.setVisibility(status);
        mFilterModeSwitcher.setVisibility(status);
        if(status == View.INVISIBLE) {
            if(mCameraSwitcher.getVisibility() == View.VISIBLE) {
                mWasVisibleSet.add(mCameraSwitcher);
            }
            mCameraSwitcher.setVisibility(status);
        } else {
            if(mWasVisibleSet.contains(mCameraSwitcher)) {
                mCameraSwitcher.setVisibility(status);
                mWasVisibleSet.remove(mCameraSwitcher);
            }
        }
        mPreviewThumbnail.setVisibility(status);
    }
}
