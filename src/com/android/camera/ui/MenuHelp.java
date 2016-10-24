/*
Copyright (c) 2016, The Linux Foundation. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.android.camera.ui;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;
import java.util.ArrayList;

import org.codeaurora.snapcam.R;
import com.android.camera.ui.ModuleSwitcher;
import com.android.camera.ui.RotateImageView;
import com.android.camera.ShutterButton;
import com.android.camera.Storage;
import com.android.camera.util.CameraUtil;
import com.android.camera.TsMakeupManager;

public class MenuHelp extends RotatableLayout {

    private static final String TAG = "MenuHelp";
    private View mBackgroundView;
    private Arrows mArrows;
    private static int mTopMargin = 0;
    private static int mBottomMargin = 0;
    private static final int HELP_0_0_INDEX = 0;
    private static final int HELP_1_0_INDEX = 1;
    private static final int HELP_3_0_INDEX = 2;
    private static final int HELP_4_6_INDEX = 3;
    private static final int OK_2_4_INDEX = 4;
    private static final int MAX_INDEX = 5;
    private float[][] mLocX = new float[4][MAX_INDEX];
    private float[][] mLocY = new float[4][MAX_INDEX];
    private RotateLayout mHelp0_0;
    private RotateLayout mHelp1_0;
    private RotateLayout mHelp3_0;
    private RotateLayout mHelp4_6;
    private RotateLayout mOk2_4;
    private Context mContext;
    private int mOrientation;
    private final static int POINT_MARGIN = 50;
    private static final int WIDTH_GRID = 5;
    private static final int HEIGHT_GRID = 7;
    private Typeface mTypeface;
    private boolean forCamera2 = false;

    public MenuHelp(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mTypeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL);
    }

    public MenuHelp(Context context) {
        this(context, null);
    }

    public void setForCamera2(boolean forCamera2) {
        this.forCamera2 = forCamera2;
    }

    public void setMargins(int top, int bottom) {
        mTopMargin = top;
        mBottomMargin = bottom;
    }

    @Override
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        r = r - l;
        b = b - t;
        l = 0;
        t = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            v.layout(l, t, r, b);
        }
        setLocation(r - l, b - t);
    }

    private void setLocation(int w, int h) {
        int rotation = getUnifiedRotation();
        toIndex(mHelp0_0, w, h, rotation, 1, 3, HELP_0_0_INDEX);
        toIndex(mHelp1_0, w, h, rotation, 2, 2, HELP_1_0_INDEX);
        if(TsMakeupManager.HAS_TS_MAKEUP)
            toIndex(mHelp3_0, w, h, rotation, 3, 1, HELP_3_0_INDEX);
        if (!forCamera2) {
            toIndex(mHelp4_6, w, h, rotation, 3, 4, HELP_4_6_INDEX);
        } else {
            mHelp4_6.setVisibility(View.GONE);
        }
        toIndex(mOk2_4, w, h, rotation, 1, 5, OK_2_4_INDEX);
        fillArrows(w, h, rotation);
    }

    private void fillArrows(int w, int h, int rotation) {
        View v1 = new View(mContext);
        View v2 = new View(mContext);
        View v3 = new View(mContext);
        {
            toIndex(v1, w, h, rotation, 1, 3, -1);
            toIndex(v2, w, h, rotation, 0, 1, -1);
            toIndex(v3, w, h, rotation, 0, 0, -1);
            float[] x = {v1.getX()-POINT_MARGIN, v2.getX(), v3.getX()};
            float[] y = {v1.getY()-POINT_MARGIN, v2.getY(), v3.getY()+POINT_MARGIN};
            mArrows.addPath(x, y);
        }

        {
            toIndex(v1, w, h, rotation, 2, 2, -1);
            toIndex(v2, w, h, rotation, 1, 1, -1);
            toIndex(v3, w, h, rotation, 1, 0, -1);
            float[] x = {v1.getX()-POINT_MARGIN, v2.getX(), v3.getX()};
            float[] y = {v1.getY()-POINT_MARGIN, v2.getY(), v3.getY()+POINT_MARGIN};
            mArrows.addPath(x, y);
        }

        if(TsMakeupManager.HAS_TS_MAKEUP) {
            toIndex(v1, w, h, rotation, 3, 1, -1);
            toIndex(v2, w, h, rotation, 3, 0, -1);
            float[] x = {v1.getX(), v2.getX()};
            float[] y = {v1.getY()-POINT_MARGIN*2, v2.getY()+POINT_MARGIN};
            mArrows.addPath(x, y);
        }

        if (!forCamera2) {
            toIndex(v1, w, h, rotation, 3, 4, -1);
            toIndex(v2, w, h, rotation, 3, 5, -1);
            toIndex(v3, w, h, rotation, 4, 6, -1);
            float[] x = {v1.getX(), v2.getX(), v3.getX()};
            float[] y = {v1.getY()+POINT_MARGIN, v2.getY(), v3.getY()-POINT_MARGIN};
            mArrows.addPath(x, y);
        }
    }

    private void toIndex(View v, int w, int h, int rotation, int index, int index2, int index3) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        int tw = v.getMeasuredWidth();
        int th = v.getMeasuredHeight();
        int l = 0, r = 0, t = 0, b = 0;

        int wnumber = WIDTH_GRID;
        int hnumber = HEIGHT_GRID;
        int windex = 0;
        int hindex = 0;
        switch (rotation) {
            case 0:
                // portrait, to left of anchor at bottom
                wnumber = WIDTH_GRID;
                hnumber = HEIGHT_GRID;
                windex = index;
                hindex = index2;
                break;
            case 90:
                // phone landscape: below anchor on right
                wnumber = HEIGHT_GRID;
                hnumber = WIDTH_GRID;
                windex = index2;
                hindex = hnumber - index - 1;
                break;
            case 180:
                // phone upside down: right of anchor at top
                wnumber = WIDTH_GRID;
                hnumber = HEIGHT_GRID;
                windex = wnumber - index - 1;
                hindex = hnumber - index2 - 1;
                break;
            case 270:
                // reverse landscape: above anchor on left
                wnumber = HEIGHT_GRID;
                hnumber = WIDTH_GRID;
                windex = wnumber - index2 - 1;
                hindex = index;
                break;
        }
        int boxh = h / hnumber;
        int boxw = w / wnumber;
        int cx = (2 * windex + 1) * boxw / 2;
        int cy = (2 * hindex + 1) * boxh / 2;

        if (index2 == 0 && mTopMargin != 0) {
            switch (rotation) {
                case 90:
                    cx = mTopMargin / 2;
                    break;
                case 180:
                    cy = h - mTopMargin / 2;
                    break;
                case 270:
                    cx = w - mTopMargin / 2;
                    break;
                default:
                    cy = mTopMargin / 2;
                    break;
            }
        }

        l = cx - tw / 2;
        r = cx + tw / 2;
        t = cy - th / 2;
        b = cy + th / 2;

        if (index3 != -1) {
            int idx1 = rotation / 90;
            int idx2 = index3;
            mLocX[idx1][idx2] = l;
            mLocY[idx1][idx2] = t;
        }
        v.layout(l, t, r, b);
    }

    public void setOrientation(int orientation, boolean animation) {
        mOrientation = orientation;
        RotateLayout[] layouts = {
                mHelp0_0, mHelp1_0, mHelp3_0, mHelp4_6, mOk2_4
        };
        for (RotateLayout l : layouts) {
            l.setOrientation(orientation, animation);
        }
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        mBackgroundView = findViewById(R.id.background);
        mBackgroundView.setBackgroundColor(Color.argb(200, 0, 0, 0));
        mHelp0_0 = (RotateLayout)findViewById(R.id.help_text_0_0);
        fillHelp0_0();
        mHelp1_0 = (RotateLayout)findViewById(R.id.help_text_1_0);
        fillHelp1_0();
        mHelp3_0 = (RotateLayout) findViewById(R.id.help_text_3_0);
        fillHelp3_0();
        mHelp4_6 = (RotateLayout)findViewById(R.id.help_text_4_6);
        fillHelp4_6();
        mOk2_4 = (RotateLayout)findViewById(R.id.help_ok_2_4);
        fillOk2_4();
        mArrows = (Arrows)findViewById(R.id.arrows);
    }

    private void fillOk2_4() {
        LinearLayout linearLayout = new LinearLayout(mContext);
        mOk2_4.addView(linearLayout);
        linearLayout.setGravity(Gravity.CENTER);
        linearLayout.setPadding(40, 20, 40, 20);
        linearLayout.setBackgroundColor(Color.WHITE);
        TextView text1 = new TextView(mContext);
        text1.setText(getResources().getString(R.string.help_menu_ok));
        text1.setTextColor(Color.BLACK);
        text1.setTypeface(mTypeface);
        linearLayout.addView(text1);
    }

    private void fillHelp0_0() {
        TableLayout tableLayout = new TableLayout(mContext);
        mHelp0_0.addView(tableLayout);
        LinearLayout linearLayout = new LinearLayout(mContext);
        TextView text1 = new TextView(mContext);
        text1.setTextColor(getResources().getColor(R.color.help_menu_scene_mode_1));
        text1.setText(getResources().getString(R.string.help_menu_scene_mode_1)+" ");
        text1.setTypeface(mTypeface);
        linearLayout.addView(text1);
        TextView text2 = new TextView(mContext);
        text2.setText(getResources().getString(R.string.help_menu_scene_mode_2));
        text2.setTypeface(mTypeface);
        linearLayout.addView(text2);
        text2.setTextColor(getResources().getColor(R.color.help_menu_scene_mode_2));
        tableLayout.addView(linearLayout);
        TextView text3 = new TextView(mContext);
        text3.setText(getResources().getString(R.string.help_menu_scene_mode_3));
        text3.setTextColor(getResources().getColor(R.color.help_menu_scene_mode_3));
        text3.setTypeface(mTypeface);
        tableLayout.addView(text3);
    }

    private void fillHelp1_0() {
        TableLayout tableLayout = new TableLayout(mContext);
        mHelp1_0.addView(tableLayout);
        LinearLayout linearLayout = new LinearLayout(mContext);
        TextView text1 = new TextView(mContext);
        text1.setText(getResources().getString(R.string.help_menu_color_filter_1)+" ");
        text1.setTextColor(getResources().getColor(R.color.help_menu_color_filter_1));
        text1.setTypeface(mTypeface);
        linearLayout.addView(text1);
        TextView text2 = new TextView(mContext);
        text2.setText(getResources().getString(R.string.help_menu_color_filter_2)+" ");
        text2.setTextColor(getResources().getColor(R.color.help_menu_color_filter_2));
        text2.setTypeface(mTypeface);
        linearLayout.addView(text2);
        TextView text3 = new TextView(mContext);
        text3.setText(getResources().getString(R.string.help_menu_color_filter_3));
        text3.setTextColor(getResources().getColor(R.color.help_menu_color_filter_3));
        text3.setTypeface(mTypeface);
        linearLayout.addView(text3);
        tableLayout.addView(linearLayout);
        TextView text4 = new TextView(mContext);
        text4.setText(getResources().getString(R.string.help_menu_color_filter_4));
        text4.setTextColor(getResources().getColor(R.color.help_menu_color_filter_4));
        text4.setTypeface(mTypeface);
        tableLayout.addView(text4);
    }

    private void fillHelp3_0() {
        TableLayout tableLayout = new TableLayout(mContext);
        mHelp3_0.addView(tableLayout);
        if(TsMakeupManager.HAS_TS_MAKEUP) {
            TextView text1 = new TextView(mContext);
            text1.setText(getResources().getString(R.string.help_menu_beautify_1));
            text1.setTextColor(getResources().getColor(R.color.help_menu_beautify_1));
            text1.setTypeface(mTypeface);
            tableLayout.addView(text1);
            TextView text2 = new TextView(mContext);
            text2.setText(getResources().getString(R.string.help_menu_beautify_2));
            text2.setTextColor(getResources().getColor(R.color.help_menu_beautify_2));
            text2.setTypeface(mTypeface);
            tableLayout.addView(text2);
            TextView text3 = new TextView(mContext);
            text3.setText(getResources().getString(R.string.help_menu_beautify_3));
            text3.setTextColor(getResources().getColor(R.color.help_menu_beautify_3));
            text3.setTypeface(mTypeface);
            tableLayout.addView(text3);
        }
    }

    private void fillHelp4_6() {
        TableLayout tableLayout = new TableLayout(mContext);
        mHelp4_6.addView(tableLayout);
        LinearLayout linearLayout = new LinearLayout(mContext);
        TextView text1 = new TextView(mContext);
        text1.setText(getResources().getString(R.string.help_menu_switcher_1)+" ");
        text1.setTextColor(Color.GREEN);
        text1.setTypeface(mTypeface);
        linearLayout.addView(text1);
        TextView text2 = new TextView(mContext);
        text2.setText(getResources().getString(R.string.help_menu_switcher_2));
        text2.setTextColor(Color.WHITE);
        text2.setTypeface(mTypeface);
        linearLayout.addView(text2);
        tableLayout.addView(linearLayout);
        TextView text3 = new TextView(mContext);
        text3.setText(getResources().getString(R.string.help_menu_switcher_3));
        text3.setTextColor(Color.WHITE);
        text3.setTypeface(mTypeface);
        tableLayout.addView(text3);
    }
}
