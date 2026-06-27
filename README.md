# Detect Emeny - 基于 YOLO 的人体姿态识别 (YOLO Pose Detection)

[English Version Below](#english-version)

Detect Emeny 是一款基于 **NCNN** 框架和 **YOLO11n-Pose** 模型的 Android 实时人体姿态识别应用。它不仅提供高性能的端侧 AI 识别体验，还融入了丰富的互动游戏元素。

![Gameplay Preview](lv_0_20260627104512.gif)

## 主要功能

### 1. 实时姿态估计
*   采用专为移动端优化的 **YOLO11n-Pose** 高性能模型。
*   支持 **高帧率模式 (60 FPS)**：若摄像头硬件支持，自动开启 60 FPS 检测。
*   **显示切换**：支持一键开启/关闭姿态识别骨架及关键点显示，提供更纯净的游戏视角。

### 2. 双武器系统与切水果游戏
*   **双手武器叠加**：支持在左、右手腕处分别独立叠加武器（如长剑、棒球棍）。
*   **水果与炸弹**：
    *   **丰富种类**：随机生成多种水果（苹果、香蕉、西瓜等）。
    *   **黑色炸弹**：随机出现黑色炸弹 (💣)，击中将**扣除 50 分**，增加游戏挑战性。
*   **表情包头显**：一键开启“头戴模式”，使用有趣的表情包（😎, 🤡, 🤖 等）遮挡面部，支持多种表情切换。
*   **切水果挑战**：基于武器尖端的实时碰撞检测，触发爆炸音效与视觉反馈。

### 3. 实时游戏配置与排行榜
*   **即时参数调整**：内置“游戏配置”面板，支持在运行时动态调整：
    *   水果生成频率 (0.5 - 10 个/秒)。
    *   水果移动速度区间。
    *   炸弹出现概率 (0% - 20%)。
*   **高分排行榜**：点击上方得分文字即可弹出**历史前 10 名**高分记录，并支持“保存并重置”当前游戏。

### 4. 深度可配置化 (`AppConfig`)
*   支持在配置文件中精细调整基础参数：武器尺寸、水果尺寸、透明度、平滑因子等。

### 5. 高性能架构
*   底层基于 **ncnn** 神经网络框架，支持 **Vulkan** GPU 加速。
*   基于 CameraX 和 Jetpack Compose 构建的现代化 UI 界面。

---

<a name="english-version"></a>

# Detect Emeny - YOLO Pose Detection

Detect Emeny is an Android application that performs real-time human pose estimation using the **NCNN** framework and **YOLO11n-Pose** model, featuring interactive gaming elements.

![Gameplay Preview](lv_0_20260627104512.gif)

## Features

### 1. Real-Time Pose Estimation
*   Uses a high-performance **YOLO11n-Pose** model.
*   Supports **High Frame Mode (60 FPS)** on compatible camera hardware.
*   **Visual Toggle**: Easily show or hide the skeletal overlay for a cleaner gameplay experience.

### 2. Dual Weapon System & Game Elements
*   **Dual Weapon Overlay**: Independently attach weapons (Sword, Bat) to **both wrists**.
*   **Fruits & Bombs**:
    *   Slice various fruits for points.
    *   Avoid **Black Bombs (💣)**: Hitting a bomb results in a **50-point penalty**.
*   **Emoji Head Mask**: Toggle "Head Mask" mode to cover your face with fun emojis (😎, 🤡, 🤖, etc.). Includes an emoji picker to choose your favorite mask.
*   **Collision Detection**: Real-time tracking of weapon tips for accurate slicing feedback.

### 3. Real-Time Config & Leaderboard
*   **Dynamic Settings**: A dedicated "Game Config" button allows adjusting parameters during play:
    *   Spawn rate (0.5 to 10 fruits/sec).
    *   Fruit speed ranges.
    *   Bomb spawn probability (0% to 20%).
*   **Top 10 Leaderboard**: Tap the score display to view your personal high scores. Supports saving sessions and resetting scores.

### 4. Extensive Customization
*   Centralized `AppConfig` for weapon scaling, fruit size, smoothing, and transparency.

### 5. Technical Excellence
*   Powered by **ncnn** with **Vulkan** GPU acceleration.
*   Modern tech stack: Kotlin, Jetpack Compose, CameraX, and C++.
