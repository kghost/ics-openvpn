LOCAL_PATH:= $(call my-dir)

#on a 32bit maschine run ./configure --enable-password-save --disable-pkcs11 --with-ifconfig-path=/system/bin/ifconfig --with-route-path=/system/bin/route
#from generated Makefile copy variable contents of openvpn_SOURCES to common_SRC_FILES
# append missing.c to the end of the list
# missing.c defines undefined functions.
# in tun.c replace /dev/net/tun with /dev/tun

common_SRC_FILES:= \
        base64.c base64.h \
	basic.h \
	buffer.c buffer.h \
	circ_list.h \
	common.h \
	crypto.c crypto.h \
	dhcp.c dhcp.h \
	errlevel.h \
	error.c error.h \
	event.c event.h \
	fdmisc.c fdmisc.h \
        forward.c forward.h forward-inline.h \
	fragment.c fragment.h \
	gremlin.c gremlin.h \
	helper.c helper.h \
	httpdigest.c httpdigest.h \
	lladdr.c lladdr.h \
	init.c init.h \
	integer.h \
        interval.c interval.h \
	list.c list.h \
	lzo.c lzo.h \
	manage.c manage.h \
	mbuf.c mbuf.h \
        memdbg.h \
	misc.c misc.h \
	mroute.c mroute.h \
	mss.c mss.h \
	mtcp.c mtcp.h \
	mtu.c mtu.h \
	mudp.c mudp.h \
	multi.c multi.h \
        ntlm.c ntlm.h \
	occ.c occ.h occ-inline.h \
	pkcs11.c pkcs11.h \
	openvpn.c openvpn.h \
	openvpn-plugin.h \
	options.c options.h \
	otime.c otime.h \
	packet_id.c packet_id.h \
	perf.c perf.h \
	pf.c pf.h pf-inline.h \
	ping.c ping.h ping-inline.h \
	plugin.c plugin.h \
	pool.c pool.h \
	proto.c proto.h \
	proxy.c proxy.h \
	ieproxy.h ieproxy.c \
        ps.c ps.h \
	push.c push.h \
	pushlist.h \
	reliable.c reliable.h \
	route.c route.h \
	schedule.c schedule.h \
	session_id.c session_id.h \
	shaper.c shaper.h \
	sig.c sig.h \
	socket.c socket.h \
	socks.c socks.h \
	ssl.c ssl.h \
	status.c status.h \
	syshead.h \
	thread.c thread.h \
	tun.c tun.h \
	win32.h win32.c \
	cryptoapi.h cryptoapi.c \
	missing.c

#common_CFLAGS += -DNO_WINDOWS_BRAINDEATH 
common_CFLAGS += -DANDROID_CHANGES

common_C_INCLUDES += \
	external/openssl \
	external/openssl/include \
	external/openssl/crypto \
	external/lzo/include \
	frameworks/base/cmds/keystore

common_SHARED_LIBRARIES := libcutils 

ifneq ($(TARGET_SIMULATOR),true)
	common_SHARED_LIBRARIES += libdl
endif

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

LOCAL_SHARED_LIBRARIES:= $(common_SHARED_LIBRARIES) libssl libcrypto
LOCAL_STATIC_LIBRARIES:= $(common_STATIC_LIBRARIES) liblzo-static

#LOCAL_LDLIBS += -ldl
#LOCAL_PRELINK_MODULE:= false

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE:= openvpn
#LOCAL_MODULE_PATH := $(TARGET_OUT_OPTIONAL_EXECUTABLES)
include $(BUILD_EXECUTABLE)
