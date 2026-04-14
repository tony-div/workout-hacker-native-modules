#include <jni.h>
#include "PoseLandmarksOnLoad.hpp"

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
  return margelo::nitro::poselandmarks::initialize(vm);
}
