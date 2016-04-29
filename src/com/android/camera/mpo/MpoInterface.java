/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 * Not a contribution.
 *
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

package com.android.camera.mpo;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import android.util.Log;

import com.android.camera.exif.ExifInterface;
import com.android.camera.util.CameraUtil;

public class MpoInterface {
    private static final String TAG = "MpoInterface";
    private static final String NULL_ARGUMENT_STRING = "Argument is null";

    // Index IFD
    public static final int TAG_MP_FORMAT_VERSION = ExifInterface.defineTag(
            MpoIfdData.TYPE_MP_INDEX_IFD + MpoIfdData.TYPE_MP_ATTRIB_IFD, (short) 0xB000);
    public static final int TAG_NUM_IMAGES = ExifInterface.defineTag(MpoIfdData.TYPE_MP_INDEX_IFD,
            (short) 0xB001);
    public static final int TAG_MP_ENTRY = ExifInterface.defineTag(MpoIfdData.TYPE_MP_INDEX_IFD,
            (short) 0xB002);
    public static final int TAG_IMAGE_UNIQUE_ID_LIST = ExifInterface.defineTag(
            MpoIfdData.TYPE_MP_INDEX_IFD, (short) 0xB003);
    public static final int TAG_NUM_CAPTURED_FRAMES = ExifInterface.defineTag(
            MpoIfdData.TYPE_MP_INDEX_IFD, (short) 0xB004);

    // Attrib IFD
    public static final int TAG_IMAGE_NUMBER = ExifInterface.defineTag(
            MpoIfdData.TYPE_MP_ATTRIB_IFD, (short) 0xB101);
    public static final int TAG_PAN_ORIENTATION = ExifInterface.defineTag(
            MpoIfdData.TYPE_MP_ATTRIB_IFD, (short) 0xB201);
    public static final int TAG_PAN_OVERLAP_H = ExifInterface.defineTag(
            MpoIfdData.TYPE_MP_ATTRIB_IFD, (short) 0xB202);
    public static final int TAG_PAN_OVERLAP_V = ExifInterface.defineTag(
            MpoIfdData.TYPE_MP_ATTRIB_IFD, (short) 0xB203);
    public static final int TAG_BASE_VIEWPOINT_NUM = ExifInterface.defineTag(
            MpoIfdData.TYPE_MP_ATTRIB_IFD, (short) 0xB204);
    public static final int TAG_CONVERGE_ANGLE = ExifInterface.defineTag(
            MpoIfdData.TYPE_MP_ATTRIB_IFD, (short) 0xB205);
    public static final int TAG_BASELINE_LEN = ExifInterface.defineTag(
            MpoIfdData.TYPE_MP_ATTRIB_IFD, (short) 0xB206);
    public static final int TAG_DIVERGE_ANGLE = ExifInterface.defineTag(
            MpoIfdData.TYPE_MP_ATTRIB_IFD, (short) 0xB207);
    public static final int TAG_AXIS_DISTANCE_X = ExifInterface.defineTag(
            MpoIfdData.TYPE_MP_ATTRIB_IFD, (short) 0xB208);
    public static final int TAG_AXIS_DISTANCE_Y = ExifInterface.defineTag(
            MpoIfdData.TYPE_MP_ATTRIB_IFD, (short) 0xB209);
    public static final int TAG_AXIS_DISTANCE_Z = ExifInterface.defineTag(
            MpoIfdData.TYPE_MP_ATTRIB_IFD, (short) 0xB20A);
    public static final int TAG_YAW_ANGLE = ExifInterface.defineTag(MpoIfdData.TYPE_MP_ATTRIB_IFD,
            (short) 0xB20B);
    public static final int TAG_PITCH_ANGLE = ExifInterface.defineTag(
            MpoIfdData.TYPE_MP_ATTRIB_IFD, (short) 0xB20C);
    public static final int TAG_ROLL_ANGLE = ExifInterface.defineTag(MpoIfdData.TYPE_MP_ATTRIB_IFD,
            (short) 0xB20D);

    public static int writeMpo(MpoData mpo, OutputStream out) {
        if (mpo == null || out == null)
            throw new IllegalArgumentException(NULL_ARGUMENT_STRING);

        MpoOutputStream s = getMpoWriterStream(out);
        s.setMpoData(mpo);

        // check and write mpo file
        try {
            s.writeMpoFile();
        } catch (IOException e) {
            CameraUtil.closeSilently(s);
            Log.w(TAG, "IO Exception when writing mpo image");
            return -1;
        }

        // close stream
        CameraUtil.closeSilently(s);
        return s.size();
    }

    public static int writeMpo(MpoData mpo, String outFilename) {
        if (mpo == null || outFilename == null)
            throw new IllegalArgumentException(NULL_ARGUMENT_STRING);

        return writeMpo(mpo, getFileWriterStream(outFilename));
    }

    /**
     * Wraps an OutputStream object with an MpoOutputStream.
     *
     * @param outStream
     *            an OutputStream to wrap.
     * @return an MpoOutputStream that wraps the outStream parameter, and adds
     *         mpo metadata. A jpeg image should be written to this stream.
     */
    private static MpoOutputStream getMpoWriterStream(OutputStream outStream) {
        if (outStream == null) {
            throw new IllegalArgumentException(NULL_ARGUMENT_STRING);
        }
        MpoOutputStream mos = new MpoOutputStream(outStream);
        return mos;
    }

    /**
     * Returns an FileOutputStream object that writes to a file.
     *
     * @param outFileName
     *            an String containing a filepath for a file.
     * @return an FileOutputStream that writes to the outFileName file.
     * @throws FileNotFoundException
     */
    private static OutputStream getFileWriterStream(String outFileName) {
        if (outFileName == null) {
            throw new IllegalArgumentException(NULL_ARGUMENT_STRING);
        }
        OutputStream out = null;
        try {
            out = new FileOutputStream(outFileName);
        } catch (FileNotFoundException e) {
            CameraUtil.closeSilently(out);
            Log.w(TAG, "File not found");
        }
        return out;
    }
}
