#
# Copyright (C) 2017-2018 The LineageOS Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res \
    frameworks/support/design/res \
    frameworks/support/v7/appcompat/res \
    frameworks/support/v7/cardview/res

LOCAL_SRC_FILES := $(call all-java-files-under, java)

LOCAL_AAPT_FLAGS := \
    --auto-add-overlay \
    --extra-packages android.support.constraint \
    --extra-packages android.support.design \
    --extra-packages android.support.transition \
    --extra-packages android.support.v7.appcompat \
    --extra-packages android.support.v7.cardview

LOCAL_STATIC_JAVA_LIBRARIES += \
    android-support-annotations \
    android-support-constraint-layout-solver \
    android-support-design \
    android-support-v4 \
    android-support-v7-appcompat \
    android-support-v7-cardview \
    android-support-v7-recyclerview

LOCAL_STATIC_JAVA_AAR_LIBRARIES += \
    android-support-constraint-layout \
    android-support-transition-recorder

LOCAL_PACKAGE_NAME := Recorder
LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true
#LOCAL_PROGUARD_FLAG_FILES := $(LOCAL_PATH)/../../proguard-rules.pro

LOCAL_PRIVATE_PLATFORM_APIS := true

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += \
    android-support-constraint-layout:../../libs/constraint-layout-1.1.0.aar \
    android-support-constraint-layout-solver:../../libs/constraint-layout-solver-1.1.0.jar \
    android-support-transition-recorder:../../libs/transition-27.0.2.aar

include $(BUILD_MULTI_PREBUILT)

include $(call all-makefiles-under,$(LOCAL_PATH))
