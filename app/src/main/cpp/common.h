#ifndef PICOFLEXXTEST_COMMON_H
#define PICOFLEXXTEST_COMMON_H

#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern JavaVM *javaVM;

extern jclass jArrayList;
extern jmethodID jArrayList_init;
extern jmethodID jArrayList_add;
extern jmethodID jArrayList_size;

extern jclass jRoyaleDepthData;
extern jmethodID jRoyaleDepthData_init;
extern jclass jDataListener;
extern jmethodID jDataListener_onData;

extern jclass jFloatArray;

extern jclass jIrListener;
extern jmethodID jIrListener_onIrData;

#define FindJavaClass(dest, name)                                   \
{                                                                   \
    jclass tmpClazz = env->FindClass(name);                         \
    if (env->ExceptionCheck()) {                                    \
        LOGE ("can not find path=[" #name "]");                     \
        return -1;                                                  \
    }                                                               \
    LOGI ("found path=[" #name "]");                                \
    dest = reinterpret_cast<jclass> (env->NewGlobalRef(tmpClazz));  \
    env->DeleteLocalRef(tmpClazz);                                  \
}

#define FindJavaMethod(dest, class, name, signature)                                                        \
{                                                                                                           \
    dest = env->GetMethodID(class, name, signature);                                                        \
    if (env->ExceptionCheck()) {                                                                            \
        LOGE ("can not get method=[" #name "] with signature=[" #signature "] from class=[" #class "]");    \
        return -1;                                                                                          \
    }                                                                                                       \
    LOGI ("got method=[" #name "] with signature=[" #signature "] from class=[" #class "]");                \
}

#define FindJavaField(dest, class, name, signature)                                                        \
{                                                                                                          \
    dest = env->GetFieldID(class, name, signature);                                                        \
    if (env->ExceptionCheck()) {                                                                           \
        LOGE ("can not get field=[" #name "] with signature=[" #signature "] from class=[" #class "]");    \
        return -1;                                                                                         \
    }                                                                                                      \
    LOGI ("got field=[" #name "] with signature=[" #signature "] from class=[" #class "]");                \
}

size_t GetArrayListSize(JNIEnv *env, jobject instance, jfieldID field);

#define ThrowException(exceptionClass, message) \
    (env->ThrowNew(exceptionClass, message))

#endif //PICOFLEXXTEST_COMMON_H
