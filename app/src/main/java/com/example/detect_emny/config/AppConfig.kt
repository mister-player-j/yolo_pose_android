package com.example.detect_emeny.config

object AppConfig {
    // enable_weapon=1
    const val ENABLE_WEAPON = true
    
    // enable_pose_transparent=0.2
    const val POSE_TRANSPARENCY = 0.3f
    
    // esp32_usb_wire=1
    const val ENABLE_USB_CONTROL = false

    // enable_position_display=1
    const val ENABLE_POSITION_DISPLAY = false
    
    // enable_fps_display=1
    const val ENABLE_FPS_DISPLAY = false

    // enable_status_display=1
    const val ENABLE_STATUS_DISPLAY = false

    // enable_info_display=1
    const val ENABLE_INFO_DISPLAY = false

    // smoothing_factor=0.25
    const val SMOOTHING_FACTOR = 0.6f

    // Fruit Game Config
    const val FRUIT_SPAWN_INTERVAL_MS_MIN = 250L // 4 fruits per second
    const val FRUIT_SPAWN_INTERVAL_MS_MAX = 1000L // 1 fruit per second
    const val FRUIT_LIFETIME_MS = 3000L
    const val FRUIT_SPEED_MIN = 200f // pixels per second (scaled)
    const val FRUIT_SPEED_MAX = 600f
    const val WEAPON_SIZE_MULTIPLIER = 0.5f
    const val FRUIT_BASE_SIZE = 40f
    const val BOMB_RATE = 0.02f
    
    val HEAD_MASKS = listOf("😎", "🤡", "👻", "🤖", "🐱", "🐶", "🦊", "🦁")
}
