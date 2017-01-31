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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.content.Context;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewPropertyAnimator;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;

import com.android.camera.ui.CameraControls;
import com.android.camera.ui.ListSubMenu;
import com.android.camera.ui.ListMenu;
import com.android.camera.ui.TimeIntervalPopup;
import com.android.camera.ui.RotateImageView;
import com.android.camera.ui.RotateTextToast;
import org.codeaurora.snapcam.R;
import android.widget.HorizontalScrollView;
import android.widget.Toast;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.Display;
import com.android.camera.ui.RotateLayout;
import com.android.camera.util.CameraUtil;
import android.text.TextUtils;
import java.util.Locale;

public class VideoMenu extends MenuController
        implements ListMenu.Listener,
        ListSubMenu.Listener,
        TimeIntervalPopup.Listener {

    private static String TAG = "VideoMenu";

    private VideoUI mUI;
    private String[] mOtherKeys1;
    private String[] mOtherKeys2;

    private ListMenu mListMenu;
    private ListSubMenu mListSubMenu;
    private View mPreviewMenu;
    private static final int POPUP_NONE = 0;
    private static final int POPUP_FIRST_LEVEL = 1;
    private static final int POPUP_SECOND_LEVEL = 2;
    private static final int POPUP_IN_ANIMATION_SLIDE = 3;
    private static final int POPUP_IN_ANIMATION_FADE = 4;
    private static final int PREVIEW_MENU_NONE = 0;
    private static final int PREVIEW_MENU_IN_ANIMATION = 1;
    private static final int PREVIEW_MENU_ON = 2;
    private static final int MODE_FILTER = 1;
    private int mSceneStatus;
    private View mFrontBackSwitcher;
    private View mFilterModeSwitcher;
    private int mPopupStatus;
    private int mPreviewMenuStatus;
    private CameraActivity mActivity;
    private String mPrevSavedVideoCDS;
    private boolean mIsVideoTNREnabled = false;
    private boolean mIsVideoCDSUpdated = false;
    private static final int ANIMATION_DURATION = 300;
    private static final int CLICK_THRESHOLD = 200;
    private int previewMenuSize;

    private static final boolean PERSIST_4K_NO_LIMIT =
            android.os.SystemProperties.getBoolean("persist.camcorder.4k.nolimit", false);

    public VideoMenu(CameraActivity activity, VideoUI ui) {
        super(activity);
        mUI = ui;
        mActivity = activity;
        mFrontBackSwitcher = ui.getRootView().findViewById(R.id.front_back_switcher);
        mFilterModeSwitcher = ui.getRootView().findViewById(R.id.filter_mode_switcher);
    }

    public void initialize(PreferenceGroup group) {
        super.initialize(group);
        mListMenu = null;
        mListSubMenu = null;
        mPopupStatus = POPUP_NONE;
        mPreviewMenuStatus = POPUP_NONE;
        initFilterModeButton(mFilterModeSwitcher);
        // settings popup
        mOtherKeys1 = new String[] {
                CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE,
                CameraSettings.KEY_VIDEO_QUALITY,
                CameraSettings.KEY_VIDEO_DURATION,
                CameraSettings.KEY_RECORD_LOCATION,
                CameraSettings.KEY_CAMERA_SAVEPATH,
                CameraSettings.KEY_WHITE_BALANCE,
                CameraSettings.KEY_VIDEO_HIGH_FRAME_RATE,
                CameraSettings.KEY_DIS
        };
        mOtherKeys2 = new String[] {
                CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE,
                CameraSettings.KEY_VIDEO_QUALITY,
                CameraSettings.KEY_VIDEO_DURATION,
                CameraSettings.KEY_RECORD_LOCATION,
                CameraSettings.KEY_CAMERA_SAVEPATH,
                CameraSettings.KEY_WHITE_BALANCE,
                CameraSettings.KEY_FACE_DETECTION,
                CameraSettings.KEY_VIDEO_HIGH_FRAME_RATE,
                CameraSettings.KEY_SEE_MORE,
                CameraSettings.KEY_NOISE_REDUCTION,
                CameraSettings.KEY_DIS,
                CameraSettings.KEY_VIDEO_EFFECT,
                CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL,
                CameraSettings.KEY_VIDEO_ENCODER,
                CameraSettings.KEY_AUDIO_ENCODER,
                CameraSettings.KEY_VIDEO_HDR,
                CameraSettings.KEY_POWER_MODE,
                CameraSettings.KEY_VIDEO_ROTATION,
                CameraSettings.KEY_VIDEO_CDS_MODE,
                CameraSettings.KEY_VIDEO_TNR_MODE,
                CameraSettings.KEY_VIDEO_SNAPSHOT_SIZE
        };
        initSwitchItem(CameraSettings.KEY_CAMERA_ID, mFrontBackSwitcher);
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
        vp.setDuration(ANIMATION_DURATION).start();
    }

    public void animateFadeIn(final ListView v) {
        ViewPropertyAnimator vp = v.animate();
        vp.alpha(0.85f).setDuration(ANIMATION_DURATION);
        vp.start();
    }

    public void animateSlideIn(final View v, int delta, boolean adjustOrientation) {
        int orientation = mUI.getOrientation();
        if (!adjustOrientation)
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
        vp.setDuration(ANIMATION_DURATION).start();
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

    public void initSwitchItem(final String prefKey, View switcher) {
        final IconListPreference pref =
                (IconListPreference) mPreferenceGroup.findPreference(prefKey);
        if (pref == null)
            return;

        int[] iconIds = pref.getLargeIconIds();
        int resid = -1;
        int index = pref.findIndexOfValue(pref.getValue());
        if (!pref.getUseSingleIcon() && iconIds != null) {
            if (index == -1) {
                return;
            }
            // Each entry has a corresponding icon.
            resid = iconIds[index];
        } else {
            // The preference only has a single icon to represent it.
            resid = pref.getSingleIcon();
        }
        ((ImageView) switcher).setImageResource(resid);
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
                ((ImageView) v).setImageResource(
                        ((IconListPreference) pref).getLargeIconIds()[index]);
                if (prefKey.equals(CameraSettings.KEY_CAMERA_ID))
                    mListener.onCameraPickerClicked(index);
                reloadPreference(pref);
                onSettingChanged(pref);
            }
        });
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
                addFilterMode();
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
        if (mSceneStatus == MODE_FILTER) {
            addFilterMode();
        }
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
                            for (View v1 : views) {
                                v1.setBackground(null);
                            }
                            ImageView image = (ImageView) v.findViewById(R.id.image);
                            image.setBackgroundColor(0xff33b5e5);
                            onSettingChanged(pref);
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
        if (isMenuBeingShown() || CameraControls.isAnimating())
            return;
        if (mListMenu == null || mPopupStatus != POPUP_FIRST_LEVEL) {
            initializePopup();
            mPopupStatus = POPUP_FIRST_LEVEL;
        }
        mUI.showPopup(mListMenu, 1, true);
    }

    public void setPreference(String key, String value) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        if (pref != null && !value.equals(pref.getValue())) {
            pref.setValue(value);
            reloadPreferences();
        }
    }

    private void overridePreferenceAccessibility() {
        overrideMenuForLocation();
        overrideMenuFor4K();
        overrideMenuForCDSMode();
        overrideMenuForSeeMore();
        overrideMenuForVideoHighFrameRate();
    }

    private void overrideMenuForLocation() {
        if (mActivity.isSecureCamera()) {
            // Prevent location preference from getting changed in secure camera
            // mode
            mListMenu.setPreferenceEnabled(CameraSettings.KEY_RECORD_LOCATION, false);
        }
    }
    private void overrideMenuFor4K() {
        if(mUI.is4KEnabled() && !PERSIST_4K_NO_LIMIT) {

            mListMenu.setPreferenceEnabled(
                     CameraSettings.KEY_DIS,false);
            mListMenu.overrideSettings(
                     CameraSettings.KEY_DIS, "disable");

            mListMenu.setPreferenceEnabled(
                    CameraSettings.KEY_SEE_MORE, false);
            mListMenu.overrideSettings(
                    CameraSettings.KEY_SEE_MORE, mActivity.getString(R.string.pref_camera_see_more_value_off));
        }
    }

    private void overrideMenuForSeeMore() {
        ListPreference pref_SeeMore = mPreferenceGroup.findPreference(CameraSettings.KEY_SEE_MORE);
        if(pref_SeeMore != null && pref_SeeMore.getValue() != null
                && pref_SeeMore.getValue().equals("on")) {
            mListMenu.setPreferenceEnabled(
                    CameraSettings.KEY_VIDEO_CDS_MODE,false);
            mListMenu.setPreferenceEnabled(
                    CameraSettings.KEY_VIDEO_TNR_MODE, false);
            mListMenu.setPreferenceEnabled(
                    CameraSettings.KEY_NOISE_REDUCTION, false);
            mListMenu.overrideSettings(
                    CameraSettings.KEY_VIDEO_CDS_MODE,
                    mActivity.getString(R.string.pref_camera_video_cds_value_off),
                    CameraSettings.KEY_VIDEO_TNR_MODE,
                    mActivity.getString(R.string.pref_camera_video_tnr_value_off),
                    CameraSettings.KEY_NOISE_REDUCTION,
                    mActivity.getString(R.string.pref_camera_noise_reduction_value_off));
        }
    }

    private void overrideMenuForCDSMode() {

        ListPreference pref_tnr = mPreferenceGroup.
                findPreference(CameraSettings.KEY_VIDEO_TNR_MODE);
        ListPreference pref_cds = mPreferenceGroup.
                findPreference(CameraSettings.KEY_VIDEO_CDS_MODE);
        String tnr = (pref_tnr != null) ? pref_tnr.getValue() : null;
        String cds = (pref_cds != null) ? pref_cds.getValue() : null;

        if (mPrevSavedVideoCDS == null && cds != null) {
            mPrevSavedVideoCDS = cds;
        }

        if ((tnr != null) && !tnr.equals("off")) { 
            mListMenu.setPreferenceEnabled(
                    CameraSettings.KEY_VIDEO_CDS_MODE,false);
            mListMenu.overrideSettings(
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
            mListMenu.setPreferenceEnabled(
                    CameraSettings.KEY_VIDEO_CDS_MODE,true);
            if (mIsVideoTNREnabled) {
                mListMenu.overrideSettings(
                        CameraSettings.KEY_VIDEO_CDS_MODE, mPrevSavedVideoCDS);
                mIsVideoTNREnabled = false;
                mIsVideoCDSUpdated = false;
            }
        }

    }

    private void overrideMenuForVideoHighFrameRate() {
        ListPreference disPref = mPreferenceGroup
                .findPreference(CameraSettings.KEY_DIS);
        ListPreference frameIntervalPref = mPreferenceGroup
                .findPreference(CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL);
        ListPreference videoHDRPref = mPreferenceGroup
                .findPreference(CameraSettings.KEY_VIDEO_HDR);
        String disMode = disPref.getValue();
        String videoHDR = videoHDRPref == null ? "off" : videoHDRPref.getValue();
        String frameIntervalStr = frameIntervalPref.getValue();
        int timeLapseInterval = Integer.parseInt(frameIntervalStr);
        int PERSIST_EIS_MAX_FPS =  android.os.SystemProperties
                .getInt("persist.camcorder.eis.maxfps", 30);
        ListPreference hfrPref = mPreferenceGroup
                .findPreference(CameraSettings.KEY_VIDEO_HIGH_FRAME_RATE);
        if (hfrPref != null) {
            String highFrameRate = hfrPref.getValue();
            boolean isHFR = "hfr".equals(highFrameRate.substring(0,3));
            boolean isHSR = "hsr".equals(highFrameRate.substring(0,3));
            int rate = 0;
            if ( isHFR || isHSR ) {
                String hfrRate = highFrameRate.substring(3);
                rate = Integer.parseInt(hfrRate);
            }

            if ((disMode.equals("enable") && rate > PERSIST_EIS_MAX_FPS)
                    || !videoHDR.equals("off")
                    || timeLapseInterval != 0) {
                mListMenu.setPreferenceEnabled(CameraSettings.KEY_VIDEO_HIGH_FRAME_RATE, false);
            }
        }

    }

    @Override
    public void overrideSettings(final String... keyvalues) {
        super.overrideSettings(keyvalues);
        if (mListMenu == null) {
            initializePopup();
        } else {
            overridePreferenceAccessibility();
        }
        mListMenu.overrideSettings(keyvalues);
    }

    @Override
    // Hit when an item in the second-level popup gets selected
    public void onListPrefChanged(ListPreference pref) {
        onSettingChanged(pref);
        closeView();
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

        mListMenu = popup1;

        overridePreferenceAccessibility();
        overrideMenuForVideoHighFrameRate();
    }

    public void popupDismissed(boolean topPopupOnly) {
        // if the 2nd level popup gets dismissed
        if (mPopupStatus == POPUP_SECOND_LEVEL) {
            initializePopup();
            mPopupStatus = POPUP_FIRST_LEVEL;
            if (topPopupOnly) {
                mUI.showPopup(mListMenu, 1, false);
            }
        } else {
            initializePopup();
        }
    }

    public void hideUI() {
        mFrontBackSwitcher.setVisibility(View.INVISIBLE);
        mFilterModeSwitcher.setVisibility(View.INVISIBLE);
    }

    public void showUI() {
        mFrontBackSwitcher.setVisibility(View.VISIBLE);
        final IconListPreference pref = (IconListPreference) mPreferenceGroup
                .findPreference(CameraSettings.KEY_FILTER_MODE);
        if (pref != null) {
            mFilterModeSwitcher.setVisibility(View.VISIBLE);
        }
    }

    @Override
    // Hit when an item in the first-level popup gets selected, then bring up
    // the second-level popup
    public void onPreferenceClicked(ListPreference pref) {
        onPreferenceClicked(pref, 0);
    }

    @Override
    // Hit when an item in the first-level popup gets selected, then bring up
    // the second-level popup
    public void onPreferenceClicked(ListPreference pref, int y) {
        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        ListSubMenu basic = (ListSubMenu) inflater.inflate(
                R.layout.list_sub_menu, null, false);
        basic.initialize(pref, y);
        basic.setSettingChangedListener(this);
        mUI.removeLevel2();
        mListSubMenu = basic;
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

    public void closeAllView() {
        if (mUI != null)
            mUI.removeLevel2();

        if (mListMenu != null)
            animateSlideOut(mListMenu, 1);
        animateSlideOutPreviewMenu();
    }

    public void closeView() {
        if (mUI != null)
            mUI.removeLevel2();

        if (mListMenu != null)
            animateSlideOut(mListMenu, 1);
    }

    // Return true if the preference has the specified key but not the value.
    private static boolean notSame(ListPreference pref, String key, String value) {
        return (key.equals(pref.getKey()) && !value.equals(pref.getValue()));
    }

    @Override
    public void onSettingChanged(ListPreference pref) {

        if (notSame(pref, CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL,
                mActivity.getString(R.string.pref_video_time_lapse_frame_interval_default))) {
            ListPreference hfrPref =
                    mPreferenceGroup.findPreference(CameraSettings.KEY_VIDEO_HIGH_FRAME_RATE);
            if (hfrPref != null && !"off".equals(hfrPref.getValue())) {
                RotateTextToast.makeText(mActivity, R.string.error_app_unsupported_hfr_selection,
                        Toast.LENGTH_LONG).show();
            }
            setPreference(CameraSettings.KEY_VIDEO_HIGH_FRAME_RATE, "off");
        }
        if (notSame(pref, CameraSettings.KEY_VIDEO_HIGH_FRAME_RATE, "off")) {
            String defaultValue =
                    mActivity.getString(R.string.pref_video_time_lapse_frame_interval_default);
            ListPreference lapsePref = mPreferenceGroup
                    .findPreference(CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL);
            if (lapsePref != null && !defaultValue.equals(lapsePref.getValue())) {
                RotateTextToast.makeText(mActivity, R.string.error_app_unsupported_hfr_selection,
                        Toast.LENGTH_LONG).show();
            }
            setPreference(CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL, defaultValue);
        }

        if (notSame(pref, CameraSettings.KEY_RECORD_LOCATION, "off")) {
            mActivity.requestLocationPermission();
        }


        super.onSettingChanged(pref);
    }

    public int getOrientation() {
        return mUI == null ? 0 : mUI.getOrientation();
    }
}
