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
import android.util.Log;

public class ExtendedFaceWrapper extends Wrapper{
    private final static String CLASS_NAME = "org.codeaurora.camera.ExtendedFace";
    private static Class<?> mExtendFaceClass;

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

    private static Method method_getSmileDegree = null;
    public static int getSmileDegree(Face face) {
        int degree = 0;
        try {
            if (method_getSmileDegree == null) {
                method_getSmileDegree = getMethod("getSmileDegree");
            }
            degree = (int) method_getSmileDegree.invoke(face);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return degree;
    }

    private static Method method_getSmileScore = null;
    public static int getSmileScore(Face face) {
        int score = 0;
        try{
            if ( method_getSmileScore == null ){
                method_getSmileScore = getMethod("getSmileScore");
            }
            score = (int)method_getSmileScore.invoke(face);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return score;
    }

    private static Method method_getBlinkDetected = null;
    public static int getBlinkDetected(Face face) {
        int blink = 0;
        try{
            if ( method_getBlinkDetected == null ){
                method_getBlinkDetected = getMethod("getBlinkDetected");
            }
            blink = (int)method_getBlinkDetected.invoke(face);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return blink;
    }

    private static Method method_getFaceRecognized = null;
    public static int getFaceRecognized(Face face) {
        int faces = 0;
        try{
            if ( method_getFaceRecognized == null ){
                method_getFaceRecognized = getMethod("getFaceRecognized");
            }
            faces = (int)method_getFaceRecognized.invoke(face);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return faces;
    }

    private static Method method_getGazeAngle = null;
    public static int getGazeAngle(Face face) {
        int angle = 0;
        try{
            if ( method_getGazeAngle == null ){
                method_getGazeAngle = getMethod("getGazeAngle");
            }
            angle = (int)method_getGazeAngle.invoke(face);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return angle;
    }

    private static Method method_getUpDownDirection = null;
    public static int getUpDownDirection(Face face) {
        int direction = 0;
        try{
            if ( method_getUpDownDirection == null ){
                method_getUpDownDirection = getMethod("getUpDownDirection");
            }
            direction = (int)method_getUpDownDirection.invoke(face);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return direction;
    }

    private static Method method_getLeftRightDirection = null;
    public static int getLeftRightDirection(Face face) {
        int direction = 0;
        try{
            if ( method_getLeftRightDirection == null ){
                method_getLeftRightDirection = getMethod("getLeftRightDirection");
            }
            direction = (int)method_getLeftRightDirection.invoke(face);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return direction;
    }

    private static Method method_getRollDirection = null;
    public static int getRollDirection(Face face) {
        int direction = 0;
        try{
            if ( method_getRollDirection == null ){
                method_getRollDirection = getMethod("getRollDirection");
            }
            direction = (int)method_getRollDirection.invoke(face);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return direction;
    }

    private static Method method_getLeftEyeBlinkDegree = null;
    public static int getLeftEyeBlinkDegree(Face face) {
        int degree = 0;
        try{
            if ( method_getLeftEyeBlinkDegree == null ){
                method_getLeftEyeBlinkDegree = getMethod("getLeftEyeBlinkDegree");
            }
            degree = (int)method_getLeftEyeBlinkDegree.invoke(face);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return degree;
    }

    private static Method method_getRightEyeBlinkDegree = null;
    public static int getRightEyeBlinkDegree(Face face) {
        int degree = 0;
        try{
            if ( method_getRightEyeBlinkDegree == null ){
                method_getRightEyeBlinkDegree = getMethod("getRightEyeBlinkDegree");
            }
            degree = (int)method_getRightEyeBlinkDegree.invoke(face);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return degree;
    }

    private static Method method_getLeftRightGazeDegree = null;
    public static int getLeftRightGazeDegree(Face face) {
        int degree = 0;
        try{
            if ( method_getLeftRightGazeDegree == null ){
                method_getLeftRightGazeDegree = getMethod("getLeftRightGazeDegree");
            }
            degree = (int)method_getLeftRightGazeDegree.invoke(face);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return degree;
    }

    private static Method method_getTopBottomGazeDegree = null;
    public static int getTopBottomGazeDegree(Face face) {
        int degree = 0;
        try{
            if ( method_getTopBottomGazeDegree == null ){
                method_getTopBottomGazeDegree = getMethod("getTopBottomGazeDegree");
            }
            degree = (int)method_getTopBottomGazeDegree.invoke(face);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return degree;
    }


    private static Method getMethod(String name) throws Exception{
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + CLASS_NAME + " no " + name);
            return null;
        }
        if (mExtendFaceClass == null) {
            mExtendFaceClass = Class.forName(CLASS_NAME);
        }
        return  mExtendFaceClass.getDeclaredMethod(name);
    }
}
