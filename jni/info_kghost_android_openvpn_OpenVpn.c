#include "info_kghost_android_openvpn_OpenVpn.h"
#include "helper.h"

#include <sys/socket.h>
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <sys/resource.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <errno.h>

#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     info_kghost_android_openvpn_OpenVpn
 * Method:    start
 * Signature: (Ljava/lang/String;[Ljava/lang/String;Linfo/kghost/android/openvpn/FileDescriptorHolder;)I
 */
JNIEXPORT jint JNICALL Java_info_kghost_android_openvpn_OpenVpn_start
  (JNIEnv *env, jclass cls, jstring exec, jobjectArray options, jobject control) {
    // create control socket
    int socket[2];
    int result = socketpair(PF_UNIX, SOCK_DGRAM, 0, socket);
    if (result < 0) {
      throwError(env, "java/lang/RuntimeException", strerror(errno));
      goto ERROR0;
    }

    // Copy commands into char*[].
    if (options == NULL) {
      result = -1;
      goto ERROR1;
    }

    const char *path = (*env)->GetStringUTFChars(env, exec, NULL);
    if (path == NULL) {
      throwError(env, "java/lang/IllegalArgumentException", "exec is null");
      goto ERROR1;
    }

    jsize length = (*env)->GetArrayLength(env, options);
    const char** array = malloc(sizeof(char*)*(length + 4));
    array[0] = "openvpn";
    array[1] = "--dev";
    char control_fd[10];
    snprintf(control_fd, sizeof(control_fd), "%d", socket[1]);
    array[2] = control_fd;
    array[length+3] = 0;
    int i;
    for (i = 0; i < length; ++i) {
      array[i+3] = (*env)->GetStringUTFChars(env, (*env)->GetObjectArrayElement(env, options, i), NULL);
    }

    // Keep track of the system properties fd so we don't close it.
    int androidSystemPropertiesFd = -1;
    char* fdString = getenv("ANDROID_PROPERTY_WORKSPACE");
    if (fdString) {
      androidSystemPropertiesFd = atoi(fdString);
    }

    struct rlimit rlimit;
    getrlimit(RLIMIT_NOFILE, &rlimit);
    const int max_fd = rlimit.rlim_max;

    result = fork();
    if (result < 0) {
      throwError(env, "java/lang/RuntimeException", strerror(errno));
      goto ERROR2;
    }
    if (result == 0) {
      // child
      // DON'T DO ANY MEMORY ALLOC HERE
      // because some other java process may hold the malloc lock
      // keep these as simple as possible
      int fd;
      for (fd = 3; fd < max_fd; ++fd) {
        if (fd != socket[1] && fd != androidSystemPropertiesFd) { 
          close(fd);
        }
      }

      execvp(path, (char**)array);
      exit(errno); // unreachable if execvp success
    }

    // parent
    close(socket[1]);
    jniSetFileDescriptorOfFD(env, control, socket[0]);
    for (i = 0; i < length; ++i) {
      (*env)->ReleaseStringUTFChars(env, (*env)->GetObjectArrayElement(env, options, i), array[i+3]);
    }
    free(array);
    (*env)->ReleaseStringUTFChars(env, exec, path);
    return result;

ERROR2:
    for (i = 0; i < length; ++i) {
      (*env)->ReleaseStringUTFChars(env, (*env)->GetObjectArrayElement(env, options, i), array[i+3]);
    }
    free(array);
    (*env)->ReleaseStringUTFChars(env, exec, path);
ERROR1:
    close(socket[0]);
    close(socket[1]);
ERROR0:
    return result;
  }

/*
 * Class:     info_kghost_android_openvpn_OpenVpn
 * Method:    stop
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_info_kghost_android_openvpn_OpenVpn_stop
  (JNIEnv *env, jclass cls, jint pid) {
    kill(pid, SIGTERM);
    int status = 0;
    waitpid(pid, &status, 0);
  }

#ifdef __cplusplus
}
#endif
