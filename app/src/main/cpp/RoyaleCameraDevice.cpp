#include "RoyaleCameraDevice.h"
#include <royale.hpp>
#include <royale/CameraManager.hpp>
#include <royale/ICameraDevice.hpp>
#include <android/log.h>
#include "common.h"

#undef TAG
#define TAG "com.example.picoflexxtest.RoyaleCameraDevice.cpp"

#define SELF ((RoyaleCameraDevice*) env->GetLongField(instance, jRoyaleCameraDevice_ptr))

jclass jRoyaleCameraException;
jmethodID jRoyaleCameraException_init;

jclass jRoyaleCameraDevice;
jfieldID jRoyaleCameraDevice_ptr;
jfieldID jRoyaleCameraDevice_fullDepthDataCallbacks;
jmethodID jRoyaleCameraDevice_onFullDepthData;
jfieldID jRoyaleCameraDevice_encodedDepthDataCallbacks;
jmethodID jRoyaleCameraDevice_onEncodedDepthData;
jfieldID jRoyaleCameraDevice_exposureTimeCallbacks;
jmethodID jRoyaleCameraDevice_onExposureTime;

#define ThrowRoyaleException(message, code) \
{                                                                                                                                       \
    auto royaleErrorMessage = royale::getErrorString((royale::CameraStatus) (code));                                                      \
    jstring jstr_royaleErrorMessage = env->NewStringUTF(royaleErrorMessage.c_str());                                                    \
    jstring jstr_message = env->NewStringUTF(message);                                                                                  \
    auto throwable = env->NewObject(jRoyaleCameraException, jRoyaleCameraException_init, jstr_message, code, jstr_royaleErrorMessage);  \
    env->Throw(static_cast<jthrowable>(throwable));                                                                                     \
};

int RoyaleCameraDevice::InitJni(JNIEnv *env) {
    FindJavaClass(jRoyaleCameraException, "com/example/picoflexxtest/royale/RoyaleCameraException");
    FindJavaMethod(jRoyaleCameraException_init, jRoyaleCameraException, "<init>",
                   "(Ljava/lang/String;ILjava/lang/String;)V");

    FindJavaClass(jRoyaleCameraDevice, "com/example/picoflexxtest/royale/RoyaleCameraDevice");
    FindJavaField(jRoyaleCameraDevice_ptr, jRoyaleCameraDevice, "__ptr", "J");
    FindJavaField(jRoyaleCameraDevice_fullDepthDataCallbacks, jRoyaleCameraDevice, "fullDepthDataCallbacks",
                  "Ljava/util/ArrayList;");
    FindJavaMethod(jRoyaleCameraDevice_onFullDepthData, jRoyaleCameraDevice, "onFullDepthData",
                   "(Lcom/example/picoflexxtest/royale/RoyaleDepthData;)V")
    FindJavaField(jRoyaleCameraDevice_encodedDepthDataCallbacks, jRoyaleCameraDevice, "encodedDepthDataCallbacks",
                  "Ljava/util/ArrayList;");
    FindJavaMethod(jRoyaleCameraDevice_onEncodedDepthData, jRoyaleCameraDevice, "onEncodedDepthData", "([B)V")
    FindJavaField(jRoyaleCameraDevice_exposureTimeCallbacks, jRoyaleCameraDevice, "exposureTimeCallbacks",
                  "Ljava/util/ArrayList;");
    FindJavaMethod(jRoyaleCameraDevice_onExposureTime, jRoyaleCameraDevice, "onExposureTime", "([I)V")

    LOGI("RoyaleCameraDevice::JNI_OnEnv finished.");

    return 0;
}

void RoyaleCameraDevice::onNewData(const royale::IRImage *data) {
    /* TODO: Ignored for now */
}

#define GET_BYTE(x, y) (*((uint8_t *) (((char*) &(x)) + (y))))
#define DO_FILL_ENCODED(fillEncoded, elementSize, i, pt) \
{                                                                                         \
    (fillEncoded)[(i) * (elementSize) + 0] = (jbyte) (((pt).grayValue >> 4) & 0xFF);      \
                                                                                          \
    (fillEncoded)[(i) * (elementSize) + 1 + 0] = (jbyte) GET_BYTE((pt).x, 0);             \
    (fillEncoded)[(i) * (elementSize) + 1 + 1] = (jbyte) GET_BYTE((pt).x, 1);             \
    (fillEncoded)[(i) * (elementSize) + 1 + 2] = (jbyte) GET_BYTE((pt).x, 2);             \
    (fillEncoded)[(i) * (elementSize) + 1 + 3] = (jbyte) GET_BYTE((pt).x, 3);             \
                                                                                          \
    (fillEncoded)[(i) * (elementSize) + 5 + 0] = (jbyte) GET_BYTE((pt).y, 0);             \
    (fillEncoded)[(i) * (elementSize) + 5 + 1] = (jbyte) GET_BYTE((pt).y, 1);             \
    (fillEncoded)[(i) * (elementSize) + 5 + 2] = (jbyte) GET_BYTE((pt).y, 2);             \
    (fillEncoded)[(i) * (elementSize) + 5 + 3] = (jbyte) GET_BYTE((pt).y, 3);             \
                                                                                          \
    (fillEncoded)[(i) * (elementSize) + 9 + 0] = (jbyte) GET_BYTE((pt).z, 0);             \
    (fillEncoded)[(i) * (elementSize) + 9 + 1] = (jbyte) GET_BYTE((pt).z, 1);             \
    (fillEncoded)[(i) * (elementSize) + 9 + 2] = (jbyte) GET_BYTE((pt).z, 2);             \
    (fillEncoded)[(i) * (elementSize) + 9 + 3] = (jbyte) GET_BYTE((pt).z, 3);             \
                                                                                          \
    (fillEncoded)[(i) * (elementSize) + 13 + 0] = (jbyte) GET_BYTE((pt).noise, 0);        \
    (fillEncoded)[(i) * (elementSize) + 13 + 1] = (jbyte) GET_BYTE((pt).noise, 1);        \
    (fillEncoded)[(i) * (elementSize) + 13 + 2] = (jbyte) GET_BYTE((pt).noise, 2);        \
    (fillEncoded)[(i) * (elementSize) + 13 + 3] = (jbyte) GET_BYTE((pt).noise, 3);        \
                                                                                          \
    (fillEncoded)[(i) * (elementSize) + 17] = (jbyte) pt.depthConfidence;                 \
}
#define DO_POINTCLOUD(pointCloud, i, pt)                                        \
{                                                                               \
    jfloat fillFloatArray[] = {(pt).x, (pt).y, (pt).z};                         \
    jfloatArray floatArray = env->NewFloatArray(3);                             \
    env->SetFloatArrayRegion(floatArray, (jsize) 0, (jsize) 3, fillFloatArray); \
    env->SetObjectArrayElement(pointCloud, (jsize) (i), floatArray);            \
    env->DeleteLocalRef(floatArray);                                            \
}
#define DO_CONFIDENCE(fillConfidence, i, pt) \
    fillConfidence[i] = (pt).depthConfidence;
#define DO_GRAY_VALUE(fillGrayValue, i, pt) \
    fillGrayValue[i] = (pt).grayValue;
#define DO_NOISE(fillNoise, i, pt) \
    fillNoise[i] = (pt).noise;

void RoyaleCameraDevice::onNewData(const royale::DepthData *data) {
    JNIEnv *env;
    javaVM->AttachCurrentThread(&env, NULL);

    size_t i;
    size_t n = data->width * data->height;

    jint fillExposureTimes[data->exposureTimes.size()];
    for (i = 0; i < data->exposureTimes.size(); i++) {
        fillExposureTimes[i] = data->exposureTimes[i];
    }
    jintArray exposureTimes = env->NewIntArray(data->exposureTimes.size());
    env->SetIntArrayRegion(exposureTimes, 0, data->exposureTimes.size(), fillExposureTimes);

    bool wantEncoded = GetArrayListSize(env, this->instance, jRoyaleCameraDevice_encodedDepthDataCallbacks) != 0;
    bool wantFull = GetArrayListSize(env, this->instance, jRoyaleCameraDevice_fullDepthDataCallbacks) != 0;
    bool wantExposureTime = GetArrayListSize(env, this->instance, jRoyaleCameraDevice_exposureTimeCallbacks) != 0;
    if (!wantEncoded && !wantFull) {
        LOGE("Encoded and full both not wanted!");
    }

    if (wantExposureTime) {
        env->CallVoidMethod(this->instance, jRoyaleCameraDevice_onExposureTime, exposureTimes);
    }
    if (wantFull) {
        jobjectArray pointCloud = env->NewObjectArray(n, jFloatArray, NULL);

        jint fillConfidence[n];
        jint fillGrayValue[n];
        jfloat fillNoise[n];

        for (i = 0; i < n; i++) {
            const auto pt = data->points[i];

            DO_CONFIDENCE(fillConfidence, i, pt);
            DO_GRAY_VALUE(fillGrayValue, i, pt);
            DO_NOISE(fillNoise, i, pt);

            DO_POINTCLOUD(pointCloud, i, pt);
        }

        jintArray confidence = env->NewIntArray(n);
        env->SetIntArrayRegion(confidence, 0, n, fillConfidence);
        jintArray grayValue = env->NewIntArray(n);
        env->SetIntArrayRegion(grayValue, 0, n, fillGrayValue);
        jfloatArray noise = env->NewFloatArray(n);
        env->SetFloatArrayRegion(noise, 0, n, fillNoise);

        auto royaleData = env->NewObject(jRoyaleDepthData, jRoyaleDepthData_init,
                                         (jint) data->version, // Int
                                         (jlong) data->timeStamp.count(), // Long
                                         (jint) data->streamId, // Int
                                         (jint) data->width, // Int
                                         (jint) data->height, // Int
                                         exposureTimes, // IntArray
                                         pointCloud, // FloatArray
                                         noise, // FloatArray
                                         confidence, // IntArray
                                         grayValue // IntArray
        );

        env->CallVoidMethod(this->instance, jRoyaleCameraDevice_onFullDepthData, royaleData);

        env->DeleteLocalRef(pointCloud);
        env->DeleteLocalRef(noise);
        env->DeleteLocalRef(confidence);
        env->DeleteLocalRef(grayValue);
        env->DeleteLocalRef(royaleData);
    }
    if (wantEncoded) {
        const size_t elementSize = 1 + 4 * 4 + 1;
        auto fillEncoded = new jbyte[n * elementSize];

        for (i = 0; i < n; i++) {
            const auto pt = data->points[i];

            DO_FILL_ENCODED(fillEncoded, elementSize, i, pt);
        }

        jbyteArray encoded = env->NewByteArray(n * elementSize);
        env->SetByteArrayRegion(encoded, 0, n * elementSize, fillEncoded);
        delete[] fillEncoded;

        env->CallVoidMethod(this->instance, jRoyaleCameraDevice_onEncodedDepthData, encoded);

        env->DeleteLocalRef(encoded);
    }

    env->DeleteLocalRef(exposureTimes);

    // detach from the JavaVM thread
    javaVM->DetachCurrentThread();
}

#undef GET_BYTE
#undef DO_FILL_ENCODED
#undef DO_POINTCLOUD
#undef DO_CONFIDENCE
#undef DO_GRAY_VALUE
#undef DO_NOISE

extern "C" {
JNIEXPORT void JNICALL Java_com_example_picoflexxtest_royale_RoyaleCameraDevice_init(
        JNIEnv *env, jobject instance
) {
    auto self = new RoyaleCameraDevice(env->NewGlobalRef(instance));

    env->SetLongField(instance, jRoyaleCameraDevice_ptr, (jlong) self);
}

JNIEXPORT void JNICALL Java_com_example_picoflexxtest_royale_RoyaleCameraDevice_openCameraNative(
        JNIEnv *env, jobject instance, jint fd, jint vid, jint pid
) {
    auto self = SELF;

    {
        auto cFD = static_cast<uint32_t> (fd);
        auto cVID = static_cast<uint32_t> (vid);
        auto cPID = static_cast<uint32_t> (pid);

        royale::CameraManager manager;

        auto cameraList = manager.getConnectedCameraList(cFD, cVID, cPID);
        LOGI ("Detected %zu camera(s).", cameraList.size());

        if (!cameraList.empty()) {
            self->cameraDevice = manager.createCamera(cameraList.at(0));
        }
    }

    // the camera device is now available and CameraManager can be deallocated here
    if (self->cameraDevice == nullptr) {
        LOGI ("Cannot create the camera device");
        ThrowRoyaleException("Failed to create the camera device", -1);
        return;
    }

    // IMPORTANT: call the initialize method before working with the camera device
    auto ret = self->cameraDevice->initialize();
    if (ret != royale::CameraStatus::SUCCESS) {
        LOGE ("Cannot initialize the camera device, CODE %d", static_cast<uint32_t> (ret));
        ThrowRoyaleException("Failed to create the camera device", static_cast<int>(ret));
        return;
    }

    // IMPORTANT: call the initialize method before working with the camera device
    ret = self->cameraDevice->registerDataListener(self);
    if (ret != royale::CameraStatus::SUCCESS) {
        LOGE ("Failed to register data listener, CODE %d", static_cast<uint32_t> (ret));
        ThrowRoyaleException("Failed to register data listener", static_cast<int>(ret));
        return;
    }
}

JNIEXPORT void JNICALL Java_com_example_picoflexxtest_royale_RoyaleCameraDevice_deinit(
        JNIEnv *env, jobject instance
) {
    auto self = SELF;

    LOGI("Closing RoyaleCameraDevice %p", self);

    auto ret = self->cameraDevice->unregisterDataListener();
    if (ret != royale::CameraStatus::SUCCESS) {
        LOGE ("Failed to unregister data listener, CODE %d", static_cast<uint32_t> (ret));
        ThrowRoyaleException("Failed to unregister data listener", static_cast<int>(ret));
        return;
    }

    env->DeleteGlobalRef(self->instance);
    self->cameraDevice.reset();
    delete self;

    env->SetLongField(instance, jRoyaleCameraDevice_ptr, (jlong) 0);
}

JNIEXPORT void JNICALL Java_com_example_picoflexxtest_royale_RoyaleCameraDevice_stopCapture(
        JNIEnv *env, jobject instance
) {
    auto ret = SELF->cameraDevice->stopCapture();
    if (ret != royale::CameraStatus::SUCCESS) {
        ThrowRoyaleException("Failed to stop capture", (int) ret)
        return;
    }
}

JNIEXPORT void JNICALL Java_com_example_picoflexxtest_royale_RoyaleCameraDevice_startCapture(
        JNIEnv *env, jobject instance
) {
    auto ret = SELF->cameraDevice->startCapture();
    if (ret != royale::CameraStatus::SUCCESS) {
        ThrowRoyaleException("Failed to start capture", (int) ret)
        return;
    }
}

JNIEXPORT jstring JNICALL Java_com_example_picoflexxtest_royale_RoyaleCameraDevice_getCameraName(
        JNIEnv *env, jobject instance
) {
    royale::String cameraName;

    auto ret = SELF->cameraDevice->getCameraName(cameraName);
    if (ret != royale::CameraStatus::SUCCESS) {
        ThrowRoyaleException("Failed to get camera name", (int) ret)
        return nullptr;
    }

    return env->NewStringUTF(cameraName.c_str());
}

JNIEXPORT jstring JNICALL Java_com_example_picoflexxtest_royale_RoyaleCameraDevice_getCameraId(
        JNIEnv *env, jobject instance
) {
    royale::String cameraId;

    auto ret = SELF->cameraDevice->getId(cameraId);
    if (ret != royale::CameraStatus::SUCCESS) {
        ThrowRoyaleException("Failed to get camera id", (int) ret)
        return nullptr;
    }

    return env->NewStringUTF(cameraId.c_str());
}

JNIEXPORT jint JNICALL Java_com_example_picoflexxtest_royale_RoyaleCameraDevice_getMaxSensorWidth(
        JNIEnv *env, jobject instance
) {
    auto self = SELF;

    auto ret = self->cameraDevice->getMaxSensorWidth(self->width);
    if (ret != royale::CameraStatus::SUCCESS) {
        ThrowRoyaleException("Failed to get max sensor width", (int) ret)
        return -1;
    }

    return (jint) self->width;
}

JNIEXPORT jint JNICALL Java_com_example_picoflexxtest_royale_RoyaleCameraDevice_getMaxSensorHeight(
        JNIEnv *env, jobject instance
) {
    auto self = SELF;

    auto ret = self->cameraDevice->getMaxSensorHeight(self->height);
    if (ret != royale::CameraStatus::SUCCESS) {
        ThrowRoyaleException("Failed to get max sensor height", (int) ret)
        return -1;
    }

    return (jint) self->height;
}

JNIEXPORT jint JNICALL Java_com_example_picoflexxtest_royale_RoyaleCameraDevice_getFrameRate(
        JNIEnv *env, jobject instance
) {
    auto self = SELF;

    uint16_t frameRate;

    auto ret = self->cameraDevice->getFrameRate(frameRate);
    if (ret != royale::CameraStatus::SUCCESS) {
        ThrowRoyaleException("Failed to get frame rate", (int) ret)
        return -1;
    }

    return (jint) frameRate;
}

JNIEXPORT jint JNICALL Java_com_example_picoflexxtest_royale_RoyaleCameraDevice_getMaxFrameRate(
        JNIEnv *env, jobject instance
) {
    auto self = SELF;

    uint16_t frameRate;

    auto ret = self->cameraDevice->getMaxFrameRate(frameRate);
    if (ret != royale::CameraStatus::SUCCESS) {
        ThrowRoyaleException("Failed to get max frame rate", (int) ret)
        return -1;
    }

    return (jint) frameRate;
}

JNIEXPORT jintArray JNICALL Java_com_example_picoflexxtest_royale_RoyaleCameraDevice_getExposureLimits(
        JNIEnv *env, jobject instance
) {
    auto self = SELF;

    royale::Pair<uint32_t, uint32_t> limits;
    jintArray jLimits = env->NewIntArray(2);

    auto ret = self->cameraDevice->getExposureLimits(limits);
    if (ret != royale::CameraStatus::SUCCESS) {
        ThrowRoyaleException("Failed to get exposure limits", (int) ret)
        return jLimits;
    }

    jint fillLimits[] = {(jint) limits.first, (jint) limits.second};
    env->SetIntArrayRegion(jLimits, 0, 2, fillLimits);

    return jLimits;
}

JNIEXPORT jobject JNICALL Java_com_example_picoflexxtest_royale_RoyaleCameraDevice_getUseCases(
        JNIEnv *env, jobject instance
) {
    royale::Vector<royale::String> opModes;

    auto ret = SELF->cameraDevice->getUseCases(opModes);
    if (ret != royale::CameraStatus::SUCCESS) {
        ThrowRoyaleException("Failed to get use cases", (int) ret)
        return nullptr;
    }

    jobject opModesList = env->NewObject(jArrayList, jArrayList_init, opModes.size());
    for (const auto &mode : opModes) {
        env->CallBooleanMethod(opModesList, jArrayList_add, env->NewStringUTF(mode.c_str()));
    }

    return opModesList;
}

JNIEXPORT jstring JNICALL Java_com_example_picoflexxtest_royale_RoyaleCameraDevice_getCurrentUseCase(
        JNIEnv *env, jobject instance
) {
    royale::String useCase;

    auto ret = SELF->cameraDevice->getCurrentUseCase(useCase);
    if (ret != royale::CameraStatus::SUCCESS) {
        ThrowRoyaleException("Failed to get use case", (int) ret)
        return nullptr;
    }

    return env->NewStringUTF(useCase.c_str());
}

JNIEXPORT void JNICALL Java_com_example_picoflexxtest_royale_RoyaleCameraDevice_setUseCase(
        JNIEnv *env, jobject instance, jstring jstr_usecase
) {
    const char *usecase = env->GetStringUTFChars(jstr_usecase, nullptr);
    auto ret = SELF->cameraDevice->setUseCase(usecase);
    env->ReleaseStringUTFChars(jstr_usecase, usecase);
    if (ret != royale::CameraStatus::SUCCESS) {
        ThrowRoyaleException ("Failed to set use case", (int) ret);
        return;
    }
}

JNIEXPORT jboolean JNICALL Java_com_example_picoflexxtest_royale_RoyaleCameraDevice_isConnected(
        JNIEnv *env, jobject instance
) {
    bool connected;

    auto ret = SELF->cameraDevice->isConnected(connected);
    if (ret != royale::CameraStatus::SUCCESS) {
        ThrowRoyaleException("Failed to get isConnected", (int) ret)
        return static_cast<jboolean>(false);
    }

    return (jboolean) connected;
}

JNIEXPORT jboolean JNICALL Java_com_example_picoflexxtest_royale_RoyaleCameraDevice_isCapturing(
        JNIEnv *env, jobject instance
) {
    bool capturing;

    auto ret = SELF->cameraDevice->isCapturing(capturing);
    if (ret != royale::CameraStatus::SUCCESS) {
        ThrowRoyaleException("Failed to get isCapturing", (int) ret)
        return static_cast<jboolean>(false);
    }

    return (jboolean) capturing;
}

JNIEXPORT jboolean JNICALL Java_com_example_picoflexxtest_royale_RoyaleCameraDevice_getExposureMode(
        JNIEnv *env, jobject instance
) {
    royale::ExposureMode exposureMode;

    auto ret = SELF->cameraDevice->getExposureMode(exposureMode);
    if (ret != royale::CameraStatus::SUCCESS) {
        ThrowRoyaleException("Failed to get exposure mode", (int) ret)
        return false;
    }

    return (jboolean) (exposureMode == royale::ExposureMode::AUTOMATIC);
}

JNIEXPORT void JNICALL Java_com_example_picoflexxtest_royale_RoyaleCameraDevice_setExposureMode(
        JNIEnv *env, jobject instance, jboolean exposure_mode
) {
    auto ret = SELF->cameraDevice->setExposureMode(
            exposure_mode
            ? royale::ExposureMode::AUTOMATIC
            : royale::ExposureMode::MANUAL
    );
    if (ret != royale::CameraStatus::SUCCESS) {
        ThrowRoyaleException ("Failed to set exposure mode", (int) ret);
        return;
    }
}

JNIEXPORT void JNICALL Java_com_example_picoflexxtest_royale_RoyaleCameraDevice_setExposureTime(
        JNIEnv *env, jobject instance, jlong exposure_time
) {
    auto ret = SELF->cameraDevice->setExposureTime(exposure_time);
    if (ret != royale::CameraStatus::SUCCESS) {
        ThrowRoyaleException ("Failed to set exposure time", (int) ret);
        return;
    }
}
}
