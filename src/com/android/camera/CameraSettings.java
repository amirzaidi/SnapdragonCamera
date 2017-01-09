/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.EncoderCapabilities;
import android.media.EncoderCapabilities.VideoEncoderCap;
import java.util.HashMap;
import android.util.Log;

import com.android.camera.util.ApiHelper;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.GcamHelper;
import org.codeaurora.snapcam.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import android.os.Build;
import java.util.StringTokenizer;
import android.os.SystemProperties;

/**
 *  Provides utilities and keys for Camera settings.
 */
public class CameraSettings {
    private static final int NOT_FOUND = -1;

    public static final String KEY_VERSION = "pref_version_key";
    public static final String KEY_LOCAL_VERSION = "pref_local_version_key";
    public static final String KEY_RECORD_LOCATION = "pref_camera_recordlocation_key";
    public static final String KEY_VIDEO_QUALITY = "pref_video_quality_key";
    public static final String KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL = "pref_video_time_lapse_frame_interval_key";
    public static final String KEY_PICTURE_SIZE = "pref_camera_picturesize_key";
    public static final String KEY_JPEG_QUALITY = "pref_camera_jpegquality_key";
    public static final String KEY_FOCUS_MODE = "pref_camera_focusmode_key";
    public static final String KEY_FLASH_MODE = "pref_camera_flashmode_key";
    public static final String KEY_VIDEOCAMERA_FLASH_MODE = "pref_camera_video_flashmode_key";
    public static final String KEY_WHITE_BALANCE = "pref_camera_whitebalance_key";
    public static final String KEY_SCENE_MODE = "pref_camera_scenemode_key";
    public static final String KEY_EXPOSURE = "pref_camera_exposure_key";
    public static final String KEY_TIMER = "pref_camera_timer_key";
    public static final String KEY_TIMER_SOUND_EFFECTS = "pref_camera_timer_sound_key";
    public static final String KEY_VIDEO_EFFECT = "pref_video_effect_key";
    public static final String KEY_CAMERA_ID = "pref_camera_id_key";
    public static final String KEY_CAMERA_HDR = "pref_camera_hdr_key";
    public static final String KEY_CAMERA_HQ = "pref_camera_hq_key";
    public static final String KEY_CAMERA_HDR_PLUS = "pref_camera_hdr_plus_key";
    public static final String KEY_CAMERA_FIRST_USE_HINT_SHOWN = "pref_camera_first_use_hint_shown_key";
    public static final String KEY_VIDEO_FIRST_USE_HINT_SHOWN = "pref_video_first_use_hint_shown_key";
    public static final String KEY_PHOTOSPHERE_PICTURESIZE = "pref_photosphere_picturesize_key";
    public static final String KEY_STARTUP_MODULE_INDEX = "camera.startup_module";

    public static final String KEY_VIDEO_ENCODER = "pref_camera_videoencoder_key";
    public static final String KEY_AUDIO_ENCODER = "pref_camera_audioencoder_key";
    public static final String KEY_VIDEO_DURATION = "pref_camera_video_duration_key";
    public static final String KEY_POWER_MODE = "pref_camera_powermode_key";
    public static final String KEY_PICTURE_FORMAT = "pref_camera_pictureformat_key";
    public static final String KEY_ZSL = "pref_camera_zsl_key";
    public static final String KEY_CAMERA_SAVEPATH = "pref_camera_savepath_key";
    public static final String KEY_FILTER_MODE = "pref_camera_filter_mode_key";
    public static final String KEY_COLOR_EFFECT = "pref_camera_coloreffect_key";
    public static final String KEY_FACE_DETECTION = "pref_camera_facedetection_key";
    public static final String KEY_TOUCH_AF_AEC = "pref_camera_touchafaec_key";
    public static final String KEY_SELECTABLE_ZONE_AF = "pref_camera_selectablezoneaf_key";
    public static final String KEY_SATURATION = "pref_camera_saturation_key";
    public static final String KEY_CONTRAST = "pref_camera_contrast_key";
    public static final String KEY_SHARPNESS = "pref_camera_sharpness_key";
    public static final String KEY_AUTOEXPOSURE = "pref_camera_autoexposure_key";
    public static final String KEY_ANTIBANDING = "pref_camera_antibanding_key";
    public static final String KEY_ISO = "pref_camera_iso_key";
    public static final String KEY_LENSSHADING = "pref_camera_lensshading_key";
    public static final String KEY_HISTOGRAM = "pref_camera_histogram_key";
    public static final String KEY_DENOISE = "pref_camera_denoise_key";
    public static final String KEY_BRIGHTNESS = "pref_camera_brightness_key";
    public static final String KEY_REDEYE_REDUCTION = "pref_camera_redeyereduction_key";
    public static final String KEY_SELFIE_MIRROR = "pref_camera_selfiemirror_key";
    public static final String KEY_SHUTTER_SOUND = "pref_camera_shuttersound_key";
    public static final String KEY_CDS_MODE = "pref_camera_cds_mode_key";
    public static final String KEY_VIDEO_CDS_MODE = "pref_camera_video_cds_mode_key";
    public static final String KEY_TNR_MODE = "pref_camera_tnr_mode_key";
    public static final String KEY_VIDEO_TNR_MODE = "pref_camera_video_tnr_mode_key";
    public static final String KEY_AE_BRACKET_HDR = "pref_camera_ae_bracket_hdr_key";
    public static final String KEY_ADVANCED_FEATURES = "pref_camera_advanced_features_key";
    public static final String KEY_HDR_MODE = "pref_camera_hdr_mode_key";
    public static final String KEY_HDR_NEED_1X = "pref_camera_hdr_need_1x_key";
    public static final String KEY_DEVELOPER_MENU = "pref_developer_menu_key";

    public static final String KEY_VIDEO_SNAPSHOT_SIZE = "pref_camera_videosnapsize_key";
    public static final String KEY_VIDEO_HIGH_FRAME_RATE = "pref_camera_hfr_key";
    public static final String KEY_SEE_MORE = "pref_camera_see_more_key";
    public static final String KEY_NOISE_REDUCTION = "pref_camera_noise_reduction_key";
    public static final String KEY_VIDEO_HDR = "pref_camera_video_hdr_key";
    public static final String DEFAULT_VIDEO_QUALITY_VALUE = "custom";
    public static final String KEY_SKIN_TONE_ENHANCEMENT = "pref_camera_skinToneEnhancement_key";
    public static final String KEY_SKIN_TONE_ENHANCEMENT_FACTOR = "pref_camera_skinToneEnhancement_factor_key";

    public static final String KEY_FACE_RECOGNITION = "pref_camera_facerc_key";
    public static final String KEY_DIS = "pref_camera_dis_key";

    public static final String KEY_LONGSHOT = "pref_camera_longshot_key";
    public static final String KEY_INSTANT_CAPTURE = "pref_camera_instant_capture_key";

    public static final String KEY_BOKEH_MODE = "pref_camera_bokeh_mode_key";
    public static final String KEY_BOKEH_MPO = "pref_camera_bokeh_mpo_key";
    public static final String KEY_BOKEH_BLUR_VALUE = "pref_camera_bokeh_blur_degree_key";

    private static final String KEY_QC_SUPPORTED_AE_BRACKETING_MODES = "ae-bracket-hdr-values";
    private static final String KEY_QC_SUPPORTED_AF_BRACKETING_MODES = "af-bracket-values";
    private static final String KEY_QC_SUPPORTED_RE_FOCUS_MODES = "re-focus-values";
    private static final String KEY_QC_SUPPORTED_CF_MODES = "chroma-flash-values";
    private static final String KEY_QC_SUPPORTED_OZ_MODES = "opti-zoom-values";
    private static final String KEY_QC_SUPPORTED_FSSR_MODES = "FSSR-values";
    private static final String KEY_QC_SUPPORTED_TP_MODES = "true-portrait-values";
    private static final String KEY_QC_SUPPORTED_MTF_MODES = "multi-touch-focus-values";
    private static final String KEY_QC_SUPPORTED_FACE_RECOGNITION_MODES = "face-recognition-values";
    private static final String KEY_QC_SUPPORTED_DIS_MODES = "dis-values";
    private static final String KEY_QC_SUPPORTED_SEE_MORE_MODES = "see-more-values";
    private static final String KEY_QC_SUPPORTED_NOISE_REDUCTION_MODES = "noise-reduction-mode-values";
    private static final String KEY_QC_SUPPORTED_STILL_MORE_MODES = "still-more-values";
    private static final String KEY_QC_SUPPORTED_CDS_MODES = "cds-mode-values";
    private static final String KEY_QC_SUPPORTED_VIDEO_CDS_MODES = "video-cds-mode-values";
    private static final String KEY_QC_SUPPORTED_TNR_MODES = "tnr-mode-values";
    private static final String KEY_QC_SUPPORTED_VIDEO_TNR_MODES = "video-tnr-mode-values";
    private static final String KEY_SNAPCAM_SUPPORTED_HDR_MODES = "hdr-mode-values";
    private static final String KEY_SNAPCAM_SUPPORTED_HDR_NEED_1X = "hdr-need-1x-values";
    public static final String KEY_QC_AE_BRACKETING = "ae-bracket-hdr";
    public static final String KEY_QC_AF_BRACKETING = "af-bracket";
    public static final String KEY_QC_RE_FOCUS = "re-focus";
    public static final int KEY_QC_RE_FOCUS_COUNT = 7;
    public static final String KEY_QC_LEGACY_BURST = "snapshot-burst-num";
    public static final String KEY_QC_CHROMA_FLASH = "chroma-flash";
    public static final String KEY_QC_OPTI_ZOOM = "opti-zoom";
    public static final String KEY_QC_FSSR = "FSSR";
    public static final String KEY_QC_TP = "true-portrait";
    public static final String KEY_QC_MULTI_TOUCH_FOCUS = "multi-touch-focus";
    public static final String KEY_QC_STILL_MORE = "still-more";
    public static final String KEY_QC_FACE_RECOGNITION = "face-recognition";
    public static final String KEY_QC_DIS_MODE = "dis";
    public static final String KEY_QC_CDS_MODE = "cds-mode";
    public static final String KEY_QC_VIDEO_CDS_MODE = "video-cds-mode";
    public static final String KEY_QC_TNR_MODE = "tnr-mode";
    public static final String KEY_QC_VIDEO_TNR_MODE = "video-tnr-mode";
    public static final String KEY_SNAPCAM_HDR_MODE = "hdr-mode";
    public static final String KEY_SNAPCAM_HDR_NEED_1X = "hdr-need-1x";
    public static final String KEY_VIDEO_HSR = "video-hsr";
    public static final String KEY_QC_SEE_MORE_MODE = "see-more";
    public static final String KEY_QC_NOISE_REDUCTION_MODE = "noise-reduction-mode";
    public static final String KEY_QC_INSTANT_CAPTURE = "instant-capture";
    public static final String KEY_QC_INSTANT_CAPTURE_VALUES = "instant-capture-values";

    public static final String KEY_INTERNAL_PREVIEW_RESTART = "internal-restart";
    public static final String KEY_QC_ZSL_HDR_SUPPORTED = "zsl-hdr-supported";
    public static final String KEY_QC_LONGSHOT_SUPPORTED = "longshot-supported";
    private static final String TRUE = "true";
    private static final String FALSE = "false";

    public static final String KEY_AUTO_HDR = "pref_camera_auto_hdr_key";

    //for flip
    public static final String KEY_QC_PREVIEW_FLIP = "preview-flip";
    public static final String KEY_QC_VIDEO_FLIP = "video-flip";
    public static final String KEY_QC_SNAPSHOT_PICTURE_FLIP = "snapshot-picture-flip";
    public static final String KEY_QC_SUPPORTED_FLIP_MODES = "flip-mode-values";

    public static final String FLIP_MODE_OFF = "off";
    public static final String FLIP_MODE_V = "flip-v";
    public static final String FLIP_MODE_H = "flip-h";
    public static final String FLIP_MODE_VH = "flip-vh";

    private static final String KEY_QC_PICTURE_FORMAT = "picture-format-values";
    public static final String KEY_VIDEO_ROTATION = "pref_camera_video_rotation_key";
    private static final String VIDEO_QUALITY_HIGH = "high";
    private static final String VIDEO_QUALITY_MMS = "mms";
    private static final String VIDEO_QUALITY_YOUTUBE = "youtube";


    //manual 3A keys and parameter strings
    public static final String KEY_MANUAL_EXPOSURE = "pref_camera_manual_exp_key";
    public static final String KEY_MANUAL_WB = "pref_camera_manual_wb_key";
    public static final String KEY_MANUAL_FOCUS = "pref_camera_manual_focus_key";

    public static final String KEY_MANUAL_EXPOSURE_MODES = "manual-exp-modes";
    public static final String KEY_MANUAL_WB_MODES = "manual-wb-modes";
    public static final String KEY_MANUAL_FOCUS_MODES = "manual-focus-modes";
    //manual exposure
    public static final String KEY_MIN_EXPOSURE_TIME = "min-exposure-time";
    public static final String KEY_MAX_EXPOSURE_TIME = "max-exposure-time";
    public static final String KEY_EXPOSURE_TIME = "exposure-time";
    public static final String KEY_MIN_ISO = "min-iso";
    public static final String KEY_MAX_ISO = "max-iso";
    public static final String KEY_CONTINUOUS_ISO = "continuous-iso";
    public static final String KEY_MANUAL_ISO = "manual";
    public static final String KEY_CURRENT_ISO = "cur-iso";
    public static final String KEY_CURRENT_EXPOSURE_TIME = "cur-exposure-time";

    //manual WB
    public static final String KEY_MIN_WB_GAIN = "min-wb-gain";
    public static final String KEY_MAX_WB_GAIN = "max-wb-gain";
    public static final String KEY_MANUAL_WB_GAINS = "manual-wb-gains";
    public static final String KEY_MIN_WB_CCT = "min-wb-cct";
    public static final String KEY_MAX_WB_CCT = "max-wb-cct";
    public static final String KEY_MANUAL_WB_CCT = "wb-manual-cct";
    public static final String KEY_MANUAL_WHITE_BALANCE = "manual";
    public static final String KEY_MANUAL_WB_TYPE = "manual-wb-type";
    public static final String KEY_MANUAL_WB_VALUE = "manual-wb-value";

    //manual focus
    public static final String KEY_MIN_FOCUS_SCALE = "min-focus-pos-ratio";
    public static final String KEY_MAX_FOCUS_SCALE = "max-focus-pos-ratio";
    public static final String KEY_MIN_FOCUS_DIOPTER = "min-focus-pos-diopter";
    public static final String KEY_MAX_FOCUS_DIOPTER = "max-focus-pos-diopter";
    public static final String KEY_MANUAL_FOCUS_TYPE = "manual-focus-pos-type";
    public static final String KEY_MANUAL_FOCUS_POSITION = "manual-focus-position";
    public static final String KEY_MANUAL_FOCUS_SCALE = "cur-focus-scale";
    public static final String KEY_MANUAL_FOCUS_DIOPTER = "cur-focus-diopter";

    public static final String KEY_QC_SUPPORTED_MANUAL_FOCUS_MODES = "manual-focus-modes";
    public static final String KEY_QC_SUPPORTED_MANUAL_EXPOSURE_MODES = "manual-exposure-modes";
    public static final String KEY_QC_SUPPORTED_MANUAL_WB_MODES = "manual-wb-modes";

    //Bokeh
    public static final String KEY_QC_IS_BOKEH_MODE_SUPPORTED = "is-bokeh-supported";
    public static final String KEY_QC_IS_BOKEH_MPO_SUPPORTED = "is-bokeh-mpo-supported";
    public static final String KEY_QC_BOKEH_MODE = "bokeh-mode";
    public static final String KEY_QC_BOKEH_MPO_MODE = "bokeh-mpo-mode";
    public static final String KEY_QC_SUPPORTED_DEGREES_OF_BLUR = "supported-blur-degrees";
    public static final String KEY_QC_BOKEH_BLUR_VALUE = "bokeh-blur-value";

    public static final String KEY_TS_MAKEUP_UILABLE       = "pref_camera_tsmakeup_key";
    public static final String KEY_TS_MAKEUP_PARAM         = "tsmakeup"; // on/of
    public static final String KEY_TS_MAKEUP_PARAM_WHITEN  = "tsmakeup_whiten"; // 0~100
    public static final String KEY_TS_MAKEUP_PARAM_CLEAN   = "tsmakeup_clean";  // 0~100
    public static final String KEY_TS_MAKEUP_LEVEL         = "pref_camera_tsmakeup_level_key";
    public static final String KEY_TS_MAKEUP_LEVEL_WHITEN  = "pref_camera_tsmakeup_whiten";
    public static final String KEY_TS_MAKEUP_LEVEL_CLEAN   = "pref_camera_tsmakeup_clean";

    public static final String KEY_REFOCUS_PROMPT = "refocus-prompt";

    public static final String KEY_SHOW_MENU_HELP = "help_menu";

    public static final String KEY_REQUEST_PERMISSION  = "request_permission";

    public static final String KEY_SELFIE_FLASH = "pref_selfie_flash_key";

    public static final String EXPOSURE_DEFAULT_VALUE = "0";

    public static final int CURRENT_VERSION = 5;
    public static final int CURRENT_LOCAL_VERSION = 2;

    public static final int DEFAULT_VIDEO_DURATION = 0; // no limit
    private static final int MMS_VIDEO_DURATION = (CamcorderProfile.get(CamcorderProfile.QUALITY_LOW) != null) ?
          CamcorderProfile.get(CamcorderProfile.QUALITY_LOW).duration :30;
    private static final int YOUTUBE_VIDEO_DURATION = 15 * 60; // 15 mins

    private static final String TAG = "CameraSettings";

    private final Context mContext;
    private final Parameters mParameters;
    private final CameraInfo[] mCameraInfo;
    private final int mCameraId;
    private static final HashMap<Integer, String>
            VIDEO_ENCODER_TABLE = new HashMap<Integer, String>();
    public static final HashMap<String, Integer>
            VIDEO_QUALITY_TABLE = new HashMap<String, Integer>();
    public static final HashMap<String, Integer>
            VIDEO_ENCODER_BITRATE = new HashMap<String, Integer>();

    static {
        //video encoders
        VIDEO_ENCODER_TABLE.put(MediaRecorder.VideoEncoder.H263, "h263");
        VIDEO_ENCODER_TABLE.put(MediaRecorder.VideoEncoder.H264, "h264");
        int h265 = ApiHelper.getIntFieldIfExists(MediaRecorder.VideoEncoder.class,
                       "HEVC", null, MediaRecorder.VideoEncoder.DEFAULT);
        if (h265 == MediaRecorder.VideoEncoder.DEFAULT) {
            h265 = ApiHelper.getIntFieldIfExists(MediaRecorder.VideoEncoder.class,
                       "H265", null, MediaRecorder.VideoEncoder.DEFAULT);
        }
        VIDEO_ENCODER_TABLE.put(h265, "h265");
        VIDEO_ENCODER_TABLE.put(MediaRecorder.VideoEncoder.MPEG_4_SP, "m4v");

        //video qualities
        VIDEO_QUALITY_TABLE.put("4096x2160", CamcorderProfile.QUALITY_4KDCI);
        VIDEO_QUALITY_TABLE.put("3840x2160", CamcorderProfile.QUALITY_2160P);
        VIDEO_QUALITY_TABLE.put("2560x1440", CamcorderProfile.QUALITY_QHD);
        VIDEO_QUALITY_TABLE.put("2048x1080", CamcorderProfile.QUALITY_2k);
        VIDEO_QUALITY_TABLE.put("1920x1080", CamcorderProfile.QUALITY_1080P);
        VIDEO_QUALITY_TABLE.put("1280x720",  CamcorderProfile.QUALITY_720P);
        VIDEO_QUALITY_TABLE.put("720x480",   CamcorderProfile.QUALITY_480P);
        VIDEO_QUALITY_TABLE.put("640x480",   CamcorderProfile.QUALITY_VGA);
        VIDEO_QUALITY_TABLE.put("352x288",   CamcorderProfile.QUALITY_CIF);
        VIDEO_QUALITY_TABLE.put("320x240",   CamcorderProfile.QUALITY_QVGA);
        VIDEO_QUALITY_TABLE.put("176x144",   CamcorderProfile.QUALITY_QCIF);

        //video encoder bitrate
        VIDEO_ENCODER_BITRATE.put("1920x1080:60",  32000000);
        VIDEO_ENCODER_BITRATE.put("1920x1080:120", 50000000);
        VIDEO_ENCODER_BITRATE.put("1280x720:120",  35000000);
        VIDEO_ENCODER_BITRATE.put("1280x720:240",  72000000);
        VIDEO_ENCODER_BITRATE.put("720:480:120",   5200000);

   }

   // Following maps help find a corresponding time-lapse or high-speed quality
   // given a normal quality.
   // Ideally, one should be able to traverse by offsetting +1000, +2000 respectively,
   // But the profile values are messed-up in AOSP
   private static final HashMap<Integer, Integer>
       VIDEO_QUALITY_TO_TIMELAPSE = new HashMap<Integer, Integer>();
   static {
        VIDEO_QUALITY_TO_TIMELAPSE.put(CamcorderProfile.QUALITY_LOW  , CamcorderProfile.QUALITY_TIME_LAPSE_LOW  );
        VIDEO_QUALITY_TO_TIMELAPSE.put(CamcorderProfile.QUALITY_HIGH , CamcorderProfile.QUALITY_TIME_LAPSE_HIGH );
        VIDEO_QUALITY_TO_TIMELAPSE.put(CamcorderProfile.QUALITY_QCIF , CamcorderProfile.QUALITY_TIME_LAPSE_QCIF );
        VIDEO_QUALITY_TO_TIMELAPSE.put(CamcorderProfile.QUALITY_CIF  , CamcorderProfile.QUALITY_TIME_LAPSE_CIF  );
        VIDEO_QUALITY_TO_TIMELAPSE.put(CamcorderProfile.QUALITY_480P , CamcorderProfile.QUALITY_TIME_LAPSE_480P );
        VIDEO_QUALITY_TO_TIMELAPSE.put(CamcorderProfile.QUALITY_720P , CamcorderProfile.QUALITY_TIME_LAPSE_720P );
        VIDEO_QUALITY_TO_TIMELAPSE.put(CamcorderProfile.QUALITY_1080P, CamcorderProfile.QUALITY_TIME_LAPSE_1080P);
        VIDEO_QUALITY_TO_TIMELAPSE.put(CamcorderProfile.QUALITY_QVGA , CamcorderProfile.QUALITY_TIME_LAPSE_QVGA );
        VIDEO_QUALITY_TO_TIMELAPSE.put(CamcorderProfile.QUALITY_2160P, CamcorderProfile.QUALITY_TIME_LAPSE_2160P);
        VIDEO_QUALITY_TO_TIMELAPSE.put(CamcorderProfile.QUALITY_VGA  , CamcorderProfile.QUALITY_TIME_LAPSE_VGA  );
        VIDEO_QUALITY_TO_TIMELAPSE.put(CamcorderProfile.QUALITY_4KDCI, CamcorderProfile.QUALITY_TIME_LAPSE_4KDCI);
   }

   public static int getTimeLapseQualityFor(int quality) {
       return VIDEO_QUALITY_TO_TIMELAPSE.get(quality);
   }

   private static final HashMap<Integer, Integer>
       VIDEO_QUALITY_TO_HIGHSPEED = new HashMap<Integer, Integer>();
   static {
        VIDEO_QUALITY_TO_HIGHSPEED.put(CamcorderProfile.QUALITY_LOW  , CamcorderProfile.QUALITY_HIGH_SPEED_LOW  );
        VIDEO_QUALITY_TO_HIGHSPEED.put(CamcorderProfile.QUALITY_HIGH , CamcorderProfile.QUALITY_HIGH_SPEED_HIGH );
        VIDEO_QUALITY_TO_HIGHSPEED.put(CamcorderProfile.QUALITY_QCIF , -1 ); // does not exist
        VIDEO_QUALITY_TO_HIGHSPEED.put(CamcorderProfile.QUALITY_CIF  , CamcorderProfile.QUALITY_HIGH_SPEED_CIF  );
        VIDEO_QUALITY_TO_HIGHSPEED.put(CamcorderProfile.QUALITY_480P , CamcorderProfile.QUALITY_HIGH_SPEED_480P );
        VIDEO_QUALITY_TO_HIGHSPEED.put(CamcorderProfile.QUALITY_720P , CamcorderProfile.QUALITY_HIGH_SPEED_720P );
        VIDEO_QUALITY_TO_HIGHSPEED.put(CamcorderProfile.QUALITY_1080P, CamcorderProfile.QUALITY_HIGH_SPEED_1080P);
        VIDEO_QUALITY_TO_HIGHSPEED.put(CamcorderProfile.QUALITY_QVGA , -1 ); // does not exist
        VIDEO_QUALITY_TO_HIGHSPEED.put(CamcorderProfile.QUALITY_2160P, CamcorderProfile.QUALITY_HIGH_SPEED_2160P);
        VIDEO_QUALITY_TO_HIGHSPEED.put(CamcorderProfile.QUALITY_VGA  , CamcorderProfile.QUALITY_HIGH_SPEED_VGA  );
        VIDEO_QUALITY_TO_HIGHSPEED.put(CamcorderProfile.QUALITY_4KDCI, CamcorderProfile.QUALITY_HIGH_SPEED_4KDCI);
   } 

   public static int getHighSpeedQualityFor(int quality) {
       return VIDEO_QUALITY_TO_HIGHSPEED.get(quality);
   }

    public CameraSettings(Activity activity, Parameters parameters,
                          int cameraId, CameraInfo[] cameraInfo) {
        mContext = activity;
        mParameters = parameters;
        mCameraId = cameraId;
        mCameraInfo = cameraInfo;
    }

    public PreferenceGroup getPreferenceGroup(int preferenceRes) {
        PreferenceInflater inflater = new PreferenceInflater(mContext);
        PreferenceGroup group =
                (PreferenceGroup) inflater.inflate(preferenceRes);
        if (mParameters != null) initPreference(group);
        return group;
    }

    public static String getSupportedHighestVideoQuality(
            int cameraId, Parameters parameters) {
        // When launching the camera app first time, we will set the video quality
        // to the first one (i.e. highest quality) in the supported list
        List<String> supported = getSupportedVideoQualities(cameraId,parameters);
        assert (supported != null) : "No supported video quality is found";
        return supported.get(0);
    }

    public static void initialCameraPictureSize(
            Context context, Parameters parameters) {
        // When launching the camera app first time, we will set the picture
        // size to the first one in the list defined in "arrays.xml" and is also
        // supported by the driver.
        List<Size> supported = parameters.getSupportedPictureSizes();
        if (supported == null) return;
        for (String candidate : context.getResources().getStringArray(
                R.array.pref_camera_picturesize_entryvalues)) {
            if (setCameraPictureSize(candidate, supported, parameters)) {
                SharedPreferences.Editor editor = ComboPreferences
                        .get(context).edit();
                editor.putString(KEY_PICTURE_SIZE, candidate);
                editor.apply();
                return;
            }
        }
        Log.e(TAG, "No supported picture size found");
    }

    public static void removePreferenceFromScreen(
            PreferenceGroup group, String key) {
        removePreference(group, key);
    }

    public static boolean setCameraPictureSize(
            String candidate, List<Size> supported, Parameters parameters) {
        int index = candidate.indexOf('x');
        if (index == NOT_FOUND) return false;
        int width = Integer.parseInt(candidate.substring(0, index));
        int height = Integer.parseInt(candidate.substring(index + 1));
        for (Size size : supported) {
            if (size.width == width && size.height == height) {
                parameters.setPictureSize(width, height);
                return true;
            }
        }
        return false;
    }

    public static int getMaxVideoDuration(Context context) {
        int duration = 0;  // in milliseconds, 0 means unlimited.
        try {
            duration = context.getResources().getInteger(R.integer.max_video_recording_length);
        } catch (Resources.NotFoundException ex) {
        }
        return duration;
    }

    public static List<String> getSupportedFaceRecognitionModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_FACE_RECOGNITION_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    public static List<String> getSupportedDISModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_DIS_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    public static List<String> getSupportedSeeMoreModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_SEE_MORE_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    public static List<String> getSupportedNoiseReductionModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_NOISE_REDUCTION_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    public static List<String> getSupportedAEBracketingModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_AE_BRACKETING_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    public static List<String> getSupportedCDSModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_CDS_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    public static List<String> getSupportedVideoCDSModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_VIDEO_CDS_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    public static List<String> getSupportedTNRModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_TNR_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    public static List<String> getSupportedVideoTNRModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_VIDEO_TNR_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    public static List<String> getSupportedHDRModes(Parameters params) {
        String str = params.get(KEY_SNAPCAM_SUPPORTED_HDR_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    public static List<String> getSupportedHDRNeed1x(Parameters params) {
        String str = params.get(KEY_SNAPCAM_SUPPORTED_HDR_NEED_1X);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    public List<String> getSupportedAdvancedFeatures(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_AF_BRACKETING_MODES);
        str += ',' + params.get(KEY_QC_SUPPORTED_CF_MODES);
        str += ',' + params.get(KEY_QC_SUPPORTED_OZ_MODES);
        str += ',' + params.get(KEY_QC_SUPPORTED_FSSR_MODES);
        str += ',' + params.get(KEY_QC_SUPPORTED_TP_MODES);
        str += ',' + params.get(KEY_QC_SUPPORTED_MTF_MODES);
        str += ',' + mContext.getString(R.string.pref_camera_advanced_feature_default);
        str += ',' + params.get(KEY_QC_SUPPORTED_RE_FOCUS_MODES);
        str += ',' + params.get(KEY_QC_SUPPORTED_STILL_MORE_MODES);
        return split(str);
    }

    public static List<String> getSupportedAFBracketingModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_AF_BRACKETING_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    public static List<String> getSupportedChromaFlashModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_CF_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    public static List<String> getSupportedOptiZoomModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_OZ_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    public static List<String> getSupportedRefocusModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_RE_FOCUS_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    public static List<String> getSupportedFSSRModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_FSSR_MODES);
         if (str == null) {
             return null;
         }
         return split(str);
    }

    public static List<String> getSupportedTruePortraitModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_TP_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    public static List<String> getSupportedMultiTouchFocusModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_MTF_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    public static List<String> getSupportedStillMoreModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_STILL_MORE_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    // add auto as a valid video snapshot size.
    public static List<String> getSupportedVideoSnapSizes(Parameters params) {
        List<String> sizes = sizeListToStringList(params.getSupportedPictureSizes());
        sizes.add(0, "auto");

        return sizes;
    }

    // Splits a comma delimited string to an ArrayList of String.
    // Return null if the passing string is null or the size is 0.
    private static ArrayList<String> split(String str) {
        if (str == null) return null;

        // Use StringTokenizer because it is faster than split.
        StringTokenizer tokenizer = new StringTokenizer(str, ",");
        ArrayList<String> substrings = new ArrayList<String>();
        while (tokenizer.hasMoreElements()) {
            substrings.add(tokenizer.nextToken());
        }
        return substrings;
    }
    private List<String> getSupportedPictureFormatLists() {
        String str = mParameters.get(KEY_QC_PICTURE_FORMAT);
        if (str == null) {
            str = "jpeg,raw"; // if not set, fall back to default behavior
        }
        return split(str);
    }

   public static List<String> getSupportedFlipMode(Parameters params){
        String str = params.get(KEY_QC_SUPPORTED_FLIP_MODES);
        if(str == null)
            return null;

        return split(str);
    }

    private static List<String> getSupportedVideoEncoders() {
        ArrayList<String> supported = new ArrayList<String>();
        String str = null;
        List<VideoEncoderCap> videoEncoders = EncoderCapabilities.getVideoEncoders();
        for (VideoEncoderCap videoEncoder: videoEncoders) {
            str = VIDEO_ENCODER_TABLE.get(videoEncoder.mCodec);
            if (str != null) {
                supported.add(str);
            }
        }
        return supported;

    }

    private void qcomInitPreferences(PreferenceGroup group){
        //Qcom Preference add here
        ListPreference powerMode = group.findPreference(KEY_POWER_MODE);
        ListPreference zsl = group.findPreference(KEY_ZSL);
        ListPreference colorEffect = group.findPreference(KEY_COLOR_EFFECT);
        ListPreference faceDetection = group.findPreference(KEY_FACE_DETECTION);
        ListPreference touchAfAec = group.findPreference(KEY_TOUCH_AF_AEC);
        ListPreference selectableZoneAf = group.findPreference(KEY_SELECTABLE_ZONE_AF);
        ListPreference saturation = group.findPreference(KEY_SATURATION);
        ListPreference contrast = group.findPreference(KEY_CONTRAST);
        ListPreference sharpness = group.findPreference(KEY_SHARPNESS);
        ListPreference autoExposure = group.findPreference(KEY_AUTOEXPOSURE);
        ListPreference antiBanding = group.findPreference(KEY_ANTIBANDING);
        ListPreference mIso = group.findPreference(KEY_ISO);
        ListPreference lensShade = group.findPreference(KEY_LENSSHADING);
        ListPreference histogram = group.findPreference(KEY_HISTOGRAM);
        ListPreference denoise = group.findPreference(KEY_DENOISE);
        ListPreference redeyeReduction = group.findPreference(KEY_REDEYE_REDUCTION);
        ListPreference aeBracketing = group.findPreference(KEY_AE_BRACKET_HDR);
        ListPreference advancedFeatures = group.findPreference(KEY_ADVANCED_FEATURES);
        ListPreference faceRC = group.findPreference(KEY_FACE_RECOGNITION);
        ListPreference jpegQuality = group.findPreference(KEY_JPEG_QUALITY);
        ListPreference videoSnapSize = group.findPreference(KEY_VIDEO_SNAPSHOT_SIZE);
        ListPreference videoHdr = group.findPreference(KEY_VIDEO_HDR);
        ListPreference pictureFormat = group.findPreference(KEY_PICTURE_FORMAT);
        ListPreference longShot = group.findPreference(KEY_LONGSHOT);
        ListPreference auto_hdr = group.findPreference(KEY_AUTO_HDR);
        ListPreference hdr_mode = group.findPreference(KEY_HDR_MODE);
        ListPreference hdr_need_1x = group.findPreference(KEY_HDR_NEED_1X);
        ListPreference cds_mode = group.findPreference(KEY_CDS_MODE);
        ListPreference video_cds_mode = group.findPreference(KEY_VIDEO_CDS_MODE);
        ListPreference tnr_mode = group.findPreference(KEY_TNR_MODE);
        ListPreference video_tnr_mode = group.findPreference(KEY_VIDEO_TNR_MODE);
        ListPreference manualFocus = group.findPreference(KEY_MANUAL_FOCUS);
        ListPreference manualExposure = group.findPreference(KEY_MANUAL_EXPOSURE);
        ListPreference manualWB = group.findPreference(KEY_MANUAL_WB);
        ListPreference instantCapture = group.findPreference(KEY_INSTANT_CAPTURE);
        ListPreference bokehMode = group.findPreference(KEY_BOKEH_MODE);
        ListPreference bokehMpo = group.findPreference(KEY_BOKEH_MPO);
        ListPreference bokehBlurDegree = group.findPreference(KEY_BOKEH_BLUR_VALUE);

        if (instantCapture != null) {
            if (!isInstantCaptureSupported(mParameters)) {
                removePreference(group, instantCapture.getKey());
            }
        }

        if (bokehMode != null) {
            if (!isBokehModeSupported(mParameters)) {
                removePreference(group, bokehMode.getKey());
                removePreference(group, bokehBlurDegree.getKey());
            }
        }

        if (bokehMpo != null) {
            if (!isBokehMPOSupported(mParameters)) {
                removePreference(group, bokehMpo.getKey());
            }
        }

        if (hdr_need_1x != null) {
            filterUnsupportedOptions(group,
                    hdr_need_1x, getSupportedHDRNeed1x(mParameters));
        }

        if (hdr_mode != null) {
            filterUnsupportedOptions(group,
                    hdr_mode, getSupportedHDRModes(mParameters));
        }

        if (cds_mode != null) {
            filterUnsupportedOptions(group,
                    cds_mode, getSupportedCDSModes(mParameters));
        }

        if (video_cds_mode != null) {
            filterUnsupportedOptions(group,
                    video_cds_mode, getSupportedVideoCDSModes(mParameters));
        }

        if (tnr_mode != null) {
            filterUnsupportedOptions(group,
                    tnr_mode, getSupportedTNRModes(mParameters));
        }

        if (video_tnr_mode != null) {
            filterUnsupportedOptions(group,
                    video_tnr_mode, getSupportedVideoTNRModes(mParameters));
        }

        ListPreference videoRotation = group.findPreference(KEY_VIDEO_ROTATION);

        if (touchAfAec != null) {
            filterUnsupportedOptions(group,
                    touchAfAec, mParameters.getSupportedTouchAfAec());
        }

        if (!mParameters.isPowerModeSupported() && powerMode != null) {
            removePreference(group, powerMode.getKey());
        }

        if (selectableZoneAf != null) {
            filterUnsupportedOptions(group,
                    selectableZoneAf, mParameters.getSupportedSelectableZoneAf());
        }

        if (mIso != null) {
            filterUnsupportedOptions(group,
                    mIso, mParameters.getSupportedIsoValues());
        }

        if (redeyeReduction != null) {
            filterUnsupportedOptions(group,
                    redeyeReduction, mParameters.getSupportedRedeyeReductionModes());
        }

        if (denoise != null) {
            filterUnsupportedOptions(group,
                    denoise, mParameters.getSupportedDenoiseModes());
        }

        if (videoHdr != null) {
            filterUnsupportedOptions(group,
                    videoHdr, mParameters.getSupportedVideoHDRModes());
        }

        if (colorEffect != null) {
            filterUnsupportedOptions(group,
                    colorEffect, mParameters.getSupportedColorEffects());
        }

        if (aeBracketing != null) {
            filterUnsupportedOptions(group,
                     aeBracketing, getSupportedAEBracketingModes(mParameters));
        }

        if (antiBanding != null) {
            filterUnsupportedOptions(group,
                    antiBanding, mParameters.getSupportedAntibanding());
        }

        if (faceRC != null) {
            filterUnsupportedOptions(group,
                    faceRC, getSupportedFaceRecognitionModes(mParameters));
        }

        if (autoExposure != null) {
            filterUnsupportedOptions(group,
                    autoExposure, mParameters.getSupportedAutoexposure());
        }

        if(videoSnapSize != null) {
            filterUnsupportedOptions(group, videoSnapSize, getSupportedVideoSnapSizes(mParameters));
            filterSimilarPictureSize(group, videoSnapSize);
        }

        if (histogram!= null) {
            filterUnsupportedOptions(group,
                    histogram, mParameters.getSupportedHistogramModes());
        }

        if (pictureFormat!= null) {
            filterUnsupportedOptions(group,
                    pictureFormat, getSupportedPictureFormatLists());
        }

        if(advancedFeatures != null) {
            filterUnsupportedOptions(group,
                    advancedFeatures, getSupportedAdvancedFeatures(mParameters));
        }
        if (longShot!= null && !isLongshotSupported(mParameters)) {
            removePreference(group, longShot.getKey());
        }

        if (videoRotation != null) {
            filterUnsupportedOptions(group,
                    videoRotation, mParameters.getSupportedVideoRotationValues());
        }

        if (manualFocus != null) {
            filterUnsupportedOptions(group,
                    manualFocus, getSupportedManualFocusModes(mParameters));
        }

        if (manualWB != null) {
            filterUnsupportedOptions(group,
                    manualWB, getSupportedManualWBModes(mParameters));
        }

        if (manualExposure != null) {
            filterUnsupportedOptions(group,
                    manualExposure, getSupportedManualExposureModes(mParameters));
        }
    }

    private void initPreference(PreferenceGroup group) {
        ListPreference videoQuality = group.findPreference(KEY_VIDEO_QUALITY);
        ListPreference timeLapseInterval = group.findPreference(KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL);
        ListPreference pictureSize = group.findPreference(KEY_PICTURE_SIZE);
        ListPreference whiteBalance =  group.findPreference(KEY_WHITE_BALANCE);
        ListPreference chromaFlash = group.findPreference(KEY_QC_CHROMA_FLASH);
        ListPreference sceneMode = group.findPreference(KEY_SCENE_MODE);
        ListPreference flashMode = group.findPreference(KEY_FLASH_MODE);
        ListPreference focusMode = group.findPreference(KEY_FOCUS_MODE);
        IconListPreference exposure =
                (IconListPreference) group.findPreference(KEY_EXPOSURE);
        IconListPreference cameraIdPref =
                (IconListPreference) group.findPreference(KEY_CAMERA_ID);
        ListPreference videoFlashMode =
                group.findPreference(KEY_VIDEOCAMERA_FLASH_MODE);
        ListPreference videoEffect = group.findPreference(KEY_VIDEO_EFFECT);
        ListPreference cameraHdr = group.findPreference(KEY_CAMERA_HDR);
        ListPreference disMode = group.findPreference(KEY_DIS);
        ListPreference cameraHdrPlus = group.findPreference(KEY_CAMERA_HDR_PLUS);
        ListPreference videoHfrMode =
                group.findPreference(KEY_VIDEO_HIGH_FRAME_RATE);
        ListPreference seeMoreMode = group.findPreference(KEY_SEE_MORE);
        ListPreference videoEncoder = group.findPreference(KEY_VIDEO_ENCODER);
        ListPreference noiseReductionMode = group.findPreference(KEY_NOISE_REDUCTION);

        // Since the screen could be loaded from different resources, we need
        // to check if the preference is available here

        if (noiseReductionMode != null) {
            filterUnsupportedOptions(group, noiseReductionMode,
                    getSupportedNoiseReductionModes(mParameters));
        }

        if (seeMoreMode != null) {
            filterUnsupportedOptions(group, seeMoreMode,
                    getSupportedSeeMoreModes(mParameters));
        }

        if ((videoHfrMode != null) &&
            (mParameters.getSupportedHfrSizes() == null)) {
                filterUnsupportedOptions(group, videoHfrMode, null);
        }

        if (videoQuality != null) {
            filterUnsupportedOptions(group, videoQuality, getSupportedVideoQualities(
                   mCameraId,mParameters));
        }

        if (videoEncoder != null) {
            filterUnsupportedOptions(group, videoEncoder, getSupportedVideoEncoders());
        }

        if (pictureSize != null) {
            filterUnsupportedOptions(group, pictureSize, sizeListToStringList(
                    mParameters.getSupportedPictureSizes()));
            filterSimilarPictureSize(group, pictureSize);
        }
        if (whiteBalance != null) {
            filterUnsupportedOptions(group,
                    whiteBalance, mParameters.getSupportedWhiteBalance());
        }

        if (chromaFlash != null) {
            List<String> supportedAdvancedFeatures =
                    getSupportedAdvancedFeatures(mParameters);
            if (!CameraUtil.isSupported(
                        mContext.getString(R.string
                            .pref_camera_advanced_feature_value_chromaflash_on),
                        supportedAdvancedFeatures)) {
                removePreference(group, chromaFlash.getKey());
            }
        }

        if (sceneMode != null) {
            List<String> supportedSceneModes = mParameters.getSupportedSceneModes();
            List<String> supportedAdvancedFeatures =
                    getSupportedAdvancedFeatures(mParameters);
            if (CameraUtil.isSupported(
                        mContext.getString(R.string
                                .pref_camera_advanced_feature_value_refocus_on),
                        supportedAdvancedFeatures)) {
                supportedSceneModes.add(mContext.getString(R.string
                            .pref_camera_advanced_feature_value_refocus_on));
            }
            if (CameraUtil.isSupported(
                        mContext.getString(R.string
                                .pref_camera_advanced_feature_value_optizoom_on),
                        supportedAdvancedFeatures)) {
                supportedSceneModes.add(mContext.getString(R.string
                            .pref_camera_advanced_feature_value_optizoom_on));
            }
            filterUnsupportedOptions(group, sceneMode, supportedSceneModes);
        }
        if (flashMode != null) {
            filterUnsupportedOptions(group,
                    flashMode, mParameters.getSupportedFlashModes());
        }
        if (disMode != null) {
            filterUnsupportedOptions(group,
                    disMode, getSupportedDISModes(mParameters));
        }
        if (focusMode != null) {
            if (!CameraUtil.isFocusAreaSupported(mParameters)) {
                filterUnsupportedOptions(group,
                        focusMode, mParameters.getSupportedFocusModes());
            }
        }
        if (videoFlashMode != null) {
            filterUnsupportedOptions(group,
                    videoFlashMode, mParameters.getSupportedFlashModes());
        }
        if (exposure != null) buildExposureCompensation(group, exposure);
        if (cameraIdPref != null) buildCameraId(group, cameraIdPref);

        if (timeLapseInterval != null) {
            resetIfInvalid(timeLapseInterval);
        }
        if (videoEffect != null) {
            filterUnsupportedOptions(group, videoEffect, null);
        }
        if (cameraHdr != null && (!ApiHelper.HAS_CAMERA_HDR
                || !CameraUtil.isCameraHdrSupported(mParameters))) {
            removePreference(group, cameraHdr.getKey());
        }
        int frontCameraId = CameraHolder.instance().getFrontCameraId();
        boolean isFrontCamera = (frontCameraId == mCameraId);
        if (cameraHdrPlus != null && (!ApiHelper.HAS_CAMERA_HDR_PLUS ||
                !GcamHelper.hasGcamCapture() || isFrontCamera)) {
            removePreference(group, cameraHdrPlus.getKey());
        }

        if (SystemProperties.getBoolean("persist.env.camera.saveinsd", false)) {
            final String CAMERA_SAVEPATH_SDCARD = "1";
            final int CAMERA_SAVEPATH_SDCARD_IDX = 1;
            final int CAMERA_SAVEPATH_PHONE_IDX = 0;
            ListPreference savePath = group.findPreference(KEY_CAMERA_SAVEPATH);
            SharedPreferences pref = group.getSharedPreferences();
            String savePathValue = null;
            if (pref != null) {
                savePathValue = pref.getString(KEY_CAMERA_SAVEPATH, CAMERA_SAVEPATH_SDCARD);
            }
            if (savePath != null && CAMERA_SAVEPATH_SDCARD.equals(savePathValue)) {
                // If sdCard is present, set sdCard as default save path.
                // Only for the first time when camera start.
                if (SDCard.instance().isWriteable()) {
                    Log.d(TAG, "set Sdcard as save path.");
                    savePath.setValueIndex(CAMERA_SAVEPATH_SDCARD_IDX);
                } else {
                    Log.d(TAG, "set Phone as save path when sdCard is unavailable.");
                    savePath.setValueIndex(CAMERA_SAVEPATH_PHONE_IDX);
                }
           }
        }

        qcomInitPreferences(group);
    }

    private void buildExposureCompensation(
            PreferenceGroup group, IconListPreference exposure) {
        int max = mParameters.getMaxExposureCompensation();
        int min = mParameters.getMinExposureCompensation();
        if (max == 0 && min == 0) {
            removePreference(group, exposure.getKey());
            return;
        }
        float step = mParameters.getExposureCompensationStep();

        // show only integer values for exposure compensation
        int maxValue = Math.min(3, (int) Math.floor(max * step));
        int minValue = Math.max(-3, (int) Math.ceil(min * step));
        String explabel = mContext.getResources().getString(R.string.pref_exposure_label);
        CharSequence entries[] = new CharSequence[maxValue - minValue + 1];
        CharSequence entryValues[] = new CharSequence[maxValue - minValue + 1];
        CharSequence labels[] = new CharSequence[maxValue - minValue + 1];
        int[] icons = new int[maxValue - minValue + 1];
        TypedArray iconIds = mContext.getResources().obtainTypedArray(
                R.array.pref_camera_exposure_icons);
        for (int i = minValue; i <= maxValue; ++i) {
            entryValues[i - minValue] = Integer.toString(Math.round(i / step));
            StringBuilder builder = new StringBuilder();
            if (i > 0) builder.append('+');
            entries[i - minValue] = builder.append(i).toString();
            labels[i - minValue] = explabel + " " + builder.toString();
            icons[i - minValue] = iconIds.getResourceId(3 + i, 0);
        }
        exposure.setUseSingleIcon(true);
        exposure.setEntries(entries);
        exposure.setLabels(labels);
        exposure.setEntryValues(entryValues);
    }

    private void buildCameraId(
            PreferenceGroup group, IconListPreference preference) {
        int numOfCameras = mCameraInfo.length;
        if (numOfCameras < 2) {
            removePreference(group, preference.getKey());
            return;
        }

//        if (numOfCameras > 2 ) {
//            numOfCameras = 2;
//        }

        CharSequence[] entryValues = new CharSequence[numOfCameras];
        for (int i = 0; i < numOfCameras; ++i) {
            entryValues[i] = "" + i;
        }
        preference.setEntryValues(entryValues);
    }

    private static boolean removePreference(PreferenceGroup group, String key) {
        for (int i = 0, n = group.size(); i < n; i++) {
            CameraPreference child = group.get(i);
            if (child instanceof PreferenceGroup) {
                if (removePreference((PreferenceGroup) child, key)) {
                    return true;
                }
            }
            if (child instanceof ListPreference &&
                    ((ListPreference) child).getKey().equals(key)) {
                group.removePreference(i);
                return true;
            }
        }
        return false;
    }

    private static boolean filterUnsupportedOptions(PreferenceGroup group,
            ListPreference pref, List<String> supported) {

        // Remove the preference if the parameter is not supported or there is
        // only one options for the settings.
        if (supported == null || supported.size() <= 1) {
            removePreference(group, pref.getKey());
            return true;
        }

        pref.filterUnsupported(supported);
        if (pref.getEntries().length <= 1) {
            removePreference(group, pref.getKey());
            return true;
        }

        resetIfInvalid(pref);
        return false;
    }

    private static boolean filterSimilarPictureSize(PreferenceGroup group,
            ListPreference pref) {
        pref.filterDuplicated();
        if (pref.getEntries().length <= 1) {
            removePreference(group, pref.getKey());
            return true;
        }
        resetIfInvalid(pref);
        return false;
    }

    private static void resetIfInvalid(ListPreference pref) {
        // Set the value to the first entry if it is invalid.
        String value = pref.getValue();
        if (pref.findIndexOfValue(value) == NOT_FOUND) {
            pref.setValueIndex(0);
        }
    }

    private static List<String> sizeListToStringList(List<Size> sizes) {
        ArrayList<String> list = new ArrayList<String>();
        for (Size size : sizes) {
            list.add(String.format(Locale.ENGLISH, "%dx%d", size.width, size.height));
        }
        return list;
    }

    public static void upgradeLocalPreferences(SharedPreferences pref) {
        int version;
        try {
            version = pref.getInt(KEY_LOCAL_VERSION, 0);
        } catch (Exception ex) {
            version = 0;
        }
        if (version == CURRENT_LOCAL_VERSION) return;

        SharedPreferences.Editor editor = pref.edit();
        if (version == 1) {
            // We use numbers to represent the quality now. The quality definition is identical to
            // that of CamcorderProfile.java.
            editor.remove("pref_video_quality_key");
        }
        editor.putInt(KEY_LOCAL_VERSION, CURRENT_LOCAL_VERSION);
        editor.apply();
    }

    public static void upgradeGlobalPreferences(SharedPreferences pref, Context context) {
        upgradeOldVersion(pref, context);
        upgradeCameraId(pref);
    }

    private static void upgradeOldVersion(SharedPreferences pref, Context context) {
        int version;
        try {
            version = pref.getInt(KEY_VERSION, 0);
        } catch (Exception ex) {
            version = 0;
        }
        if (version == CURRENT_VERSION) return;

        SharedPreferences.Editor editor = pref.edit();
        if (version == 0) {
            // We won't use the preference which change in version 1.
            // So, just upgrade to version 1 directly
            version = 1;
        }
        if (version == 1) {
            // Change jpeg quality {65,75,85} to {normal,fine,superfine}
            String quality = pref.getString(KEY_JPEG_QUALITY, "85");
            if (quality.equals("65")) {
                quality = "normal";
            } else if (quality.equals("75")) {
                quality = "fine";
            } else {
                quality = context.getString(R.string.pref_camera_jpegquality_default);
            }
            editor.putString(KEY_JPEG_QUALITY, quality);
            version = 2;
        }
        if (version == 2) {
            editor.putString(KEY_RECORD_LOCATION,
                    pref.getBoolean(KEY_RECORD_LOCATION, false)
                    ? RecordLocationPreference.VALUE_ON
                    : RecordLocationPreference.VALUE_NONE);
            version = 3;
        }
        if (version == 3) {
            // Just use video quality to replace it and
            // ignore the current settings.
            editor.remove("pref_camera_videoquality_key");
            editor.remove("pref_camera_video_duration_key");
        }

        editor.putInt(KEY_VERSION, CURRENT_VERSION);
        editor.apply();
    }

    private static void upgradeCameraId(SharedPreferences pref) {
        // The id stored in the preference may be out of range if we are running
        // inside the emulator and a webcam is removed.
        // Note: This method accesses the global preferences directly, not the
        // combo preferences.
        int cameraId = readPreferredCameraId(pref);
        if (cameraId == 0) return;  // fast path

        int n = CameraHolder.instance().getNumberOfCameras();
        if (cameraId < 0 || cameraId >= n) {
            cameraId = 0;
        }
        writePreferredCameraId(pref, cameraId);
    }

    public static int readPreferredCameraId(SharedPreferences pref) {
        String rearCameraId = Integer.toString(
                CameraHolder.instance().getBackCameraId());
        return Integer.parseInt(pref.getString(KEY_CAMERA_ID, rearCameraId));
    }

    public static void writePreferredCameraId(SharedPreferences pref,
            int cameraId) {
        Editor editor = pref.edit();
        editor.putString(KEY_CAMERA_ID, Integer.toString(cameraId));
        editor.apply();
    }

    public static int readExposure(ComboPreferences preferences) {
        String exposure = preferences.getString(
                CameraSettings.KEY_EXPOSURE,
                EXPOSURE_DEFAULT_VALUE);
        try {
            return Integer.parseInt(exposure);
        } catch (Exception ex) {
            Log.e(TAG, "Invalid exposure: " + exposure);
        }
        return 0;
    }

    public static void restorePreferences(Context context,
            ComboPreferences preferences, Parameters parameters) {
        int currentCameraId = readPreferredCameraId(preferences);

        // Clear the preferences of both cameras.
        int backCameraId = CameraHolder.instance().getBackCameraId();
        if (backCameraId != -1) {
            preferences.setLocalId(context, backCameraId);
            Editor editor = preferences.edit();
            editor.clear();
            editor.apply();
        }
        int frontCameraId = CameraHolder.instance().getFrontCameraId();
        if (frontCameraId != -1) {
            preferences.setLocalId(context, frontCameraId);
            Editor editor = preferences.edit();
            editor.clear();
            editor.apply();
        }

        // Switch back to the preferences of the current camera. Otherwise,
        // we may write the preference to wrong camera later.
        preferences.setLocalId(context, currentCameraId);

        upgradeGlobalPreferences(preferences.getGlobal(), context);
        upgradeLocalPreferences(preferences.getLocal());

        // Write back the current camera id because parameters are related to
        // the camera. Otherwise, we may switch to the front camera but the
        // initial picture size is that of the back camera.
        initialCameraPictureSize(context, parameters);
        writePreferredCameraId(preferences, currentCameraId);
    }
    private static boolean checkSupportedVideoQuality(Parameters parameters,int width, int height){
        List <Size> supported = parameters.getSupportedVideoSizes();
        int flag = 0;
        for (Size size : supported){
            //since we are having two profiles with same height, we are checking with height
            if (size.height == 480) {
                if (size.height == height && size.width == width) {
                    flag = 1;
                    break;
                }
            } else {
                if (size.width == width) {
                    flag = 1;
                    break;
                }
            }
        }
        if (flag == 1)
            return true;

        return false;
    }
    private static ArrayList<String> getSupportedVideoQuality(int cameraId,Parameters parameters) {
        ArrayList<String> supported = new ArrayList<String>();
        // Check for supported quality
        if (ApiHelper.HAS_FINE_RESOLUTION_QUALITY_LEVELS) {
        getFineResolutionQuality(supported,cameraId,parameters);
        } else {
            supported.add(Integer.toString(CamcorderProfile.QUALITY_HIGH));
            CamcorderProfile high = CamcorderProfile.get(
                    cameraId, CamcorderProfile.QUALITY_HIGH);
            CamcorderProfile low = CamcorderProfile.get(
                    cameraId, CamcorderProfile.QUALITY_LOW);
            if (high.videoFrameHeight * high.videoFrameWidth >
                    low.videoFrameHeight * low.videoFrameWidth) {
                supported.add(Integer.toString(CamcorderProfile.QUALITY_LOW));
            }
        }

        return supported;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private static void getFineResolutionQuality(ArrayList<String> supported,
                                                 int cameraId,Parameters parameters) {

        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_4KDCI)) {
           if (checkSupportedVideoQuality(parameters,4096,2160)) {
              supported.add(Integer.toString(CamcorderProfile.QUALITY_4KDCI));
           }
        }

        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_2160P)) {
           if (checkSupportedVideoQuality(parameters,3840,2160)) {
              supported.add(Integer.toString(CamcorderProfile.QUALITY_2160P));
           }
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_1080P)) {
           if (checkSupportedVideoQuality(parameters,1920,1080)){
              supported.add(Integer.toString(CamcorderProfile.QUALITY_1080P));
           }
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)) {
           if (checkSupportedVideoQuality(parameters,1280,720)){
              supported.add(Integer.toString(CamcorderProfile.QUALITY_720P));
           }
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
           if (checkSupportedVideoQuality(parameters,720,480)){
              supported.add(Integer.toString(CamcorderProfile.QUALITY_480P));
           }
        }

        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_VGA)) {
           if (checkSupportedVideoQuality(parameters,640,480)){
              supported.add(Integer.toString(CamcorderProfile.QUALITY_VGA));
           }
        }

        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_CIF)) {
           if (checkSupportedVideoQuality(parameters,352,288)){
              supported.add(Integer.toString(CamcorderProfile.QUALITY_CIF));
           }
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QVGA)) {
           if (checkSupportedVideoQuality(parameters,320,240)){
              supported.add(Integer.toString(CamcorderProfile.QUALITY_QVGA));
           }
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QCIF)) {
           if (checkSupportedVideoQuality(parameters,176,144)){
              supported.add(Integer.toString(CamcorderProfile.QUALITY_QCIF));
           }
        }

    }

    public static ArrayList<String> getSupportedVideoQualities(int cameraId,Parameters parameters) {
        ArrayList<String> supported = new ArrayList<String>();
        List<String> temp = sizeListToStringList(parameters.getSupportedVideoSizes());
        for (String videoSize : temp) {
            if (VIDEO_QUALITY_TABLE.containsKey(videoSize)) {
                int profile = VIDEO_QUALITY_TABLE.get(videoSize);
                if (CamcorderProfile.hasProfile(cameraId, profile)) {
                      supported.add(videoSize);
                }
            }
        }
        return supported;
    }
    public static int getVideoDurationInMillis(String quality) {
        if (VIDEO_QUALITY_MMS.equals(quality)) {
            return MMS_VIDEO_DURATION * 1000;
        } else if (VIDEO_QUALITY_YOUTUBE.equals(quality)) {
            return YOUTUBE_VIDEO_DURATION * 1000;
        }
        return DEFAULT_VIDEO_DURATION * 1000;
    }

    public static boolean isInternalPreviewSupported(Parameters params) {
        boolean ret = false;
        if (null != params) {
            String val = params.get(KEY_INTERNAL_PREVIEW_RESTART);
            if ((null != val) && (TRUE.equals(val))) {
                ret = true;
            }
        }
        return ret;
    }

    public static boolean isLongshotSupported(Parameters params) {
        boolean ret = false;
        if (null != params) {
            String val = params.get(KEY_QC_LONGSHOT_SUPPORTED);
            if ((null != val) && (TRUE.equals(val))) {
                ret = true;
            }
        }
        return ret;
    }

    public static boolean isZSLHDRSupported(Parameters params) {
        boolean ret = false;
        if (null != params) {
            String val = params.get(KEY_QC_ZSL_HDR_SUPPORTED);
            if ((null != val) && (TRUE.equals(val))) {
                ret = true;
            }
        }
        return ret;
    }

    public static List<String> getSupportedManualExposureModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_MANUAL_EXPOSURE_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    public static List<String> getSupportedManualFocusModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_MANUAL_FOCUS_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    public static List<String> getSupportedManualWBModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_MANUAL_WB_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    public static boolean isInstantCaptureSupported(Parameters params) {
        boolean ret = false;
        if (null != params) {
            String val = params.get(KEY_QC_INSTANT_CAPTURE_VALUES);
            if (null != val) {
                ret = true;
            }
        }
        return ret;
    }

    public static boolean isBokehModeSupported(Parameters params) {
        boolean ret = false;
        if (null != params) {
            String val = params.get(KEY_QC_IS_BOKEH_MODE_SUPPORTED);
            if ("1".equals(val)) {
                ret = true;
            }
        }
        return ret;
    }

    public static boolean isBokehMPOSupported(Parameters params) {
        boolean ret = false;
        if (null != params) {
            String val = params.get(KEY_QC_IS_BOKEH_MPO_SUPPORTED);
            if ("1".equals(val)) {
                ret = true;
            }
        }
        return ret;
    }

    public static List<String> getSupportedDegreesOfBlur(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_DEGREES_OF_BLUR);
        if (str == null) {
            return null;
        }
        Log.d(TAG,"getSupportedDegreesOfBlur str =" +str);
        return split(str);
    }
}
