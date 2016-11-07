/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.camera.app;

import android.app.ActivityManager;
import android.app.Application;

import com.android.camera.SDCard;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.UsageStatistics;
import com.android.camera.SettingsManager;

public class CameraApp extends Application {
    private static long mMaxSystemMemory;
    public static boolean mIsLowMemoryDevice = false;
    private static final long LOW_MEMORY_DEVICE_THRESHOLD = 2L*1024*1024*1024;
    @Override
    public void onCreate() {
        super.onCreate();
        ActivityManager actManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        actManager.getMemoryInfo(memInfo);
        mMaxSystemMemory = memInfo.totalMem;
        if(mMaxSystemMemory <= LOW_MEMORY_DEVICE_THRESHOLD) {
            mIsLowMemoryDevice = true;
        }
        SettingsManager.createInstance(this);
        UsageStatistics.initialize(this);
        CameraUtil.initialize(this);
        SDCard.initialize(this);
    }
}

