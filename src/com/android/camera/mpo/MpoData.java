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

package com.android.camera.mpo;

import java.util.ArrayList;
import java.util.List;

import com.android.camera.mpo.MpoTag.MpEntry;

public class MpoData {

    private MpoImageData mPrimaryMpoImage;
    private ArrayList<MpoImageData> mAuxiliaryImages = new ArrayList<MpoImageData>();

    public MpoData() {
    }

    public void setPrimaryMpoImage(MpoImageData image) {
        mPrimaryMpoImage = image;
        addDefaultAttribIfdTags(mPrimaryMpoImage, 1);
        addDefaultIndexIfdTags();
    }

    public void addAuxiliaryMpoImage(MpoImageData image) {
        mAuxiliaryImages.add(image);
        int imageNum = getAuxiliaryImageCount() + ((mPrimaryMpoImage == null) ? 0 : 1);
        addDefaultAttribIfdTags(image, imageNum);
    }

    public boolean removeAuxiliaryMpoImage(MpoImageData image) {
        boolean ret = mAuxiliaryImages.remove(image);
        return ret;
    }

    public MpoImageData getPrimaryMpoImage() {
        return mPrimaryMpoImage;
    }

    public List<MpoImageData> getAuxiliaryMpoImages() {
        return mAuxiliaryImages;
    }

    public int getAuxiliaryImageCount() {
        return mAuxiliaryImages.size();
    }

    public void addDefaultAttribIfdTags(MpoImageData image, int imageNum) {
        MpoTag mpFormatVersionTag = new MpoTag((short) MpoInterface.TAG_MP_FORMAT_VERSION,
                MpoTag.TYPE_UNDEFINED, 4, MpoIfdData.TYPE_MP_ATTRIB_IFD, true);
        mpFormatVersionTag.setValue(MpoIfdData.MP_FORMAT_VER_VALUE);
        image.addTag(mpFormatVersionTag);

        MpoTag imageNumTag = new MpoTag((short) MpoInterface.TAG_IMAGE_NUMBER,
                MpoTag.TYPE_UNSIGNED_LONG, 1, MpoIfdData.TYPE_MP_ATTRIB_IFD, false);
        imageNumTag.setValue(imageNum);
        image.addTag(imageNumTag);
    }

    public void addDefaultIndexIfdTags() {
        if (mPrimaryMpoImage == null)
            throw new IllegalArgumentException("Primary Mpo Image has not been set");
        if (getAuxiliaryImageCount() == 0)
            throw new IllegalArgumentException("No auxiliary images have been added");

        MpoTag mpFormatVersionTag = mPrimaryMpoImage.getTag(
                (short) MpoInterface.TAG_MP_FORMAT_VERSION, MpoIfdData.TYPE_MP_INDEX_IFD);
        if (mpFormatVersionTag == null) {
            mpFormatVersionTag = new MpoTag((short) MpoInterface.TAG_MP_FORMAT_VERSION,
                    MpoTag.TYPE_UNDEFINED, 4, MpoIfdData.TYPE_MP_INDEX_IFD, true);
            mpFormatVersionTag.setValue(MpoIfdData.MP_FORMAT_VER_VALUE);
            mPrimaryMpoImage.addTag(mpFormatVersionTag);
        }

        MpoTag numImagesTag = mPrimaryMpoImage.getTag((short) MpoInterface.TAG_NUM_IMAGES,
                MpoIfdData.TYPE_MP_INDEX_IFD);
        if (numImagesTag == null) {
            numImagesTag = new MpoTag((short) MpoInterface.TAG_NUM_IMAGES,
                    MpoTag.TYPE_UNSIGNED_LONG, 1, MpoIfdData.TYPE_MP_INDEX_IFD, false);
        }
        numImagesTag.setValue(getAuxiliaryImageCount() + 1);
        mPrimaryMpoImage.addTag(numImagesTag);

        // check, create and add required tags
        MpoTag mpEntryTag = new MpoTag((short) MpoInterface.TAG_MP_ENTRY, MpoTag.TYPE_UNDEFINED,
                MpoTag.SIZE_UNDEFINED, MpoIfdData.TYPE_MP_INDEX_IFD, false);
        ArrayList<MpEntry> mpEntries = new ArrayList<MpEntry>(getAuxiliaryImageCount() + 1);
        mpEntries.add(new MpEntry()); // primary image
        for (int i = 0; i < getAuxiliaryImageCount(); i++) {
            mpEntries.add(new MpEntry()); // aux images
        }
        mpEntryTag.setValue(mpEntries);
        mPrimaryMpoImage.addTag(mpEntryTag);
    }

    public void updateAllTags() {
        updateAttribIfdTags();
        updateIndexIfdTags();
    }

    private void updateIndexIfdTags() {
        if (mPrimaryMpoImage == null)
            throw new IllegalArgumentException("Primary Mpo Image has not been set");
        if (getAuxiliaryImageCount() == 0)
            throw new IllegalArgumentException("No auxiliary images have been added");

        MpoTag numImagesTag = mPrimaryMpoImage.getTag((short) MpoInterface.TAG_NUM_IMAGES,
                MpoIfdData.TYPE_MP_INDEX_IFD);
        if (numImagesTag == null) {
            numImagesTag = new MpoTag((short) MpoInterface.TAG_NUM_IMAGES,
                    MpoTag.TYPE_UNSIGNED_LONG, 1, MpoIfdData.TYPE_MP_INDEX_IFD, false);
        }
        numImagesTag.setValue(getAuxiliaryImageCount() + 1);
        mPrimaryMpoImage.addTag(numImagesTag);

        // check, create and add required tags
        MpoTag mpEntryTag = new MpoTag((short) MpoInterface.TAG_MP_ENTRY, MpoTag.TYPE_UNDEFINED,
                MpoTag.SIZE_UNDEFINED, MpoIfdData.TYPE_MP_INDEX_IFD, false);
        ArrayList<MpEntry> mpEntries = new ArrayList<MpEntry>(getAuxiliaryImageCount() + 1);

        int imgOffset = 0;
        // primary image
        MpEntry entry = new MpEntry(1 << 29, mPrimaryMpoImage.calculateImageSize(), imgOffset);
        mpEntries.add(entry);
        imgOffset += mPrimaryMpoImage.calculateImageSize();

        for (MpoImageData image : getAuxiliaryMpoImages()) {
            int imageSize = image.calculateImageSize();
            entry = new MpEntry(0x020002, imageSize, imgOffset);
            mpEntries.add(entry); // aux images
            imgOffset += imageSize;
        }
        mpEntryTag.setValue(mpEntries);
        mPrimaryMpoImage.addTag(mpEntryTag);
    }

    private void updateAttribIfdTags() {
        if (mPrimaryMpoImage == null)
            throw new IllegalArgumentException("Primary Mpo Image has not been set");
        if (getAuxiliaryImageCount() == 0)
            throw new IllegalArgumentException("No auxiliary images have been added");

        int imageNum = 1;
        MpoTag imageNumTag = null;

        imageNumTag = new MpoTag((short) MpoInterface.TAG_IMAGE_NUMBER, MpoTag.TYPE_UNSIGNED_LONG,
                1, MpoIfdData.TYPE_MP_ATTRIB_IFD, false);
        imageNumTag.setValue(0xFFFFFFFFL);
        mPrimaryMpoImage.addTag(imageNumTag);

        for (MpoImageData image : getAuxiliaryMpoImages()) {
            imageNumTag = new MpoTag((short) MpoInterface.TAG_IMAGE_NUMBER,
                    MpoTag.TYPE_UNSIGNED_LONG, 1, MpoIfdData.TYPE_MP_ATTRIB_IFD, false);
            imageNumTag.setValue(imageNum++);
            image.addTag(imageNumTag);
        }
    }
}