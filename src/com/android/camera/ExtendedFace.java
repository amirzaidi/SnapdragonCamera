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

package com.android.camera;

import android.hardware.camera2.params.Face;

public class ExtendedFace {
    private int mSmileDegree = 0;
    private int mSmileConfidence = 0;
    private int mBlinkDetected = 0;
    private int mLeyeBlink = 0;
    private int mReyeBlink = 0;
    private int mGazeAngle = 0;
    private int mLeftrightGaze = 0;
    private int mTopbottomGaze = 0;
    private int mGazeDirection = 0;
    private int mRollDirection = 0;
    private int mId;

    public ExtendedFace(int id) {
        mId = id;
    }

    public int getBlinkDetected() {
        return mBlinkDetected;
    }

    public int getLeyeBlink() {
        return mLeyeBlink;
    }

    public int getReyeBlink() {
        return mReyeBlink;
    }

    public int getSmileDegree() {
        return mSmileDegree;
    }

    public int getSmileConfidence() {
        return mSmileConfidence;
    }

    public int getLeftrightGaze() {
        return mLeftrightGaze;
    }

    public int getTopbottomGaze() {
        return mTopbottomGaze;
    }

    public int getGazeDirection() {
        return mGazeDirection;
    }

    public int getRollDirection() {
        return mRollDirection;
    }

    public void setBlinkDetected(int blinkDetected) {
        this.mBlinkDetected = blinkDetected;
    }

    public void setBlinkDegree(byte left, byte right) {
        this.mLeyeBlink = left;
        this.mReyeBlink = right;
    }

    public void setSmileDegree(byte smileDegree) {
        this.mSmileDegree = smileDegree;
    }

    public void setGazeDirection(int topbottomGaze, int leftrightGaze, int rollDirection) {
        this.mTopbottomGaze = topbottomGaze;
        this.mLeftrightGaze = leftrightGaze;
        this.mRollDirection = rollDirection;
    }

    public void setGazeAngle(byte gazeAngle) {
        this.mGazeAngle = gazeAngle;
    }

    public void setSmileConfidence(int smileConfidence) {
        this.mSmileConfidence = smileConfidence;
    }
}