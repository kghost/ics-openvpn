#include <jni.h>

#ifndef _Included_helper__H__
#define _Included_helper__H__
#ifdef __cplusplus
extern "C" {
#endif

int jniGetFDFromFileDescriptor(JNIEnv* env, jobject fileDescriptor);
void jniSetFileDescriptorOfFD(C_JNIEnv* env, jobject fileDescriptor, int value);

#ifdef __cplusplus
}
#endif
#endif
