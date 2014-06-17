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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.content.Context;
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
import org.codeaurora.snapcam.R;
import android.widget.HorizontalScrollView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.Display;
import com.android.camera.util.CameraUtil;

public class CustomVideoMenu extends MenuController
        implements ListMenu.Listener,
        ListSubMenu.Listener,
        TimeIntervalPopup.Listener {

    private static String TAG = "CustomVideoMenu";

    private VideoUI mUI;
    private String[] mOtherKeys1;
    private String[] mOtherKeys2;

    private ListMenu mListMenu;
    private ListSubMenu mListSubMenu;
    private View mPreviewMenu;
    private static final int POPUP_NONE = 0;
    private static final int POPUP_FIRST_LEVEL = 1;
    private static final int POPUP_SECOND_LEVEL = 2;
    private static final int POPUP_IN_ANIMATION = 3;
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
    private static final int ANIMATION_DURATION = 300;
    private static final int CLICK_THRESHOLD = 200;
    private int previewMenuSize;

    public CustomVideoMenu(CameraActivity activity, VideoUI ui) {
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
                CameraSettings.KEY_VIDEO_QUALITY,
                CameraSettings.KEY_VIDEO_DURATION,
                CameraSettings.KEY_RECORD_LOCATION,
                CameraSettings.KEY_CAMERA_SAVEPATH,
                CameraSettings.KEY_COLOR_EFFECT,
                CameraSettings.KEY_WHITE_BALANCE,
                CameraSettings.KEY_VIDEO_HIGH_FRAME_RATE,
                CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE
        };
        mOtherKeys2 = new String[] {
                CameraSettings.KEY_VIDEO_QUALITY,
                CameraSettings.KEY_VIDEO_DURATION,
                CameraSettings.KEY_RECORD_LOCATION,
                CameraSettings.KEY_CAMERA_SAVEPATH,
                CameraSettings.KEY_COLOR_EFFECT,
                CameraSettings.KEY_WHITE_BALANCE,
                CameraSettings.KEY_VIDEO_HIGH_FRAME_RATE,
                CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE,
                CameraSettings.KEY_DIS,
                CameraSettings.KEY_VIDEO_EFFECT,
                CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL,
                CameraSettings.KEY_VIDEO_ENCODER,
                CameraSettings.KEY_AUDIO_ENCODER,
                CameraSettings.KEY_VIDEO_HDR,
                CameraSettings.KEY_POWER_MODE
        };
        mFrontBackSwitcher.setVisibility(View.INVISIBLE);
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

    public void openFirstLevel() {
        if (isMenuBeingShown() || CameraControls.isAnimating())
            return;
        if (mListMenu == null || mPopupStatus != POPUP_FIRST_LEVEL) {
            initializePopup();
            mPopupStatus = POPUP_FIRST_LEVEL;
        }
        mUI.showPopup(mListMenu, 1, true);
    }

    @Override
    public void overrideSettings(final String... keyvalues) {
        super.overrideSettings(keyvalues);
        if (((mListMenu == null)) || mPopupStatus != POPUP_FIRST_LEVEL) {
            mPopupStatus = POPUP_FIRST_LEVEL;
            initializePopup();
        }
        mListMenu.overrideSettings(keyvalues);

    }

    @Override
    // Hit when an item in the second-level popup gets selected
    public void onListPrefChanged(ListPreference pref) {
        if (mPopupStatus == POPUP_SECOND_LEVEL) {
            mListMenu.reloadPreference();
            animateFadeOut(mListSubMenu, 2);
        }
        super.onSettingChanged(pref);
        ((ListMenu) mListMenu).resetHighlight();
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
        mFilterModeSwitcher.setVisibility(View.VISIBLE);
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

    @Override
    public void onSettingChanged(ListPreference pref) {
        super.onSettingChanged(pref);
    }

}
