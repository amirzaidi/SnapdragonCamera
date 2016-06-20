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

rs_allocation gIn;
uint32_t width;
uint32_t height;

uchar4 __attribute__((kernel)) nv21ToRgb(uint32_t x, uint32_t y) {
    uint32_t ySize = width*height;
    uint32_t index = ySize + (x/2*2) + ((y/2) * width);
    int yV = (int)(rsGetElementAt_uchar(gIn, x + y*width) & 0xFF);
    int vV = (int)(rsGetElementAt_uchar(gIn, index) & 0xFF ) -128;
    int uV = (int)(rsGetElementAt_uchar(gIn, index+1) & 0xFF ) -128;

    int r = (int) (yV  + 1.370705f * vV );
    int g = (int) (yV  - 0.698001f * vV  - 0.337633f* uV);
    int b = (int) (yV  + 1.732446 * uV );

    r = r>255? 255 : r<0 ? 0 : r;
    g = g>255? 255 : g<0 ? 0 : g;
    b = b>255? 255 : b<0 ? 0 : b;
    uchar4 res4;
    res4.r = (uchar)(r & 0xFF);
    res4.g = (uchar)(g & 0xFF);
    res4.b = (uchar)(b & 0xFF);
    res4.a = 0xFF;

    return res4;
}