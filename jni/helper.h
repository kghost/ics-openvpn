#include <jni.h>

#ifndef _Included_helper__H__
#define _Included_helper__H__
#ifdef __cplusplus
extern "C" {
#endif

int jniGetFDFromFileDescriptor(JNIEnv* env, jobject fileDescriptor);
void jniSetFileDescriptorOfFD(C_JNIEnv* env, jobject fileDescriptor, int value);
jint throwError(JNIEnv *env, char *className, char *message);

#ifdef __cplusplus
}
#endif
#endif
