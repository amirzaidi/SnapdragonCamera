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
package com.android.camera.util;

import android.os.SystemProperties;

public class PersistUtil {

    private static final String PERSIST_MEMORY_LIMIT = "persist.camera.perf.memlimit";
    private static final String PERSIST_SKIP_MEMORY_CHECK = "persist.camera.perf.skip_memck";
    private static final String PERSIST_LONGSHOT_SHOT_LIMIT = "persist.camera.longshot.shotnum";
    private static final String PERSIST_CAMERA_PREVIEW_SIZE = "persist.camera.preview.size";

    public static int getMemoryLimit() {
        return SystemProperties.getInt(PERSIST_MEMORY_LIMIT, 60);
    }

    public static boolean getSkipMemoryCheck() {
        return SystemProperties.getBoolean(PERSIST_SKIP_MEMORY_CHECK, false);
    }

    public static int getLongshotShotLimit() {
        return SystemProperties.getInt(PERSIST_LONGSHOT_SHOT_LIMIT, 20);
    }

    public static int getCameraPreviewSize() {
        return SystemProperties.getInt(PERSIST_CAMERA_PREVIEW_SIZE, 0);
    }
}
