//#include <jni.h>
//#include <string>
//
//extern "C" JNIEXPORT jstring JNICALL
//Java_com_example_testjni_MainActivity_stringFromJNI(
//        JNIEnv* env,
//        jobject /* this */) {
//    std::string hello = "Hello from C++";
//    return env->NewStringUTF(hello.c_str());
//}


#include <jni.h>

#include <royale/CameraManager.hpp>
#include <royale/ICameraDevice.hpp>
#include <android/log.h>
#include <iostream>
#include <thread>
#include <chrono>
#include <memory>
#include <unistd.h>
#include <netdb.h>
#include "common.h"
#include "RoyaleCameraDevice.h"

#undef TAG
#define TAG "com.example.picoflexxtest.royale.jni"

JavaVM *javaVM = nullptr;

jclass jArrayList;
jmethodID jArrayList_init;
jmethodID jArrayList_add;
jmethodID jArrayList_size;

jclass jRoyaleDepthData;
jmethodID jRoyaleDepthData_init;

jclass jFloatArray;

namespace {
    uint16_t width = 0;
    uint16_t height = 0;

    // this represents the main camera device object
    std::unique_ptr<royale::ICameraDevice> cameraDevice;

#ifdef __cplusplus
    extern "C" {
#endif

    JNIEXPORT jint JNICALL
    JNI_OnLoad(JavaVM *vm, void *) {
        // Cache the java environment for later use.
        javaVM = vm;

        JNIEnv *env;
        if (javaVM->GetEnv(reinterpret_cast<void **> (&env), JNI_VERSION_1_6) != JNI_OK) {
            LOGE ("can not cache the java native interface environment");
            return -1;
        }

        RoyaleCameraDevice::InitJni(env);

        FindJavaClass(jArrayList, "java/util/ArrayList");
        FindJavaMethod(jArrayList_init, jArrayList, "<init>", "(I)V");
        FindJavaMethod(jArrayList_add, jArrayList, "add", "(Ljava/lang/Object;)Z");
        FindJavaMethod(jArrayList_size, jArrayList, "size", "()I");

        FindJavaClass(jRoyaleDepthData, "com/example/picoflexxtest/royale/RoyaleDepthData");
        FindJavaMethod(jRoyaleDepthData_init, jRoyaleDepthData, "<init>", "(IJIII[I[[F[F[I[I)V");

        FindJavaClass(jFloatArray, "[F");

        return JNI_VERSION_1_6;
    }

    JNIEXPORT void JNICALL
    JNI_OnUnload(JavaVM *vm, void *) {
        // Obtain the JNIEnv from the VM

        JNIEnv *env;
        vm->GetEnv(reinterpret_cast<void **> (&env), JNI_VERSION_1_6);

        env->DeleteGlobalRef(jArrayList);
        env->DeleteGlobalRef(jRoyaleDepthData);
        env->DeleteGlobalRef(jFloatArray);
    }

#ifdef __cplusplus
    }
#endif
}
