#ifndef PICOFLEXXTEST_ROYALECAMERADEVICE_H
#define PICOFLEXXTEST_ROYALECAMERADEVICE_H

#include <jni.h>
#include <memory>
#include <royale/ICameraDevice.hpp>


class RoyaleCameraDevice : public royale::IIRImageListener, public royale::IDepthDataListener {
    virtual ~RoyaleCameraDevice() {}

    void onNewData(const royale::IRImage *data);

    void onNewData(const royale::DepthData *data);

public:
    uint16_t width = 0;
    uint16_t height = 0;
    jobject instance = 0;
    std::unique_ptr<royale::ICameraDevice> cameraDevice;

    RoyaleCameraDevice(jobject instance) {
        this->instance = instance;
    }

    static int InitJni(JNIEnv *env);
};

#endif //PICOFLEXXTEST_ROYALECAMERADEVICE_H
