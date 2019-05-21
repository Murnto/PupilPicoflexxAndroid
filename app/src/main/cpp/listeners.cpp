#include "listeners.h"

#undef TAG
#define TAG "com.example.picoflexxtest.royale.listeners"

void RoyaleDataListener::onNewData(const royale::DepthData *data) {
    /*
     * There might be different ExposureTimes per RawFrameSet resulting in a vector of
     * exposureTimes, while however the last one is fixed and purely provided for further
     * reference.
     */

    if (data->exposureTimes.size() >= 3) {
        LOGI ("ExposureTimes: %d, %d, %d", data->exposureTimes.at(0), data->exposureTimes.at(1),
              data->exposureTimes.at(2));
    }

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

    jint fillConfidence[n];
    for (i = 0; i < n; i++) {
        fillConfidence[i] = data->points[i].depthConfidence;
    }
    jintArray confidence = env->NewIntArray(n);
    env->SetIntArrayRegion(confidence, 0, n, fillConfidence);

    jint fillGrayValue[n];
    for (i = 0; i < n; i++) {
        fillGrayValue[i] = data->points[i].grayValue;
    }
    jintArray grayValue = env->NewIntArray(n);
    env->SetIntArrayRegion(grayValue, 0, n, fillGrayValue);

    jobjectArray pointCloud = env->NewObjectArray(n, jFloatArray, NULL);
    for (i = 0; i < n; i++) {
        const auto pt = data->points[i];
        jfloat fillFloatArray[] = {pt.x, pt.y, pt.z};
        jfloatArray floatArray = env->NewFloatArray(3); // x, y, z
        env->SetFloatArrayRegion(floatArray, (jsize) 0, (jsize) 3, fillFloatArray);
        env->SetObjectArrayElement(pointCloud, (jsize) i, floatArray);
        env->DeleteLocalRef(floatArray);
    }

    jfloat fillNoise[n];
    for (i = 0; i < n; i++) {
        fillNoise[i] = data->points[i].noise;
    }
    jfloatArray noise = env->NewFloatArray(n);
    env->SetFloatArrayRegion(noise, 0, n, fillNoise);

    const size_t elementSize = 1 + 4 * 4 + 1;
    jbyte *fillEncoded = new jbyte[n * elementSize];
    for (i = 0; i < n; i++) {
        const auto pt = data->points[i];
        fillEncoded[i * elementSize + 0] = (jbyte) (pt.grayValue >> 4 & 0xFF);

        fillEncoded[i * elementSize + 1 + 0] = (jbyte) ((uint32_t) pt.x >> 0 & 0xFF);
        fillEncoded[i * elementSize + 1 + 1] = (jbyte) ((uint32_t) pt.x >> 8 & 0xFF);
        fillEncoded[i * elementSize + 1 + 2] = (jbyte) ((uint32_t) pt.x >> 16 & 0xFF);
        fillEncoded[i * elementSize + 1 + 3] = (jbyte) ((uint32_t) pt.x >> 24 & 0xFF);

        fillEncoded[i * elementSize + 5 + 0] = (jbyte) ((uint32_t) pt.y >> 0 & 0xFF);
        fillEncoded[i * elementSize + 5 + 1] = (jbyte) ((uint32_t) pt.y >> 8 & 0xFF);
        fillEncoded[i * elementSize + 5 + 2] = (jbyte) ((uint32_t) pt.y >> 16 & 0xFF);
        fillEncoded[i * elementSize + 5 + 3] = (jbyte) ((uint32_t) pt.y >> 24 & 0xFF);

        fillEncoded[i * elementSize + 9 + 0] = (jbyte) ((uint32_t) pt.z >> 0 & 0xFF);
        fillEncoded[i * elementSize + 9 + 1] = (jbyte) ((uint32_t) pt.z >> 8 & 0xFF);
        fillEncoded[i * elementSize + 9 + 2] = (jbyte) ((uint32_t) pt.z >> 16 & 0xFF);
        fillEncoded[i * elementSize + 9 + 3] = (jbyte) ((uint32_t) pt.z >> 24 & 0xFF);

        fillEncoded[i * elementSize + 13 + 0] = (jbyte) ((uint32_t) pt.noise >> 0 & 0xFF);
        fillEncoded[i * elementSize + 13 + 1] = (jbyte) ((uint32_t) pt.noise >> 8 & 0xFF);
        fillEncoded[i * elementSize + 13 + 2] = (jbyte) ((uint32_t) pt.noise >> 16 & 0xFF);
        fillEncoded[i * elementSize + 13 + 3] = (jbyte) ((uint32_t) pt.noise >> 24 & 0xFF);

        fillEncoded[i * elementSize + 17] = (jbyte) pt.depthConfidence;
    }
    jbyteArray encoded = env->NewByteArray(n * elementSize);
    env->SetByteArrayRegion(encoded, 0, n * elementSize, fillEncoded);

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
                                     grayValue, // IntArray
                                     encoded
    );

    env->CallVoidMethod(this->callback, jDataListener_onData, royaleData);

    env->DeleteLocalRef(royaleData);
    env->DeleteLocalRef(exposureTimes);
    env->DeleteLocalRef(pointCloud);
    env->DeleteLocalRef(noise);
    env->DeleteLocalRef(confidence);
    env->DeleteLocalRef(grayValue);
    env->DeleteLocalRef(encoded);

    // detach from the JavaVM thread
    javaVM->DetachCurrentThread();
}

void RoyaleIRListener::onNewData(const royale::IRImage *data) {
    /* Demonstration of how to retrieve exposureTimes
    * There might be different ExposureTimes per RawFrameSet resulting in a vector of
    * exposureTimes, while however the last one is fixed and purely provided for further
    * reference. */

    LOGI ("onNewData");

    size_t i;
    jsize n = data->width * data->height;

    // Fill a temp structure to use to populate the java int array;
    jint fill[n];
    for (i = 0; i < n; i++) {
        fill[i] = data->data[i];
    }

    // Attach to the JavaVM thread and get a JNI interface pointer.
    JNIEnv *env;
    javaVM->AttachCurrentThread(&env, NULL);

    auto irData = env->NewIntArray(n);
    env->SetIntArrayRegion(irData, 0, n, fill);

    // call java method and pass amplitude array
    env->CallVoidMethod(this->callback, jIrListener_onIrData, irData);

    env->DeleteLocalRef(irData);

    // detach from the JavaVM thread
    javaVM->DetachCurrentThread();
}
