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

package com.android.camera;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import org.codeaurora.snapcam.R;

public class BestpictureFragment extends Fragment {
    public static final String PARAM_IMAGE_NUM = "image_num";
    private static final String TAG = "BestpictureFilter";
    private int mImageNum;
    private ImageView mImageView;
    private ImageView mPictureSelectButton;
    private BestpictureActivity.ImageItems mImageItems;

    public static BestpictureFragment create(int imageNum, BestpictureActivity.ImageItems items) {
        BestpictureFragment fragment = new BestpictureFragment();
        Bundle args = new Bundle();
        args.putInt(PARAM_IMAGE_NUM, imageNum);
        fragment.setArguments(args);
        return fragment;
    }

    public BestpictureFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mImageNum = getArguments().getInt(PARAM_IMAGE_NUM);
        mImageItems = ((BestpictureActivity)getActivity()).getImageItems();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater
                .inflate(R.layout.bestpicture_page, container, false);
        mImageView = (ImageView) rootView.findViewById(R.id.image_view);
        mPictureSelectButton = (ImageView) rootView.findViewById(R.id.picture_select);
        if (mImageItems != null) {
            initSelectButton();
            mImageView.setImageBitmap(mImageItems.getBitmap(mImageNum));
            rootView.findViewById(R.id.picture_select).setOnClickListener(
                    new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    mImageItems.toggleImageSelection(mImageNum);
                    initSelectButton();
                    }
            });
        }
        return rootView;
    }

    private void initSelectButton() {
        if(mImageItems.isChosen(mImageNum)) {
            mPictureSelectButton.setBackground(getResources().getDrawable(R.drawable.pick_the_best_photo_selected, null));
        } else {
            mPictureSelectButton.setBackground(getResources().getDrawable(R.drawable.pick_the_best_photo_unselected, null));
        }
    }

    @Override
    public void onDestroy() {
        mImageItems = null;
        super.onDestroy();
    }
}