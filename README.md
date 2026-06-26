# Detect Emny - YOLO Pose Detection

Detect Emny is an Android application that performs real-time human pose estimation using the **NCNN** framework and **YOLO11n-Pose** model. The app provides a high-performance, on-device AI experience with smooth visual feedback.

## Features

### 1. Real-Time Pose Estimation
*   Uses a high-performance **YOLO11n-Pose** model optimized for mobile devices.
*   Supports **High Frame Mode (60 FPS)**: Automatically enables 60 FPS detection if the camera hardware supports it, providing ultra-smooth tracking.
*   Detects up to 17 human keypoints (nose, eyes, shoulders, elbows, wrists, hips, knees, and ankles).
*   Visualizes keypoints and skeletal connections with a dynamic overlay.

### 2. High Performance NCNN Integration
*   Native C++ implementation using the **ncnn** neural network computing framework.
*   Leverages **Vulkan Compute** for GPU acceleration (where supported) and optimized SIMD instructions for ARM CPUs.
*   Includes a robust JNI bridge for efficient communication between Kotlin and C++.

### 3. Dual Camera Support
*   Seamlessly toggle between **Front** and **Back** cameras.
*   Intelligent **Mirroring Correction**: The detection overlay correctly flips horizontally when using the front camera to match the mirrored preview, ensuring perfect alignment.

### 4. Interactive UI & Feedback
*   **Haptic Feedback**: Vibrates when a person is detected in the frame.
*   **Real-Time Metrics**: Displays current FPS (Frames Per Second) and confidence scores for all detections.
*   **Detailed View**: A scrollable list providing precise coordinate data for every detected person.

### 5. Enhanced User Experience
*   **Screen Stay-Awake**: The app prevents the screen from dimming or turning off during use (`FLAG_KEEP_SCREEN_ON`).
*   **Dynamic Permissions**: Modern Android permission handling for camera access.
*   **Smooth Previews**: Utilizes Jetpack Compose and CameraX for a responsive and battery-efficient camera interface.

## Technical Details

*   **Model**: YOLO11n-Pose (NCNN format)
*   **Frameworks**: Jetpack Compose, CameraX, NCNN (Native SDK)
*   **Language**: Kotlin, C++
*   **Build System**: Gradle, CMake

## How it works

1.  The app captures frames from the camera using CameraX.
2.  Frames are passed to the native NCNN library via JNI.
3.  The C++ layer processes the image (resize, padding, normalization) and runs inference.
4.  Detected poses are passed back to Kotlin.
5.  A Compose `Canvas` overlay renders the skeleton, adjusted for the current camera's orientation and mirroring.
