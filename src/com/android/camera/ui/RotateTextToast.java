/*
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

import java.util.HashSet;

import android.app.Activity;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.android.camera.util.CameraUtil;
import org.codeaurora.snapcam.R;

public class RotateTextToast {
    private static final int LONG_DELAY = 3500;
    private static final int SHORT_DELAY = 2000;

    private ViewGroup mLayoutRoot;
    private RotateLayout mToast;
    private Handler mHandler;
    private int mDuration;

    private static HashSet<RotateLayout> mToasts = new HashSet<RotateLayout>();
    private static int mOrientation;

    private RotateTextToast(Activity activity, int duration) {
        mLayoutRoot = (ViewGroup) activity.getWindow().getDecorView();
        LayoutInflater inflater = activity.getLayoutInflater();
        View v = inflater.inflate(R.layout.rotate_text_toast, mLayoutRoot);
        mToast = (RotateLayout) v.findViewById(R.id.rotate_toast);
        mToast.setOrientation(mOrientation, false);
        mHandler = new Handler();
        mDuration = duration == Toast.LENGTH_LONG ? LONG_DELAY : SHORT_DELAY;
    }

    public RotateTextToast(Activity activity, CharSequence text, int duration) {
        this(activity, duration);
        TextView tv = (TextView) mToast.findViewById(R.id.message);
        tv.setText(text);
    }

    public RotateTextToast(Activity activity, int textResourceId, int duration) {
        this(activity, duration);
        TextView tv = (TextView) mToast.findViewById(R.id.message);
        tv.setText(textResourceId);
    }

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            CameraUtil.fadeOut(mToast);
            mLayoutRoot.removeView(mToast);
            mToasts.remove(mToast);
            mToast = null;
        }
    };

    public void show() {
        mToasts.add(mToast);
        mToast.setVisibility(View.VISIBLE);
        mHandler.postDelayed(mRunnable, mDuration);
    }

    public static RotateTextToast makeText(Activity activity, int textResourceId, int duration) {
        return new RotateTextToast(activity, textResourceId, duration);
    }

    public static RotateTextToast makeText(Activity activity, CharSequence text, int duration) {
        return new RotateTextToast(activity, text, duration);
    }

    public static void setOrientation(int orientation) {
        mOrientation = orientation;
        for (final RotateLayout toast: mToasts) {
            toast.setOrientation(orientation, false);
        }
    }
}
