#include <jni.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/un.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <strings.h>
#include <errno.h>
#include <android/log.h>

#include "helper.h"
#include "info_kghost_android_openvpn_ManagementSocket.h"

#ifdef __cplusplus
extern "C" {
#endif

#ifndef SUN_LEN
#define SUN_LEN(su) (sizeof(*(su)) - sizeof((su)->sun_path) + strlen((su)->sun_path))
#endif

socklen_t sockaddr_init(const char* socketFile, struct sockaddr_un* sa) {
  socklen_t salen;

  bzero(sa, sizeof(struct sockaddr_un));
  sa->sun_family = AF_UNIX;
  strcpy(sa->sun_path, socketFile);

  salen = SUN_LEN(sa);
  return salen;
}

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
  msg.msg_flags = 0;

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
  msg.msg_flags = 0;

  return (sendmsg(fd, &msg, 0));
}

/*
 * Class:     info_kghost_android_openvpn_ManagementSocket
 * Method:    open
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_info_kghost_android_openvpn_ManagementSocket_open
  (JNIEnv *env, jclass cls, jstring file)
{
  struct sockaddr_un sa;
  const char *socketFile = (*env)->GetStringUTFChars(env, file, NULL);
  socklen_t salen = sockaddr_init(socketFile, &sa);
  int s = socket(PF_UNIX, SOCK_SEQPACKET, 0);
  if (s < 0) {
    throwError(env, "java/lang/RuntimeException", strerror(errno));
    goto ERROR0;
  }

  if (connect(s, (struct sockaddr *)&sa, salen) < 0) {
    throwError(env, "java/lang/RuntimeException", strerror(errno));
    close(s);
    s = -1;
  }

ERROR0:
  (*env)->ReleaseStringUTFChars(env, file, socketFile);

  /* return the socket file handle */
  return s;
}

/*
 * Class:     info_kghost_android_openvpn_ManagementSocket
 * Method:    read
 * Signature: (ILjava/nio/ByteBuffer;II)I
 */
JNIEXPORT jint JNICALL Java_info_kghost_android_openvpn_ManagementSocket_read__ILjava_nio_ByteBuffer_2II
  (JNIEnv *env, jclass cls, jint socket, jobject buffer, jint offset, jint length)
{
  if (socket < 0) {
    throwError(env, "java/lang/IllegalArgumentException", "socket");
    return -1;
  }

  jlong cap = (*env)->GetDirectBufferCapacity(env, buffer);
  char* ptr = (*env)->GetDirectBufferAddress(env, buffer);
  int recvfd = -1;

  if (cap-offset < length) {
    throwError(env, "java/lang/IllegalArgumentException", "socket");
    return -1;
  }
  int result = read(socket, ptr+offset, length);
  if (result < 0) {
    switch (errno) {
      case EBADF:
      case ENOTSOCK:
      case ENOTCONN:
      case ECONNREFUSED:
        throwError(env, "java/lang/IllegalArgumentException", "socket");
        break;
      case EINVAL:
        throwError(env, "java/lang/IllegalArgumentException", "inval");
        break;
      case EFAULT:
        throwError(env, "java/lang/IllegalArgumentException", "buffer");
        break;
      case EINTR:
        throwError(env, "java/lang/InterruptedException", "interrupted");
        break;
      case ENOMEM:
        throwError(env, "java/lang/RuntimeException", "oom");
        break;
      case EAGAIN:
        throwError(env, "java/lang/IllegalArgumentException", "non-block socket");
        break;
      default:
        throwError(env, "java/lang/RuntimeException", strerror(errno));
        break;
    }
  }

  return result;
}

/*
 * Class:     info_kghost_android_openvpn_ManagementSocket
 * Method:    read
 * Signature: (ILjava/nio/ByteBuffer;IILinfo/kghost/android/openvpn/FileDescriptorHolder;)I
 */
JNIEXPORT jint JNICALL Java_info_kghost_android_openvpn_ManagementSocket_read__ILjava_nio_ByteBuffer_2IILinfo_kghost_android_openvpn_FileDescriptorHolder_2
  (JNIEnv *env, jclass cls, jint socket, jobject buffer, jint offset, jint length, jobject fd)
{
  if (socket < 0) {
    throwError(env, "java/lang/IllegalArgumentException", "socket");
    return -1;
  }

  jlong cap = (*env)->GetDirectBufferCapacity(env, buffer);
  char* ptr = (*env)->GetDirectBufferAddress(env, buffer);
  int recvfd = -1;

  if (cap-offset < length) {
    throwError(env, "java/lang/IllegalArgumentException", "socket");
    return -1;
  }
  int result = read_fd(socket, ptr+offset, length, &recvfd);
  if (result < 0) {
    switch (errno) {
      case EBADF:
      case ENOTSOCK:
      case ENOTCONN:
      case ECONNREFUSED:
        throwError(env, "java/lang/IllegalArgumentException", "socket");
        break;
      case EINVAL:
        throwError(env, "java/lang/IllegalArgumentException", "inval");
        break;
      case EFAULT:
        throwError(env, "java/lang/IllegalArgumentException", "buffer");
        break;
      case EINTR:
        throwError(env, "java/lang/InterruptedException", "interrupted");
        break;
      case ENOMEM:
        throwError(env, "java/lang/RuntimeException", "oom");
        break;
      case EAGAIN:
        throwError(env, "java/lang/IllegalArgumentException", "non-block socket");
        break;
      default:
        throwError(env, "java/lang/RuntimeException", strerror(errno));
        break;
    }
    return result;
  }

  if (recvfd >= 0) {
    jniSetFileDescriptorOfFD(env, fd, recvfd);
  }

  return result;
}

/*
 * Class:     info_kghost_android_openvpn_ManagementSocket
 * Method:    write
 * Signature: (ILjava/nio/ByteBuffer;II)I
 */
JNIEXPORT jint JNICALL Java_info_kghost_android_openvpn_ManagementSocket_write__ILjava_nio_ByteBuffer_2II
  (JNIEnv *env, jclass cls, jint socket, jobject buffer, jint offset, jint length)
{
  if (socket < 0) {
    throwError(env, "java/lang/IllegalArgumentException", "socket");
    return -1;
  }

  jlong cap = (*env)->GetDirectBufferCapacity(env, buffer);
  char* ptr = (*env)->GetDirectBufferAddress(env, buffer);

  if (cap-offset < length) {
    throwError(env, "java/lang/IllegalArgumentException", "socket");
    return -1;
  }
  int result = send(socket, ptr+offset, length, 0);
  if (result < 0) {
    switch (errno) {
      case EACCES:
      case EBADF:
      case ENOTCONN:
      case ENOTSOCK:
      case EISCONN:
      case ECONNRESET:
      case EDESTADDRREQ:
	throwError(env, "java/lang/IllegalArgumentException", "socket");
	break;
      case EINVAL:
	throwError(env, "java/lang/IllegalArgumentException", "inval");
	break;
      case EFAULT:
	throwError(env, "java/lang/IllegalArgumentException", "buffer");
	break;
      case EMSGSIZE:
	throwError(env, "java/lang/IllegalArgumentException", "msg size");
	break;
      case EINTR:
	throwError(env, "java/lang/InterruptedException", "interrupted");
	break;
      case ENOBUFS:
      case ENOMEM:
	throwError(env, "java/lang/RuntimeException", "oom");
	break;
      case EAGAIN:
	throwError(env, "java/lang/IllegalArgumentException", "non-block socket");
	break;
      default:
	throwError(env, "java/lang/RuntimeException", strerror(errno));
	break;
    }
  }

  return result;
}


/*
 * Class:     info_kghost_android_openvpn_ManagementSocket
 * Method:    write
 * Signature: (ILjava/nio/ByteBuffer;IILinfo/kghost/android/openvpn/FileDescriptorHolder;)I
 */
JNIEXPORT jint JNICALL Java_info_kghost_android_openvpn_ManagementSocket_write__ILjava_nio_ByteBuffer_2IILinfo_kghost_android_openvpn_FileDescriptorHolder_2
  (JNIEnv *env, jclass cls, jint socket, jobject buffer, jint offset, jint length, jobject fd)
{
  if (socket < 0) {
    throwError(env, "java/lang/IllegalArgumentException", "socket");
    return -1;
  }

  jlong cap = (*env)->GetDirectBufferCapacity(env, buffer);
  char* ptr = (*env)->GetDirectBufferAddress(env, buffer);
  int sendfd = jniGetFDFromFileDescriptor(env, fd);

  if (cap-offset < length) {
    throwError(env, "java/lang/IllegalArgumentException", "socket");
    return -1;
  }
  int result = write_fd(socket, ptr+offset, length, sendfd);
  if (result < 0) {
    switch (errno) {
      case EACCES:
      case EBADF:
      case ENOTCONN:
      case ENOTSOCK:
      case EISCONN:
      case ECONNRESET:
      case EDESTADDRREQ:
	throwError(env, "java/lang/IllegalArgumentException", "socket");
	break;
      case EINVAL:
	throwError(env, "java/lang/IllegalArgumentException", "inval");
	break;
      case EFAULT:
	throwError(env, "java/lang/IllegalArgumentException", "buffer");
	break;
      case EMSGSIZE:
	throwError(env, "java/lang/IllegalArgumentException", "msg size");
	break;
      case EINTR:
	throwError(env, "java/lang/InterruptedException", "interrupted");
	break;
      case ENOBUFS:
      case ENOMEM:
	throwError(env, "java/lang/RuntimeException", "oom");
	break;
      case EAGAIN:
	throwError(env, "java/lang/IllegalArgumentException", "non-block socket");
	break;
      default:
	throwError(env, "java/lang/RuntimeException", strerror(errno));
	break;
    }
  }

  return result;
}


/*
 * Class:     info_kghost_android_openvpn_ManagementSocket
 * Method:    close
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_info_kghost_android_openvpn_ManagementSocket_close
  (JNIEnv *env, jclass cls, jint socket)
{
  return close(socket);
}


/*
 * Class:     info_kghost_android_openvpn_ManagementSocket
 * Method:    shutdown
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL Java_info_kghost_android_openvpn_ManagementSocket_shutdown
  (JNIEnv *env, jclass cls, jint socket, jint how)
{
  return shutdown(socket, how);
}
