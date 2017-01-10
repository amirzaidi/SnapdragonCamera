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
#pragma version(1)
#pragma rs java_package_name(com.android.camera.imageprocessor)
#pragma rs_fp_relaxed

rs_allocation gOut;
rs_allocation gIn;
uint32_t width;
uint32_t height;
uint32_t pad;
uint32_t degree;

uchar __attribute__((kernel)) rotate90andMerge(uint32_t x, uint32_t y) {
    uchar yValue = rsGetElementAt_uchar(gIn, x + y*width);

    if(degree == 180) {
        if(x >= width - pad)
            return (uchar)0;
        rsSetElementAt_uchar(gOut, yValue, (width-1-x-pad)*height + height - 1 - y);
    } else if (degree == 90) {
        rsSetElementAt_uchar(gOut, yValue, x*height + height - 1 - y);
    } else if (degree == 270) {
        if(x >= width - pad)
            return (uchar)0;
        rsSetElementAt_uchar(gOut, yValue, (width-1-x-pad)*height + y);
    } else if (degree == 0) {
        if(x >= width - pad)
            return (uchar)0;
         rsSetElementAt_uchar(gOut, yValue, x*height + y);
    }


    if(x%2 == 0 && y%2 == 0) {
        uint32_t ySize = width*height;
        uint32_t index = ySize + x + ((y/2) * width);
        uchar vValue = rsGetElementAt_uchar(gIn, index);
        uchar uValue = rsGetElementAt_uchar(gIn, index + 1);
        if(degree == 180) {
            if(x >= width - pad)
                return (uchar)0;
            rsSetElementAt_uchar(gOut, uValue, ySize + (width-2-x-pad)/2*height + height - 1 - y);
            rsSetElementAt_uchar(gOut, vValue, ySize + (width-2-x-pad)/2*height + height - 1 - y - 1);
        } else if (degree == 90) {
            rsSetElementAt_uchar(gOut, uValue, ySize + x/2*height + height - 1 - y);
            rsSetElementAt_uchar(gOut, vValue, ySize + x/2*height + height - 1 - y - 1);
        } else if (degree == 270) {
            if(x >= (width - pad))
                 return (uchar)0;
            rsSetElementAt_uchar(gOut, uValue, ySize + (width-1-x-pad)/2*height + y -1);
            rsSetElementAt_uchar(gOut, vValue, ySize + (width-1-x-pad)/2*height + y);
        } else if (degree == 0) {
            if(x >= (width - pad))
                 return (uchar)0;
            rsSetElementAt_uchar(gOut, uValue, ySize + x/2*height + y - 1);
            rsSetElementAt_uchar(gOut, vValue, ySize + x/2*height + y);
        }
    }
    return (uchar)0;
}