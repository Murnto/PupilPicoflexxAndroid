#include <jni.h>
#include "common.h"

size_t GetArrayListSize(JNIEnv *env, jobject instance, jfieldID field) {
    auto arraylist = env->GetObjectField(instance, field);
    return (size_t) env->CallIntMethod(arraylist, jArrayList_size);
}
