#include "helper.h"

int jniGetFDFromFileDescriptor(JNIEnv* env, jobject obj) {
  jclass cls = (*env)->FindClass(env, "info/kghost/android/openvpn/FileDescriptorHolder");
  jfieldID field = (*env)->GetFieldID(env, cls, "descriptor", "I");
  return (*env)->GetIntField(env, obj, field);
}

void jniSetFileDescriptorOfFD(C_JNIEnv* env, jobject obj, int value) {
  jclass cls = (*env)->FindClass(env, "info/kghost/android/openvpn/FileDescriptorHolder");
  jfieldID field = (*env)->GetFieldID(env, cls, "descriptor", "I");
  (*env)->SetIntField(env, obj, field, value);
}

jint throwError(JNIEnv *env, char *className, char *message)
{
  jclass exClass = (*env)->FindClass(env, className);
  if (!exClass)
  {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/NoClassDefFoundError"), className);
  }
  return (*env)->ThrowNew(env, exClass, message);
}

