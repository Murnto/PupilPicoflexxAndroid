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
#include "listeners.h"

#undef TAG
#define TAG "com.example.picoflexxtest.royale.jni"

JavaVM *javaVM = nullptr;

jclass jArrayList;
jmethodID jArrayList_init;
jmethodID jArrayList_add;
jmethodID jArrayList_size;

jclass jRoyaleDepthData;
jmethodID jRoyaleDepthData_init;
jclass jDataListener;
jmethodID jDataListener_onData;

jclass jFloatArray;

jclass jIrListener;
jmethodID jIrListener_onIrData;


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

        FindJavaClass(jArrayList, "java/util/ArrayList");
        FindJavaMethod(jArrayList_init, jArrayList, "<init>", "(I)V");
        FindJavaMethod(jArrayList_add, jArrayList, "add", "(Ljava/lang/Object;)Z");
        FindJavaMethod(jArrayList_size, jArrayList, "size", "()I");

        FindJavaClass(jRoyaleDepthData, "com/example/picoflexxtest/royale/RoyaleDepthData");
        FindJavaMethod(jRoyaleDepthData_init, jRoyaleDepthData, "<init>", "(IJIII[I[[F[F[I[I[B)V");
        FindJavaClass(jDataListener, "com/example/picoflexxtest/royale/DataListener");
        FindJavaMethod(jDataListener_onData, jDataListener, "onData", "(Lcom/example/picoflexxtest/royale/RoyaleDepthData;)V");

        FindJavaClass(jFloatArray, "[F");

        FindJavaClass(jIrListener, "com/example/picoflexxtest/royale/IrListener");
        FindJavaMethod(jIrListener_onIrData, jIrListener, "onIrData", "([I)V");

        return JNI_VERSION_1_6;
    }

    JNIEXPORT void JNICALL
    JNI_OnUnload(JavaVM *vm, void *) {
        // Obtain the JNIEnv from the VM

        JNIEnv *env;
        vm->GetEnv(reinterpret_cast<void **> (&env), JNI_VERSION_1_6);

        env->DeleteGlobalRef(jDataListener);
        env->DeleteGlobalRef(jIrListener);
    }

    JNIEXPORT jintArray JNICALL
    Java_com_example_picoflexxtest_royale_RoyaleCamera_openCameraNative(JNIEnv *env, jobject instance, jint fd,
                                                                        jint vid,
                                                                        jint pid) {
        // the camera manager will query for a connected camera
        {
            auto cFD = static_cast<uint32_t> (fd);
            auto cVID = static_cast<uint32_t> (vid);
            auto cPID = static_cast<uint32_t> (pid);

            royale::CameraManager manager;

            auto cameraList = manager.getConnectedCameraList(cFD, cVID, cPID);
            LOGI ("Detected %zu camera(s).", cameraList.size());

            if (!cameraList.empty()) {
                cameraDevice = manager.createCamera(cameraList.at(0));
            }
        }
        // the camera device is now available and CameraManager can be deallocated here

        if (cameraDevice == nullptr) {
            LOGI ("Cannot create the camera device");
            return jintArray();
        }

        // IMPORTANT: call the initialize method before working with the camera device
        auto ret = cameraDevice->initialize();
        if (ret != royale::CameraStatus::SUCCESS) {
            LOGE ("Cannot initialize the camera device, CODE %d", static_cast<uint32_t> (ret));
            return jintArray();
        }

        royale::Vector<royale::String> opModes;
        royale::String cameraName;
        royale::String cameraId;

        ret = cameraDevice->getUseCases(opModes);
        if (ret != royale::CameraStatus::SUCCESS) {
            LOGE ("Failed to get use cases, CODE %d", (int) ret);
            return jintArray();
        }

        ret = cameraDevice->getMaxSensorWidth(width);
        if (ret != royale::CameraStatus::SUCCESS) {
            LOGE ("Failed to get max sensor width, CODE %d", (int) ret);
            return jintArray();
        }

        ret = cameraDevice->getMaxSensorHeight(height);
        if (ret != royale::CameraStatus::SUCCESS) {
            LOGE ("Failed to get max sensor height, CODE %d", (int) ret);
            return jintArray();
        }

        ret = cameraDevice->getId(cameraId);
        if (ret != royale::CameraStatus::SUCCESS) {
            LOGE ("Failed to get camera ID, CODE %d", (int) ret);
            return jintArray();
        }

        ret = cameraDevice->getCameraName(cameraName);
        if (ret != royale::CameraStatus::SUCCESS) {
            LOGE ("Failed to get camera name, CODE %d", (int) ret);
            return jintArray();
        }

        // display some information about the connected camera
        LOGI ("====================================");
        LOGI ("        Camera information");
        LOGI ("====================================");
        LOGI ("Id:              %s", cameraId.c_str());
        LOGI ("Type:            %s", cameraName.c_str());
        LOGI ("Width:           %d", width);
        LOGI ("Height:          %d", height);
        LOGI ("Operation modes: %zu", opModes.size());

        for (int i = 0; i < opModes.size(); i++) {
            LOGI ("    %s", opModes.at(i).c_str());
        }

//        // register a data listener
//        ret = cameraDevice->registerDataListener(&listener);
//        if (ret != royale::CameraStatus::SUCCESS) {
//            LOGI ("Failed to register data listener, CODE %d", (int) ret);
//        }

        // set an operation mode
        ret = cameraDevice->setUseCase(opModes[0]);
        if (ret != royale::CameraStatus::SUCCESS) {
            LOGI ("Failed to set use case, CODE %d", (int) ret);
        }

        ret = cameraDevice->startCapture();
        if (ret != royale::CameraStatus::SUCCESS) {
            LOGI ("Failed to start capture, CODE %d", (int) ret);
        }

        jint fill[2];
        fill[0] = width;
        fill[1] = height;

        jintArray intArray = env->NewIntArray(2);

        env->SetIntArrayRegion(intArray, 0, 2, fill);

        return intArray;
    }

    JNIEXPORT jlong JNICALL
    Java_com_example_picoflexxtest_royale_RoyaleCamera__1_1registerDataListener(
            JNIEnv *env,
            jobject instance,
            jobject dataListener
    ) {
        auto *ptr = new RoyaleDataListener(env->NewGlobalRef(dataListener));

        cameraDevice->registerDataListener(ptr);

        return reinterpret_cast<jlong>(ptr);
    }

    JNIEXPORT void JNICALL
    Java_com_example_picoflexxtest_royale_RoyaleCamera__1_1unregisterDataListener(
            JNIEnv *env,
            jobject instance,
            jlong pDataListener
    ) {
        auto *ptr = reinterpret_cast<RoyaleDataListener *>(pDataListener);
        cameraDevice->unregisterDataListener();

        env->DeleteGlobalRef(ptr->callback);

        delete ptr;
    }

    JNIEXPORT jlong JNICALL
    Java_com_example_picoflexxtest_royale_RoyaleCamera__1_1registerIrListener(
            JNIEnv *env,
            jobject instance,
            jobject irListener
    ) {
        auto *ptr = new RoyaleIRListener(env->NewGlobalRef(irListener));

        auto status = cameraDevice->registerIRImageListener(ptr);
        LOGI ("status=%d", (int) status);

        return reinterpret_cast<jlong>(ptr);
    }

    JNIEXPORT void JNICALL
    Java_com_example_picoflexxtest_royale_RoyaleCamera__1_1unregisterIrListener(
            JNIEnv *env,
            jobject instance,
            jlong pIrListener
    ) {
        auto *ptr = reinterpret_cast<RoyaleIRListener *>(pIrListener);
        cameraDevice->unregisterIRImageListener();

        env->DeleteGlobalRef(ptr->callback);

        delete ptr;
    }

    JNIEXPORT void JNICALL
    Java_com_example_picoflexxtest_royale_RoyaleCamera_closeCameraNative(JNIEnv *env, jobject instance) {
        cameraDevice->stopCapture();
    }
#ifdef __cplusplus
    }
#endif
}
