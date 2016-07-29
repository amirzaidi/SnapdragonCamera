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
package com.android.camera.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

public class IntentHelper {

    private static final String GALLERY_PACKAGE_NAME = "com.android.gallery3d";
    private static final String SNAPDRAGON_GALLERY_PACKAGE_NAME = "org.codeaurora.gallery";
    private static final String GALLERY_ACTIVITY_CLASS =
            "com.android.gallery3d.app.GalleryActivity";

    public static Intent getGalleryIntent(Context context) {
        String packageName = packageExist(context, SNAPDRAGON_GALLERY_PACKAGE_NAME) ?
                SNAPDRAGON_GALLERY_PACKAGE_NAME : GALLERY_PACKAGE_NAME;
        return new Intent(Intent.ACTION_MAIN)
                .setClassName(packageName, GALLERY_ACTIVITY_CLASS);
    }

    public static Intent getVideoPlayerIntent(Context context, Uri uri) {
        return new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "video/*");
    }

    private static boolean packageExist(Context context, String packageName) {
        if (packageName == null || "".equals(packageName)) {
            return false;
        }
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
