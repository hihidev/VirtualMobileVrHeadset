/*******************************************************************************

Filename    :   Main.cpp
Content     :   Base project for mobile VR samples
Created     :   February 21, 2018
Authors     :   John Carmack, J.M.P. van Waveren, Jonathan Wright
Language    :   C++

Copyright:	Copyright (c) Facebook Technologies, LLC and its affiliates. All rights reserved.

*******************************************************************************/

//#include "Platform/Android/Android.h"

#include <android/window.h>
#include <android/native_window_jni.h>
#include <android_native_app_glue.h>

#include <memory>

#include "Appl.h"
#include "VrCinema.h"

extern "C" {

VrCinema* appPtr = nullptr;

long Java_dev_hihi_virtualmobilevrheadset_VrActivity_nativeSetAppInterface(
    JNIEnv* jni,
    jclass clazz,
    jobject activity) {
    ALOG("nativeSetAppInterface %p", appPtr);
    return reinterpret_cast<jlong>(appPtr);
}

void Java_dev_hihi_virtualmobilevrheadset_VrActivity_nativeSetVideoSize(
    JNIEnv* jni,
    jclass clazz,
    jlong interfacePtr,
    int width,
    int height) {
    ALOG("nativeSetVideoSize %p", interfacePtr);
    VrCinema* cinema = appPtr;
    if (cinema && interfacePtr) {
        cinema->SetVideoSize(width, height);
    } else {
        ALOG("nativeSetVideoSize %p cinema == NULL", cinema);
    }
}

jobject Java_dev_hihi_virtualmobilevrheadset_VrActivity_nativePrepareNewVideo(
    JNIEnv* jni,
    jclass clazz,
    jlong interfacePtr) {
    ALOG("nativePrepareNewVideo %p", interfacePtr);
    jobject surfaceTexture = nullptr;
    VrCinema* cinema = appPtr;
    if (cinema && interfacePtr) {
        cinema->GetScreenSurface(surfaceTexture);
    } else {
        ALOG("nativePrepareNewVideo %p cinema == NULL", cinema);
    }
    return surfaceTexture;
}

} // extern "C"

//==============================================================
// android_main
//==============================================================
void android_main(struct android_app* app) {
    appPtr = nullptr;
    std::unique_ptr<VrCinema> appl = std::unique_ptr<VrCinema>(new VrCinema(0, 0, 0, 0));
    appPtr = appl.get();
    appl->Run(app);
    appPtr = nullptr;
}
