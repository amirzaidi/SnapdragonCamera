/*
 * Copyright (c) 2017, The Linux Foundation. All rights reserved.
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
package org.codeaurora.snapcam.filter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.hardware.Camera.Size;
import android.util.Base64;
import android.util.Log;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;

import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMeta;
import com.adobe.xmp.XMPMetaFactory;

public class GDepth{
    private final static String TAG = "Flow_GDepth";
    public final static String NAMESPACE_URL = "http://ns.google.com/photos/1.0/depthmap/";
    public final static String PREFIX = "GDepth";
    public final static String PROPERTY_FORMAT = "Format";
    public final static String PROPERTY_NEAR = "Near";
    public final static String PROPERTY_FAR = "Far";
    public final static String PROPERTY_MIME = "Mime";
    public final static String PROPERTY_DATA = "Data";
    //extend roi
    public final static String PROPERTY_ROI_X = "RoiX";
    public final static String PROPERTY_ROI_Y = "RoiY";
    public final static String PROPERTY_ROI_WIDTH = "RoiWidth";
    public final static String PROPERTY_ROI_HEIGHT = "RoiHeight";

    public final static String FORMAT_RANGE_INVERSE="RangeInverse";
    public final static String FORMAT_RANGLE_LINEAR = "RangeLinear";
    private final static String MIME = "image/jpeg";

    private DepthMap mDepthMap;
    private String mData;
    private int mNear;
    private int mFar;
    private final String mFormat = "RangeLinear";
    private int[] mMap;

    static {
        try {
            XMPMetaFactory.getSchemaRegistry().registerNamespace(
                    NAMESPACE_URL, PREFIX);
        } catch (XMPException e) {
            e.printStackTrace();
        }
    }

    private GDepth(DepthMap depthMap){
        mDepthMap = depthMap;
        mMap = new int[depthMap.buffer.length];

        for( int i=0; i < mMap.length; ++i ) {
            mMap[i] = (256+depthMap.buffer[i])%256;
        }
        mNear = mFar = mMap[0];
        for(int d : mMap ) {
            if ( d < mNear) {
                mNear = d;
            }else if ( d > mFar) {
                mFar = d;
            }
        }
    }

    public int getNear() {
        return mNear;
    }

    public int getFar(){
        return mFar;
    }

    public String getFormat(){
        return mFormat;
    }

    public String getMime() {
        return MIME;
    }

    public String getData(){
        return mData;
    }

    public Rect getRoi() {
        return mDepthMap.roi;
    }
    public static GDepth createGDepth(DepthMap depthMap){
        GDepth gDepth = new GDepth(depthMap);
        if (  gDepth.encoding() ) {
            return gDepth;
        }
        return null;
    }

    private  boolean encoding(){
        Log.d(TAG, "encoding");
        boolean result = false;
        int[]  grayscaleImage = convertIntoImage(mMap);
        byte[] jpegBytes = compressToJPEG(grayscaleImage );
        if (jpegBytes != null ) {
            String base64String = serializeAsBase64Str(jpegBytes);
            result = true;
            mData = base64String;
        }else{
            Log.e(TAG, "compressToJPEG failure");
        }

        return result;
    }

    private  int[] convertIntoImage(int[] depthMap) {
        int[] imageBuffer = new int[depthMap.length];
        float dividend = mFar-mNear;
        for ( int i =0; i < imageBuffer.length; ++i) {
            if ( depthMap[i] == 0 && mNear == 0 ) {
                imageBuffer[i] = 0;
            }else{
                imageBuffer[i] = getRangeLinearDepth(depthMap[i], mNear, dividend);
            }
        }
        return imageBuffer;
    }

    private   int getRangeLinearDepth(int depth, int near, float dividend) {
        return (int)(255*(depth-near)/dividend);
    }

    private   int getRangeLinearDepth(int depth, int near, int far) {
        return (int)(255*(depth-near)/(float)(far-near));
    }

    private  byte[] compressToJPEG(int[] image) {
        Log.d(TAG, "compressToJPEG int[].size=" + image.length);
        byte[] bitsBuffer = new byte[image.length];
        for(int i=0; i < image.length; ++i){
            bitsBuffer[i] = (byte)image[i];
        }
        return compressToJPEG(bitsBuffer);
    }

    private  byte[] compressToJPEG(byte[] image) {
        Log.d(TAG, "compressToJPEG byte[].size=" + image.length);
        Bitmap bmp = BitmapFactory.decodeByteArray (image, 0, image.length);
        if ( bmp == null ) {
            Log.d(TAG, " buffer can't be decoded ");
            return null;
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        return outputStream.toByteArray();
    }

    private  String serializeAsBase64Str(byte[] image) {
        Log.d(TAG, "serializeAsBase64Str");
        return Base64.encodeToString(image, Base64.DEFAULT);
    }

    private  void saveAsFile(String str, String name){
        Log.d(TAG, "saveAsFile " + "sdcard/DDM/"+ TAG + name + ".log");
        File file = new File("sdcard/DDM/"+ TAG + name + ".log");

        OutputStream out = null;
        byte[] bytes = str.getBytes();
        try {
            out = new BufferedOutputStream(new FileOutputStream(file));
            out.write(bytes, 0, bytes.length);
        }catch(Exception e) {
            Log.d(TAG, e.toString());
        }finally {
            if (out != null) {
                try {
                    out.close();
                }catch(Exception e){
                    Log.d(TAG, e.toString());
                }
            }
        }
    }

    private void saveAsJPEG(byte[] bytes){
        Log.d(TAG, "saveAsJPEG");
        File file = new File("sdcard/"+ System.currentTimeMillis() + "_depth.JPEG");
        OutputStream out = null;
        try {
            out = new BufferedOutputStream(new FileOutputStream(file));
            out.write(bytes, 0, bytes.length);
        }catch(Exception e) {
            Log.d(TAG, e.toString());
        }finally {
            if (out != null) {
                try {
                    out.close();
                }catch(Exception e){
                    Log.d(TAG, e.toString());
                }
            }
        }
    }

    public static class DepthMap{
        public byte[] buffer;
        public int width;
        public int height;
        public Rect roi;
        public byte[] rawDepth;
        public DepthMap(int width, int height){
            this.width = width;
            this.height = height;
        }
    }


    private GDepth(int near, int far, String data) {
        this.mNear = near;
        this.mFar = far;
        this.mData = data;
    }
    public static GDepth createGDepth(XMPMeta xmpMeta){
        try {
            int near = Integer.parseInt((String)
                    xmpMeta.getProperty(GDepth.NAMESPACE_URL, PROPERTY_NEAR).getValue());
            int far = Integer.parseInt((String)
                    xmpMeta.getProperty(GDepth.NAMESPACE_URL, PROPERTY_FAR).getValue());
            String data = (String)xmpMeta.getProperty(
                    GDepth.NAMESPACE_URL, PROPERTY_DATA).getValue();
            String format = (String)xmpMeta.getProperty(
                    GDepth.NAMESPACE_URL, PROPERTY_FORMAT).getValue();
            Log.d(TAG, "new GDepth: nerar=" + near+ " far=" + far + "format=" + format+ " data=" + data);
            int x = Integer.parseInt((String)
                    xmpMeta.getProperty(GDepth.NAMESPACE_URL, PROPERTY_ROI_X).getValue());
            int y = Integer.parseInt((String)
                    xmpMeta.getProperty(GDepth.NAMESPACE_URL, PROPERTY_ROI_Y).getValue());
            int width = Integer.parseInt((String)
                    xmpMeta.getProperty(GDepth.NAMESPACE_URL, PROPERTY_ROI_WIDTH).getValue());
            int height = Integer.parseInt((String)
                    xmpMeta.getProperty(GDepth.NAMESPACE_URL, PROPERTY_ROI_HEIGHT).getValue());
            Log.d(TAG, "x=" + x + " y=" + y + " width=" + width + " height=" + height);
            GDepth gDepth = new GDepth(near, far, data);
            return gDepth;
        }catch(XMPException e){
            Log.e(TAG, e.toString());
        }catch(Exception e) {
            Log.e(TAG, e.toString());
        }
        return null;
    }

    public boolean decode() {
        Log.d(TAG, "decode");
        byte[] depthBuffer = Base64.decode(mData, Base64.DEFAULT);
        saveAsJPEG(depthBuffer);
        //TODO:
        //convert JPEG compress bytes to bytes

        int[] intDepthBuffer = new int[depthBuffer.length];
        int[] intDepth = new int[depthBuffer.length];

        //conver to 0-255;
        for( int i=0; i < intDepthBuffer.length; ++i) {
            intDepthBuffer[i] = (256+depthBuffer[i])%256;
        }
        //conver to depth value

        return false;

    }
}