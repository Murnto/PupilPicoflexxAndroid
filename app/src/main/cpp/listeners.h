#ifndef PICOFLEXXTEST_LISTENERS_H
#define PICOFLEXXTEST_LISTENERS_H

#include <jni.h>
#include <royale/CameraManager.hpp>

#include <royale/ICameraDevice.hpp>
#include <android/log.h>
#include <iostream>
#include <thread>
#include <chrono>
#include <memory>

#include "common.h"

extern JavaVM *javaVM;

extern jclass jDataListener;
extern jmethodID jDataListener_onData;

extern jclass jIrListener;
extern jmethodID jIrListener_onIrData;

class RoyaleDataListener : public royale::IDepthDataListener {
public:
    jobject callback;

    RoyaleDataListener(jobject callback) { this->callback = callback; }

    void onNewData(const royale::DepthData *data) override;
};

class RoyaleIRListener : public royale::IIRImageListener {
public:
    jobject callback;

    RoyaleIRListener(jobject callback) { this->callback = callback; }

    void onNewData(const royale::IRImage *data) override;
};

#endif //PICOFLEXXTEST_LISTENERS_H
