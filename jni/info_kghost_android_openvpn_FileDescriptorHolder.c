#include "info_kghost_android_openvpn_FileDescriptorHolder.h"

#include <sys/socket.h>

#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     info_kghost_android_openvpn_FileDescriptorHolder
 * Method:    close
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_info_kghost_android_openvpn_FileDescriptorHolder_close
  (JNIEnv *env, jclass cls, jint fd) {
    close(fd);
  }

#ifdef __cplusplus
}
#endif
