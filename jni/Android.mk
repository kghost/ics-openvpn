LOCAL_PATH:= $(call my-dir)

#on a 32bit maschine run ./configure --enable-password-save --disable-pkcs11 --with-ifconfig-path=/system/bin/ifconfig --with-route-path=/system/bin/route
#from generated Makefile copy variable contents of openvpn_SOURCES to common_SRC_FILES
# append missing.c to the end of the list
# missing.c defines undefined functions.
# in tun.c replace /dev/net/tun with /dev/tun

common_SRC_FILES:= \
	helper.c \
	info_kghost_android_openvpn_FileDescriptorHolder.c \
	info_kghost_android_openvpn_OpenVpn.c \
	info_kghost_android_openvpn_OpenVpn_ControlChannel.c

#common_C_INCLUDES += \
#	frameworks/base/cmds/keystore

# static linked binary
# =====================================================

#include $(CLEAR_VARS)
#LOCAL_SRC_FILES:= $(common_SRC_FILES)
#LOCAL_CFLAGS:= $(common_CFLAGS)
#LOCAL_C_INCLUDES:= $(common_C_INCLUDES)
#
#LOCAL_SHARED_LIBRARIES += $(common_SHARED_LIBRARIES)
#LOCAL_STATIC_LIBRARIES:= libopenssl-static liblzo-static
#
##LOCAL_LDLIBS += -ldl
##LOCAL_PRELINK_MODULE:= false
#
#LOCAL_MODULE:= openvpn-static
#LOCAL_MODULE_PATH := $(TARGET_OUT_OPTIONAL_EXECUTABLES)
#include $(BUILD_EXECUTABLE)

# dynamic linked binary
# =====================================================

include $(CLEAR_VARS)
LOCAL_SRC_FILES:= $(common_SRC_FILES)
LOCAL_CFLAGS:= $(common_CFLAGS)
LOCAL_C_INCLUDES:= $(common_C_INCLUDES)

LOCAL_SHARED_LIBRARIES:= $(common_SHARED_LIBRARIES) liblog

#LOCAL_LDLIBS += -ldl
#LOCAL_PRELINK_MODULE:= false

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE:= libjni_openvpn
#LOCAL_MODULE_PATH := $(TARGET_OUT_OPTIONAL_EXECUTABLES)
include $(BUILD_SHARED_LIBRARY)
