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
    jint fillGrayValue[n];
    jfloat fillNoise[n];

#define GET_BYTE(x, y) (*((uint8_t *) (((char*) &(x)) + (y))))

    jobjectArray pointCloud = env->NewObjectArray(n, jFloatArray, NULL);
    const size_t elementSize = 1 + 4 * 4 + 1;
    jbyte *fillEncoded = new jbyte[n * elementSize];
    for (i = 0; i < n; i++) {
        const auto pt = data->points[i];

        fillEncoded[i * elementSize + 0] = (jbyte) ((pt.grayValue >> 4) & 0xFF);

        fillEncoded[i * elementSize + 1 + 0] = (jbyte) GET_BYTE(pt.x, 0);
        fillEncoded[i * elementSize + 1 + 1] = (jbyte) GET_BYTE(pt.x, 1);
        fillEncoded[i * elementSize + 1 + 2] = (jbyte) GET_BYTE(pt.x, 2);
        fillEncoded[i * elementSize + 1 + 3] = (jbyte) GET_BYTE(pt.x, 3);

        fillEncoded[i * elementSize + 5 + 0] = (jbyte) GET_BYTE(pt.y, 0);
        fillEncoded[i * elementSize + 5 + 1] = (jbyte) GET_BYTE(pt.y, 1);
        fillEncoded[i * elementSize + 5 + 2] = (jbyte) GET_BYTE(pt.y, 2);
        fillEncoded[i * elementSize + 5 + 3] = (jbyte) GET_BYTE(pt.y, 3);

        fillEncoded[i * elementSize + 9 + 0] = (jbyte) GET_BYTE(pt.z, 0);
        fillEncoded[i * elementSize + 9 + 1] = (jbyte) GET_BYTE(pt.z, 1);
        fillEncoded[i * elementSize + 9 + 2] = (jbyte) GET_BYTE(pt.z, 2);
        fillEncoded[i * elementSize + 9 + 3] = (jbyte) GET_BYTE(pt.z, 3);

        fillEncoded[i * elementSize + 13 + 0] = (jbyte) GET_BYTE(pt.noise, 0);
        fillEncoded[i * elementSize + 13 + 1] = (jbyte) GET_BYTE(pt.noise, 1);
        fillEncoded[i * elementSize + 13 + 2] = (jbyte) GET_BYTE(pt.noise, 2);
        fillEncoded[i * elementSize + 13 + 3] = (jbyte) GET_BYTE(pt.noise, 3);

        fillEncoded[i * elementSize + 17] = (jbyte) pt.depthConfidence;

        fillConfidence[i] = pt.depthConfidence;
        fillGrayValue[i] = pt.grayValue;
        fillNoise[i] = pt.noise;

        jfloat fillFloatArray[] = {pt.x, pt.y, pt.z};
        jfloatArray floatArray = env->NewFloatArray(3); // x, y, z
        env->SetFloatArrayRegion(floatArray, (jsize) 0, (jsize) 3, fillFloatArray);
        env->SetObjectArrayElement(pointCloud, (jsize) i, floatArray);
        env->DeleteLocalRef(floatArray);
    }
    jintArray confidence = env->NewIntArray(n);
    env->SetIntArrayRegion(confidence, 0, n, fillConfidence);
    jintArray grayValue = env->NewIntArray(n);
    env->SetIntArrayRegion(grayValue, 0, n, fillGrayValue);
    jfloatArray noise = env->NewFloatArray(n);
    env->SetFloatArrayRegion(noise, 0, n, fillNoise);
    jbyteArray encoded = env->NewByteArray(n * elementSize);
    env->SetByteArrayRegion(encoded, 0, n * elementSize, fillEncoded);
    delete[] fillEncoded;

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
                                     encoded // ByteArray
    );

    env->CallVoidMethod(this->callback, jDataListener_onData, royaleData);

    env->DeleteLocalRef(exposureTimes);
    env->DeleteLocalRef(pointCloud);
    env->DeleteLocalRef(noise);
    env->DeleteLocalRef(confidence);
    env->DeleteLocalRef(grayValue);
    env->DeleteLocalRef(encoded);
    env->DeleteLocalRef(royaleData);

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
