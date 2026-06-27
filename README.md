# Detect Emeny - 基于 YOLO 的人体姿态识别 (YOLO Pose Detection)

[English Version Below](#english-version)

Detect Emeny 是一款基于 **NCNN** 框架和 **YOLO11n-Pose** 模型的 Android 实时人体姿态识别应用。它不仅提供高性能的端侧 AI 识别体验，还融入了丰富的互动游戏元素。

![Gameplay Preview](lv_0_20260627104512.gif)

## 主要功能

### 1. 实时姿态估计
*   采用专为移动端优化的 **YOLO11n-Pose** 高性能模型。
*   支持 **高帧率模式 (60 FPS)**：若摄像头硬件支持，自动开启 60 FPS 检测。
*   **显示切换**：支持一键开启/关闭姿态识别骨架及关键点显示，默认启动时为关闭状态。

### 2. 双武器系统与切水果游戏
*   **双手武器叠加**：支持在左、右手腕处分别叠加武器（如长剑、棒球棍）。
*   **激光剑残影**：武器挥动时带有类似《星球大战》的**发光残影 (Laser Trail)**，左手为青蓝色，右手为品红色。
*   **精细碰撞逻辑**：仅武器的**上半部分（刃部）**支持有效打击，握把部分不触发碰撞。
*   **水果与炸弹**：
    *   **迪士尼沙尘特效**：击中水果时触发金色的“迪士尼沙尘”粒子迸发效果。
    *   **暗黑炸弹特效**：击中炸弹 (💣) 时产生暗紫色烟雾与黑洞视觉效果。
*   **音效系统**：内置高性能 `SoundPool` 音效引擎，提供真实的西瓜切割声与炸弹爆炸声。
*   **表情包头显**：一键开启“头戴模式”，使用有趣的表情包（😎, 🤡, 🤖 等）遮挡面部。

### 3. 实时游戏配置与记录
*   **即时参数调整**：支持运行时调整水果频率、移动速度区间、炸弹概率。
*   **双指标显示**：界面上方同时显示**当前得分 (SCORE)** 与 **炸弹击中数 (BOMBS)**。
*   **详细结算记录**：点击“保存并重置”会弹出**结算面板**，展示本次游戏的得分与炸弹数，并同步至排行榜。
*   **高分排行榜**：历史前 10 名高分记录中会详细标注每条记录的得分、炸弹数及时间。

### 4. 优化与稳定性
*   **启动优化**：应用启动时无需等待姿态识别即可立即生成水果。
*   **性能提升**：修正了水果移动逻辑，确保在无姿态识别时依然保持 60 FPS 的流畅度。

---

<a name="english-version"></a>

# Detect Emeny - YOLO Pose Detection

Detect Emeny is an Android application that performs real-time human pose estimation using the **NCNN** framework and **YOLO11n-Pose** model, featuring interactive gaming elements.

![Gameplay Preview](lv_0_20260627104512.gif)

## Features

### 1. Real-Time Pose Estimation
*   Uses a high-performance **YOLO11n-Pose** model.
*   Supports **High Frame Mode (60 FPS)** on compatible camera hardware.
*   **Visual Toggle**: Easily show or hide the skeletal overlay. (Disabled by default at startup).

### 2. Dual Weapon System & Game Elements
*   **Dual Weapon Overlay**: Attach weapons (Sword, Bat) to **both wrists**.
*   **Laser Sword Trails**: Weapons feature glowing transparency trails similar to **Star Wars light sabers**. Left hand uses **Deep Sky Blue**, right hand uses **Magenta**.
*   **Refined Hit Logic**: Only the **top 50% (blade)** of the weapon triggers collisions, ensuring the handle/grip doesn't count as a hit.
*   **Fruits & Bombs**:
    *   **Disney Sand Burst**: Slicing fruits triggers a magical golden "Disney Sand" particle effect.
    *   **Dark Void Effect**: Hitting a bomb (💣) triggers a dark purple smoke and void explosion.
*   **Sound Effects**: High-performance `SoundPool` engine provides immersive slicing and explosion sounds.
*   **Emoji Head Mask**: Cover your face with fun emojis (😎, 🤡, 🤖, etc.).

### 3. Real-Time Config & Records
*   **Dynamic Settings**: Adjust spawn rate, speed ranges, and bomb probability on the fly.
*   **Dual Counter Display**: HUD shows both **SCORE** and **BOMBS** count.
*   **Detailed Summary**: Saving a session triggers a **Summary Pop-up** showing your final score and bomb count.
*   **Advanced Leaderboard**: The top 10 list now records and displays both scores and bomb counts for every session.

### 4. Technical Optimizations
*   **Instant Start**: Fruits begin spawning immediately upon startup, independent of initial pose detection.
*   **Smooth Gameplay**: Optimized game loop ensures 60 FPS movement logic even when no pose is being tracked.
