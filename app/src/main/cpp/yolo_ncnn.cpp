#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>
#include <string>
#include <vector>

// ncnn
#include "ncnn/net.h"
#include "ncnn/cpu.h"

#define TAG "YoloNcnnJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct Object {
    float x;
    float y;
    float w;
    float h;
    float prob;
    struct Keypoint {
        float x;
        float y;
        float prob;
    };
    std::vector<Keypoint> keypoints;
};

static ncnn::Net yolo;
static int target_size = 640;

static jclass clsPose = NULL;
static jmethodID constructorPose = NULL;
static jclass clsBox = NULL;
static jmethodID constructorBox = NULL;
static jclass clsKpt = NULL;
static jmethodID constructorKpt = NULL;
static jclass clsList = NULL;
static jmethodID constructorList = NULL;
static jmethodID addMethod = NULL;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_detect_1emeny_YoloPoseDetector_init(JNIEnv* env, jobject thiz, jobject assetManager) {
    ncnn::Option opt;
    opt.lightmode = true;
    opt.num_threads = 4;
    opt.use_vulkan_compute = true;
    opt.use_fp16_arithmetic = true;
    yolo.opt = opt;

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    int ret_param = yolo.load_param(mgr, "yolo11n_pose.ncnn.param");
    int ret_bin = yolo.load_model(mgr, "yolo11n_pose.ncnn.bin");

    if (ret_param != 0 || ret_bin != 0) {
        LOGE("Load model failed: %d %d", ret_param, ret_bin);
        return JNI_FALSE;
    }

    if (clsPose == NULL) {
        jclass localPose = env->FindClass("com/example/detect_emeny/model/PoseDetection");
        clsPose = (jclass)env->NewGlobalRef(localPose);
        constructorPose = env->GetMethodID(clsPose, "<init>", "(FLcom/example/detect_emeny/model/PoseBox;Ljava/util/List;)V");

        jclass localBox = env->FindClass("com/example/detect_emeny/model/PoseBox");
        clsBox = (jclass)env->NewGlobalRef(localBox);
        constructorBox = env->GetMethodID(clsBox, "<init>", "(FFFF)V");

        jclass localKpt = env->FindClass("com/example/detect_emeny/model/PoseKeypoint");
        clsKpt = (jclass)env->NewGlobalRef(localKpt);
        constructorKpt = env->GetMethodID(clsKpt, "<init>", "(FFF)V");

        jclass localList = env->FindClass("java/util/ArrayList");
        clsList = (jclass)env->NewGlobalRef(localList);
        constructorList = env->GetMethodID(clsList, "<init>", "(I)V");
        addMethod = env->GetMethodID(clsList, "add", "(Ljava/lang/Object;)Z");
    }

    LOGD("NCNN Native Initialized");
    return JNI_TRUE;
}

static float intersection_area(const Object& a, const Object& b) {
    float x1 = std::max(a.x, b.x);
    float y1 = std::max(a.y, b.y);
    float x2 = std::min(a.x + a.w, b.x + b.w);
    float y2 = std::min(a.y + a.h, b.y + b.h);
    if (x1 >= x2 || y1 >= y2) return 0.f;
    return (x2 - x1) * (y2 - y1);
}

static void qsort_descent_inplace(std::vector<Object>& objects, int left, int right) {
    int i = left, j = right;
    float p = objects[(left + right) / 2].prob;
    while (i <= j) {
        while (objects[i].prob > p) i++;
        while (objects[j].prob < p) j--;
        if (i <= j) {
            std::swap(objects[i], objects[j]);
            i++; j--;
        }
    }
    if (left < j) qsort_descent_inplace(objects, left, j);
    if (i < right) qsort_descent_inplace(objects, i, right);
}

static void nms_sorted_bboxes(const std::vector<Object>& objects, std::vector<int>& picked, float nms_threshold) {
    picked.clear();
    const int n = objects.size();
    std::vector<float> areas(n);
    for (int i = 0; i < n; i++) {
        areas[i] = objects[i].w * objects[i].h;
    }
    for (int i = 0; i < n; i++) {
        const Object& a = objects[i];
        int keep = 1;
        for (int j = 0; j < (int)picked.size(); j++) {
            const Object& b = objects[picked[j]];
            float inter_area = intersection_area(a, b);
            float union_area = areas[i] + areas[picked[j]] - inter_area;
            if (inter_area / union_area > nms_threshold) keep = 0;
        }
        if (keep) picked.push_back(i);
    }
}

JNIEXPORT jobjectArray JNICALL
Java_com_example_detect_1emeny_YoloPoseDetector_detect(JNIEnv* env, jobject thiz, jobject bitmap) {
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);
    const int width = info.width;
    const int height = info.height;

    ncnn::Mat in = ncnn::Mat::from_android_bitmap(env, bitmap, ncnn::Mat::PIXEL_RGB);

    int w = width;
    int h = height;
    float scale = 1.f;
    if (w > h) {
        scale = (float)target_size / w;
        w = target_size;
        h = h * scale;
    } else {
        scale = (float)target_size / h;
        h = target_size;
        w = w * scale;
    }

    ncnn::Mat in_resized;
    ncnn::resize_bilinear(in, in_resized, w, h);

    ncnn::Mat in_pad;
    int wpad = target_size - w;
    int hpad = target_size - h;
    ncnn::copy_make_border(in_resized, in_pad, hpad / 2, hpad - hpad / 2, wpad / 2, wpad - wpad / 2, ncnn::BORDER_CONSTANT, 114.f);

    const float mean_vals[3] = {0.f, 0.f, 0.f};
    const float norm_vals[3] = {1/255.f, 1/255.f, 1/255.f};
    in_pad.substract_mean_normalize(mean_vals, norm_vals);

    ncnn::Extractor ex = yolo.create_extractor();
    ex.input("in0", in_pad);

    ncnn::Mat out;
    ex.extract("out0", out);

    std::vector<Object> proposals;
    const float prob_threshold = 0.45f; // Increased for cleaner results
    const int num_points = 17;

    // Correct row parsing for YOLOv8/v11 Pose
    // Row 0-3: Box, Row 4: Score, Row 5-55: Keypoints (x,y,score)
    if (out.w == 8400) {
        for (int i = 0; i < out.w; i++) {
            float score = out.row(4)[i];
            if (score > prob_threshold) {
                Object obj;
                float cx = out.row(0)[i];
                float cy = out.row(1)[i];
                float ow = out.row(2)[i];
                float oh = out.row(3)[i];
                obj.x = (cx - ow * 0.5f - wpad / 2) / scale;
                obj.y = (cy - oh * 0.5f - hpad / 2) / scale;
                obj.w = ow / scale;
                obj.h = oh / scale;
                obj.prob = score;

                for (int j = 0; j < num_points; j++) {
                    Object::Keypoint kpt;
                    kpt.x = (out.row(5 + j * 3)[i] - wpad / 2) / scale;
                    kpt.y = (out.row(5 + j * 3 + 1)[i] - hpad / 2) / scale;
                    kpt.prob = out.row(5 + j * 3 + 2)[i];
                    obj.keypoints.push_back(kpt);
                }
                proposals.push_back(obj);
            }
        }
    }

    if (proposals.size() > 0) {
        qsort_descent_inplace(proposals, 0, proposals.size() - 1);
    }
    std::vector<int> picked;
    nms_sorted_bboxes(proposals, picked, 0.45f);

    int count = std::min((int)picked.size(), 5);
    jobjectArray jPoses = env->NewObjectArray(count, clsPose, NULL);

    for (int i = 0; i < count; i++) {
        const Object& obj = proposals[picked[i]];
        jobject jBox = env->NewObject(clsBox, constructorBox, obj.x, obj.y, obj.x + obj.w, obj.y + obj.h);
        jobject jKptList = env->NewObject(clsList, constructorList, num_points);
        for (const auto& kpt : obj.keypoints) {
            jobject jKpt = env->NewObject(clsKpt, constructorKpt, kpt.x, kpt.y, kpt.prob);
            env->CallBooleanMethod(jKptList, addMethod, jKpt);
            env->DeleteLocalRef(jKpt);
        }
        jobject jPose = env->NewObject(clsPose, constructorPose, obj.prob, jBox, jKptList);
        env->SetObjectArrayElement(jPoses, i, jPose);
        env->DeleteLocalRef(jBox);
        env->DeleteLocalRef(jKptList);
        env->DeleteLocalRef(jPose);
    }

    return jPoses;
}

}
