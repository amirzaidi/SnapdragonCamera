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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import org.codeaurora.snapcam.R;

import com.android.camera.ui.DotsView;
import com.android.camera.ui.DotsViewItem;
import com.android.camera.ui.RotateImageView;
import com.android.camera.util.CameraUtil;

public class SceneModeActivity extends Activity {
    private ViewPager mPager;
    private View mCloseButton;
    private RotateImageView mButton;
    private DotsView mDotsView;
    private MyPagerAdapter mAdapter;
    private SettingsManager mSettingsManager;
    private CharSequence[] mEntries;
    private int[] mThumbnails;
    private int mCurrentScene;
    private int mNumElement;
    private int mElemPerPage = 12;
    private int mNumPage;

    private static class PageItems implements DotsViewItem {
        int number;

        public PageItems(int number) {
            this.number = number;
        }

        @Override
        public int getTotalItemNums() {
            return number;
        }

        @Override
        public boolean isChosen(int index) {
            return true;
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final boolean isSecureCamera = getIntent().getBooleanExtra(
                CameraUtil.KEY_IS_SECURE_CAMERA, false);
        if (isSecureCamera) {
            setShowInLockScreen();
        }
        setContentView(R.layout.scene_mode_menu_layout);
        mSettingsManager = SettingsManager.getInstance();


        mCurrentScene = mSettingsManager.getValueIndex(SettingsManager.KEY_SCENE_MODE);

        mEntries = mSettingsManager.getEntries(SettingsManager.KEY_SCENE_MODE);

        mThumbnails = mSettingsManager.getResource(SettingsManager.KEY_SCENE_MODE,
                SettingsManager.RESOURCE_TYPE_THUMBNAIL);

        mNumElement = mThumbnails.length;
        int pages = mNumElement / mElemPerPage;
        if (mNumElement % mElemPerPage != 0) pages++;
        mNumPage = pages;

        mAdapter = new MyPagerAdapter(this);

        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setOverScrollMode(ViewPager.OVER_SCROLL_NEVER);
        mPager.setAdapter(mAdapter);

        mCloseButton = findViewById(R.id.close_button);
        mCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        int pageCount = mAdapter.getCount();
        mDotsView = (DotsView) findViewById(R.id.page_indicator);
        mPager.setCurrentItem(mCurrentScene / mElemPerPage);
        mDotsView.update(mCurrentScene / mElemPerPage, 0f);
        if (pageCount > 1) {
            mDotsView.setItems(new PageItems(pageCount));
            mPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                    mDotsView.update(position, positionOffset);
                }
            });
        } else {
            mDotsView.setVisibility(View.GONE);
        }

        mButton = (RotateImageView) findViewById(R.id.setting_button);
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getBaseContext(), SettingsActivity.class);
                intent.putExtra(CameraUtil.KEY_IS_SECURE_CAMERA, isSecureCamera);
                startActivity(intent);
                finish();
            }
        });
    }

    public int getElmentPerPage() {
        return mElemPerPage;
    }

    public int getNumberOfPage() {
        return mNumPage;
    }

    public int getNumberOfElement() {
        return mNumElement;
    }

    public int getCurrentPage() {
        return mPager.getCurrentItem();
    }

    public CharSequence[] getEntries() {
        return mEntries;
    }


    public int[] getThumbnails() {
        return mThumbnails;
    }

    public int getCurrentScene() {
        return mCurrentScene;
    }

    private void setShowInLockScreen() {
        // Change the window flags so that secure camera can show when locked
        Window win = getWindow();
        WindowManager.LayoutParams params = win.getAttributes();
        params.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        win.setAttributes(params);
    }

    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }
}

class MyPagerAdapter extends PagerAdapter {

    private SceneModeActivity mActivity;
    private ViewGroup mRootView;

    public MyPagerAdapter(SceneModeActivity activity) {
        mActivity = activity;
    }

    public Object instantiateItem(ViewGroup viewGroup, int i) {
        mRootView = (ViewGroup) mActivity.getLayoutInflater().inflate(R.layout.scene_mode_grid, null);
        GridView mGridView = (GridView) mRootView.findViewById(R.id.grid);
        mGridView.setAdapter(new GridAdapter(mActivity, i));
        viewGroup.addView(mRootView);

        mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int page = mActivity.getCurrentPage();
                int index = page * mActivity.getElmentPerPage() + position;
                for (int i = 0; i < parent.getChildCount(); i++) {
                    View v = parent.getChildAt(i);
                    if (v != null) {
                        v.setBackground(null);
                    }
                }
                view.setBackgroundResource(R.drawable.scene_mode_view_border_selected);
                SettingsManager.getInstance().setValueIndex(SettingsManager.KEY_SCENE_MODE, index);
                mActivity.finish();
            }
        });
        return mRootView;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
    }

    @Override
    public int getCount() {
        return mActivity.getNumberOfPage();
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == object;
    }

}

class GridAdapter extends BaseAdapter {
    private SceneModeActivity mActivity;
    private LayoutInflater mInflater;
    private int mPage;

    public GridAdapter(SceneModeActivity activity, int i) {
        mActivity = activity;
        mInflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPage = i;
    }

    @Override
    public int getCount() {
        int elem = mActivity.getElmentPerPage();
        if (mPage == mActivity.getNumberOfPage() - 1) {
            elem = mActivity.getNumberOfElement() - mPage * elem;
        }
        return elem;
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        ViewHolder viewHolder;

        if (view == null) {
            viewHolder = new ViewHolder();
            view = mInflater.inflate(R.layout.scene_mode_menu_view, parent, false);
            viewHolder.imageView = (ImageView) view.findViewById(R.id.image);
            viewHolder.textTitle = (TextView) view.findViewById(R.id.label);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }
        int idx = position + mPage * mActivity.getElmentPerPage();
        viewHolder.imageView.setImageResource(mActivity.getThumbnails()[idx]);
        viewHolder.textTitle.setText(mActivity.getEntries()[position + mPage * mActivity.getElmentPerPage()]);
        if (idx == mActivity.getCurrentScene()) {
            view.setBackgroundResource(R.drawable.scene_mode_view_border_selected);
        }

        return view;
    }

    private class ViewHolder {
        public ImageView imageView;
        public TextView textTitle;
    }
}
