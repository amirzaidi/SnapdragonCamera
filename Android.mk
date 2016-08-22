LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v13
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v4
LOCAL_STATIC_JAVA_LIBRARIES += xmp_toolkit

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_SRC_FILES += $(call all-java-files-under, src_pd)
LOCAL_SRC_FILES += $(call all-java-files-under, src_pd_gcam)
LOCAL_SRC_FILES += $(call all-renderscript-files-under, rs)

LOCAL_RESOURCE_DIR += $(LOCAL_PATH)/res

include $(LOCAL_PATH)/version.mk
LOCAL_AAPT_FLAGS := \
        --auto-add-overlay \
        --version-name "$(version_name_package)" \
        --version-code $(version_code_package) \

LOCAL_PACKAGE_NAME := SnapdragonCamera
LOCAL_PRIVILEGED_MODULE := true

#LOCAL_SDK_VERSION := current
LOCAL_RENDERSCRIPT_TARGET_API := 23

LOCAL_OVERRIDES_PACKAGES := Camera2

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

# If this is an unbundled build (to install separately) then include
# the libraries in the APK, otherwise just put them in /system/lib and
# leave them out of the APK
ifneq (,$(TARGET_BUILD_APPS))
  LOCAL_JNI_SHARED_LIBRARIES := libjni_snapcammosaic libjni_snapcamtinyplanet libjni_imageutil
else
  LOCAL_REQUIRED_MODULES := libjni_snapcammosaic libjni_snapcamtinyplanet libjni_imageutil
endif

include $(BUILD_PACKAGE)

include $(call all-makefiles-under, $(LOCAL_PATH))
