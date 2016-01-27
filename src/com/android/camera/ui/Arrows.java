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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;

public class Arrows extends View {
    private static final int ARROW_COLOR = Color.WHITE;
    private static final double ARROW_END_DEGREE = 15d;
    private static final int ARROW_END_LENGTH = 50;

    private Paint mPaint;
    private ArrayList<Path> mPaths;

    public Arrows(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPaths = new ArrayList<Path>();
        mPaint = new Paint();
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setColor(ARROW_COLOR);
        mPaint.setStrokeWidth(2f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mPaths != null) {
            for(int i=0; i < mPaths.size(); i++) {
                canvas.drawPath(mPaths.get(i), mPaint);
            }
        }
    }

    public void addPath(float[] x, float[] y) {
        Path path = new Path();
        path.reset();
        path.moveTo(x[0], y[0]);
        for(int i=1; i < x.length; i++) {
            if(i == x.length-1) {
                path.lineTo(x[i], y[i]);

                double setha = Math.toDegrees(Math.atan2(y[i] - y[i - 1], x[i] - x[i - 1]));
                setha = (setha + ARROW_END_DEGREE + 360) % 360;
                path.lineTo(x[i]-(float)(ARROW_END_LENGTH*Math.cos(Math.toRadians(setha))),
                            y[i]-(float)(ARROW_END_LENGTH*Math.sin(Math.toRadians(setha))));
                path.lineTo(x[i], y[i]);
                setha = (setha - ARROW_END_DEGREE*2 + 360) % 360;
                path.lineTo(x[i]-(float)(ARROW_END_LENGTH*Math.cos(Math.toRadians(setha))),
                            y[i]-(float)(ARROW_END_LENGTH*Math.sin(Math.toRadians(setha))));
            }
            else
                path.quadTo(x[i],y[i], x[i+1], y[i+1]);
        }
        mPaths.add(path);
        invalidate();
    }
}
