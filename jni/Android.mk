LOCAL_PATH:= $(call my-dir)

common_SRC_FILES:= \
	helper.c \
	info_kghost_android_openvpn_FileDescriptorHolder.c \
	info_kghost_android_openvpn_ManagementSocket.c

include $(CLEAR_VARS)
LOCAL_SRC_FILES:= $(common_SRC_FILES)
LOCAL_CFLAGS:= $(common_CFLAGS)
LOCAL_C_INCLUDES:= $(common_C_INCLUDES)

LOCAL_SHARED_LIBRARIES:= $(common_SHARED_LIBRARIES) liblog
LOCAL_LDLIBS := -llog

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE:= libjni_openvpn
include $(BUILD_SHARED_LIBRARY)
