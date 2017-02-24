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

package org.codeaurora.snapcam.wrapper;

import java.lang.reflect.Method;

import android.hardware.Camera.Face;
import android.os.Bundle;

public class ExtendedFaceWrapper{
    private final static String CLASS_NAME = "org.codeaurora.camera.ExtendedFace";
    private static Class<?> mExtendFaceClass;
    public Face mFace;
    public ExtendedFaceWrapper(Face face){
        mFace = face;
    }

    public static boolean isExtendedFaceInstance(Object object) {
        if ( mExtendFaceClass == null ){
            try {
                mExtendFaceClass = Class.forName(CLASS_NAME);
            }catch (Exception exception){
                exception.printStackTrace();
                return false;
            }
        }
        return mExtendFaceClass.isInstance(object);
    }

    public int getSmileDegree() {
        return  (int)invokeMethod("getSmileDegree");
    }

    public int getSmileScore() {
        return (int)invokeMethod("getSmileScore");
    }

    public int getBlinkDetected() {
        return (int)invokeMethod("getBlinkDetected");
    }


    public int getFaceRecognized() {
        return (int)invokeMethod("getFaceRecognized");
    }

    public int getGazeAngle() {
        return (int)invokeMethod("getGazeAngle");
    }

    public int getUpDownDirection() {
        return (int)invokeMethod("getUpDownDirection");
    }

    public int getLeftRightDirection() {
        return (int)invokeMethod("getLeftRightDirection");
    }


    public int getRollDirection() {
        return (int)invokeMethod("getRollDirection");
    }

    public int getLeftEyeBlinkDegree() {
        return (int)invokeMethod("getLeftEyeBlinkDegree");
    }


    public int getRightEyeBlinkDegree() {
        return (int)invokeMethod("getRightEyeBlinkDegree");
    }


    public int getLeftRightGazeDegree() {
        return (int)invokeMethod("getLeftRightGazeDegree");
    }


    public int getTopBottomGazeDegree() {
        return (int)invokeMethod("getTopBottomGazeDegree");
    }

    public Bundle getExtendedFaceInfo() {
        return (Bundle)invokeMethod("getExtendedFaceInfo");
    }

    private Object invokeMethod(String name){
        Object result = null;
        try {
            if ( mExtendFaceClass == null ){
                mExtendFaceClass = Class.forName(CLASS_NAME);
            }
            Method method = mExtendFaceClass.getDeclaredMethod(name);
            result = method.invoke(mFace);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return result;
    }
}
