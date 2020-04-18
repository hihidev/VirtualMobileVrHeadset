LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

include $(LOCAL_PATH)/cflags.mk

LOCAL_MODULE			:= vrcinema

LOCAL_C_INCLUDES 	:= 	$(LOCAL_PATH)/../../../../ovr_sdk/VrSamples/SampleFramework/Src \
						$(LOCAL_PATH)/../../../../ovr_sdk/VrApi/Include \
						$(LOCAL_PATH)/../../../../ovr_sdk/1stParty/OVR/Include \
						$(LOCAL_PATH)/../../../../ovr_sdk/1stParty/utilities/include \
						$(LOCAL_PATH)/../../../../ovr_sdk/3rdParty/stb/src \


LOCAL_SRC_FILES		:= 	main.cpp \
					    VrCinema.cpp \

# include default libraries
LOCAL_LDLIBS 			:= -llog -landroid -lGLESv3 -lEGL -lz
LOCAL_STATIC_LIBRARIES 	:= sampleframework
LOCAL_SHARED_LIBRARIES	:= vrapi

include $(BUILD_SHARED_LIBRARY)

$(call import-module,VrSamples/SampleFramework/Projects/Android/jni)
$(call import-module,VrApi/Projects/AndroidPrebuilt/jni)
