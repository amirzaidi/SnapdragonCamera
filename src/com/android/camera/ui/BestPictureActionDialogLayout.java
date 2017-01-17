/*
 * Copyright (c) 2017, The Linux Foundation. All rights reserved.
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
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.android.camera.util.CameraUtil;
import org.codeaurora.snapcam.R;


public class BestPictureActionDialogLayout extends RelativeLayout implements View.OnClickListener {

    private TextView mTitleText;
    private TextView mContent;
    private Button mNativeBt;
    private Button mPositiveBt;
    private Button mOKButton;
    int mode;
    private IDialogDataControler mDialogDataControler;

    public interface IDialogDataControler {

        String getTitleString();

        String getContentString();

        String getPositionButtonString();

        String getNativeButtonString();

        String getOKButtonString();

        void doClickPositionBtAction();

        void doClickNativeBtAction();

        void doClickOKBtAction();
    }

    public BestPictureActionDialogLayout(Context context) {
        this(context, null);
    }

    public BestPictureActionDialogLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BestPictureActionDialogLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    public void setDialogDataControler(View root, IDialogDataControler dialogDataControler) {
        mode = (int) root.getTag();
        mTitleText = (TextView) root.findViewById(R.id.mtitle);
        mContent = (TextView) root.findViewById(R.id.content);
        mNativeBt = (Button) root.findViewById(R.id.nativebt);
        mPositiveBt = (Button) root.findViewById(R.id.positivebt);
        mNativeBt.setOnClickListener(this);
        mPositiveBt.setOnClickListener(this);
        mOKButton = (Button) root.findViewById(R.id.okbt);
        mOKButton.setOnClickListener(this);
        mDialogDataControler = dialogDataControler;
        setViewData();
    }

    private void setViewData() {
        mTitleText.setText(mDialogDataControler.getTitleString());
        mContent.setText(mDialogDataControler.getContentString());
        if (mode == CameraUtil.MODE_TWO_BT) {
            mNativeBt.setText(mDialogDataControler.getNativeButtonString());
            mPositiveBt.setText(mDialogDataControler.getPositionButtonString());
            mOKButton.setVisibility(View.GONE);
            mNativeBt.setVisibility(View.VISIBLE);
            mPositiveBt.setVisibility(View.VISIBLE);
        } else if (mode == CameraUtil.MODE_ONE_BT) {
            mOKButton.setText(mDialogDataControler.getOKButtonString());
            mOKButton.setVisibility(View.VISIBLE);
            mNativeBt.setVisibility(View.GONE);
            mPositiveBt.setVisibility(View.GONE);
        }

        invalidate();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.positivebt:
                mDialogDataControler.doClickPositionBtAction();
                break;

            case R.id.nativebt:
                mDialogDataControler.doClickNativeBtAction();
                break;

            case R.id.okbt:
                mDialogDataControler.doClickOKBtAction();
                break;

            default:
                break;
        }
    }
}
