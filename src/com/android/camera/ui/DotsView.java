/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
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

package com.android.camera.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class DotsView extends View {
    private Paint mTargetPaint;
    private int mPosition;
    private float mPositionOffset;
    private DotsViewItem mItems;

    public DotsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mTargetPaint = new Paint();
        mTargetPaint.setColor(Color.WHITE);
        mTargetPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    }

    public void update(int position, float positionOffest) {
        mPosition = position;
        mPositionOffset = positionOffest;
        invalidate();
    }

    public void setItems(DotsViewItem item) {
        mItems = item;
    }

    public void update() {
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        if(mItems == null) {
            return;
        }

        int dx = canvas.getWidth()/(mItems.getTotalItemNums()+1);
        int dy = canvas.getHeight()/(mItems.getTotalItemNums()+1);
        int y = canvas.getHeight()/2;
        float radius = Math.min(dx, dy)/2f;
        for(int i=0; i < mItems.getTotalItemNums(); i++) {
            if(i-1 == mPosition && mPositionOffset > 0f) {
                drawDot(canvas, (i + 1) * dx, y, radius + radius * mPositionOffset, mItems.isChosen(i));
            } else if(i+1 == mPosition && mPositionOffset < 0f) {
                drawDot(canvas, (i + 1) * dx, y, radius - radius*mPositionOffset, mItems.isChosen(i));
            } else if(i == mPosition) {
                drawDot(canvas, (i + 1) * dx, y, radius + radius * (1 - Math.abs(mPositionOffset)), mItems.isChosen(i));
            } else {
                drawDot(canvas, (i + 1) * dx, y, radius, mItems.isChosen(i));
            }
        }
    }

    private void drawDot(Canvas canvas, float cx, float cy, float radius, boolean isChosen) {
        if(isChosen) {
            mTargetPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            canvas.drawCircle(cx, cy, radius, mTargetPaint);
        } else {
            mTargetPaint.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(cx, cy, radius, mTargetPaint);
        }
    }
}
