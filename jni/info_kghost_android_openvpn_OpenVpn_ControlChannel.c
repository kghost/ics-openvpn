#include "info_kghost_android_openvpn_OpenVpn_ControlChannel.h"
#include "helper.h"

#include <sys/types.h>
#include <sys/socket.h>
#include <android/log.h>

#ifdef __cplusplus
extern "C" {
#endif

static ssize_t read_fd(int fd, void *ptr, size_t nbytes, int *recvfd)
{
  struct msghdr msg;
  struct iovec iov[1];
  ssize_t n;

  union {
    struct cmsghdr cm;
    char     control[CMSG_SPACE(sizeof (int))];
  } control_un;
  struct cmsghdr  *cmptr;

  msg.msg_control  = control_un.control;
  msg.msg_controllen = sizeof(control_un.control);

  msg.msg_name = NULL;
  msg.msg_namelen = 0;

  iov[0].iov_base = ptr;
  iov[0].iov_len = nbytes;
  msg.msg_iov = iov;
  msg.msg_iovlen = 1;

  if ( (n = recvmsg(fd, &msg, 0)) <= 0)
    return (n);

  if ( (cmptr = CMSG_FIRSTHDR(&msg)) != NULL &&
      cmptr->cmsg_len == CMSG_LEN(sizeof(int))) {
    if (cmptr->cmsg_level != SOL_SOCKET)
      __android_log_write(ANDROID_LOG_ERROR, "OpenVpn_JNI", "control level != SOL_SOCKET");
    if (cmptr->cmsg_type != SCM_RIGHTS)
      __android_log_write(ANDROID_LOG_ERROR, "OpenVpn_JNI", "control type != SCM_RIGHTS");
    *recvfd = *((int *) CMSG_DATA(cmptr));
  } else
    *recvfd = -1;           /* descriptor was not passed */

  return (n);
}

static ssize_t write_fd(int fd, void *ptr, size_t nbytes, int sendfd)
{
  struct msghdr msg;
  struct iovec iov[1];

  union {
    struct cmsghdr cm;
    char    control[CMSG_SPACE(sizeof(int))];
  } control_un;
  struct cmsghdr *cmptr;

  msg.msg_control = control_un.control;
  msg.msg_controllen = sizeof(control_un.control);

  cmptr = CMSG_FIRSTHDR(&msg);
  cmptr->cmsg_len = CMSG_LEN(sizeof(int));
  cmptr->cmsg_level = SOL_SOCKET;
  cmptr->cmsg_type = SCM_RIGHTS;
  *((int *) CMSG_DATA(cmptr)) = sendfd;

  msg.msg_name = NULL;
  msg.msg_namelen = 0;

  iov[0].iov_base = ptr;
  iov[0].iov_len = nbytes;
  msg.msg_iov = iov;
  msg.msg_iovlen = 1;

  return (sendmsg(fd, &msg, 0));
}

static inline int min(int a, int b) {
	if (a > b)
		return b;
	else
		return a;
}

/*
 * Class:     info_kghost_android_openvpn_OpenVpn_ControlChannel
 * Method:    recv
 * Signature: (Linfo/kghost/android/openvpn/FileDescriptorHolder;Ljava/nio/Buffer;IILinfo/kghost/android/openvpn/FileDescriptorHolder;)I
 */
JNIEXPORT jint JNICALL Java_info_kghost_android_openvpn_OpenVpn_00024ControlChannel_recv
(JNIEnv *env, jclass cls, jobject socket, jobject buffer, jint offset, jint length, jobject fd) {
  int s = jniGetFDFromFileDescriptor(env, socket);
  if (s < 0) {
#warning TODO: raise exception
    return -1;
  }

  jlong cap = (*env)->GetDirectBufferCapacity(env, buffer);
  void* ptr = (*env)->GetDirectBufferAddress(env, buffer);
  int recvfd = -1;

  int result = read_fd(s, ptr+offset, min(cap-offset, length), &recvfd);

  if (recvfd >= 0) {
    jniSetFileDescriptorOfFD(env, fd, recvfd);
  }

  return result;
}

/*
 * Class:     info_kghost_android_openvpn_OpenVpn_ControlChannel
 * Method:    send
 * Signature: (Linfo/kghost/android/openvpn/FileDescriptorHolder;Ljava/nio/Buffer;II)I
 */
JNIEXPORT jint JNICALL Java_info_kghost_android_openvpn_OpenVpn_00024ControlChannel_send__Linfo_kghost_android_openvpn_FileDescriptorHolder_2Ljava_nio_Buffer_2II
(JNIEnv *env, jclass cls, jobject socket, jobject buffer, jint offset, jint length) {
  int s = jniGetFDFromFileDescriptor(env, socket);
  if (s < 0) {
#warning TODO: raise exception
    return -1;
  }

  jlong cap = (*env)->GetDirectBufferCapacity(env, buffer);
  void* ptr = (*env)->GetDirectBufferAddress(env, buffer);

  return send(s, ptr+offset, min(cap-offset, length), 0);
}

/*
 * Class:     info_kghost_android_openvpn_OpenVpn_ControlChannel
 * Method:    send
 * Signature: (Ljava/io/FileDescriptor;Ljava/nio/Buffer;Ljava/io/FileDescriptor;)I
 */
JNIEXPORT jint JNICALL Java_info_kghost_android_openvpn_OpenVpn_00024ControlChannel_send__Linfo_kghost_android_openvpn_FileDescriptorHolder_2Ljava_nio_Buffer_2IILinfo_kghost_android_openvpn_FileDescriptorHolder_2
(JNIEnv *env, jclass cls, jobject socket, jobject buffer, jint offset, jint length, jobject fd) {
  int s = jniGetFDFromFileDescriptor(env, socket);
  if (s < 0) {
#warning TODO: raise exception
    return -1;
  }

  jlong cap = (*env)->GetDirectBufferCapacity(env, buffer);
  void* ptr = (*env)->GetDirectBufferAddress(env, buffer);
  int sendfd = jniGetFDFromFileDescriptor(env, fd);

  return write_fd(s, ptr+offset, min(cap-offset, length), sendfd);
}

#ifdef __cplusplus
}
#endif
