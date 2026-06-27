package com.example.detect_emeny.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.example.detect_emeny.config.AppConfig
import com.example.detect_emeny.model.*

data class WeaponTrailPoint(val tip: Offset, val base: Offset, val timestamp: Long = System.currentTimeMillis())

@Composable
fun PoseOverlay(
    poses: List<PoseDetection>,
    frameSize: FrameSize,
    isFront: Boolean,
    leftWeapon: String,
    rightWeapon: String,
    fruitManager: FruitManager? = null,
    showPoseFrame: Boolean = true,
    showHeadMask: Boolean = false,
    headMaskEmoji: String = "😎",
    onHit: (Boolean) -> Unit = {},
    gameTick: Long = 0L,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val leftWeaponBitmap = remember(leftWeapon) {
        if (!AppConfig.ENABLE_WEAPON || leftWeapon.isEmpty()) return@remember null
        try {
            context.assets.open(leftWeapon).use {
                BitmapFactory.decodeStream(it).asImageBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }

    val rightWeaponBitmap = remember(rightWeapon) {
        if (!AppConfig.ENABLE_WEAPON || rightWeapon.isEmpty()) return@remember null
        try {
            context.assets.open(rightWeapon).use {
                BitmapFactory.decodeStream(it).asImageBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }

    val leftTrailMap = remember { mutableMapOf<Int, MutableList<WeaponTrailPoint>>() }
    val rightTrailMap = remember { mutableMapOf<Int, MutableList<WeaponTrailPoint>>() }

    val transparency = AppConfig.POSE_TRANSPARENCY

    Canvas(modifier.fillMaxSize()) {
        if (frameSize.width <= 1 || frameSize.height <= 1) return@Canvas
        val scale = if (frameSize.width.toFloat() / frameSize.height > size.width / size.height)
            size.height / frameSize.height
        else
            size.width / frameSize.width
        val dx = (size.width - frameSize.width * scale) / 2f
        val dy = (size.height - frameSize.height * scale) / 2f

        fun transformX(x: Float): Float {
            val scaledX = x * scale
            return if (isFront) {
                size.width - (scaledX + dx)
            } else {
                scaledX + dx
            }
        }

        fun transformY(y: Float): Float = y * scale + dy

        fun PoseKeypoint.toOffset() = Offset(transformX(x), transformY(y))

        poses.forEachIndexed { index, pose ->
            if (showPoseFrame) {
                val left = transformX(pose.box.l)
                val right = transformX(pose.box.r)
                val top = transformY(pose.box.t)
                val bottom = transformY(pose.box.b)

                // Bounding box
                drawRect(
                    color = Color(0xFF00E676).copy(transparency),
                    topLeft = Offset(minOf(left, right), top),
                    size = Size(kotlin.math.abs(right - left), bottom - top),
                    style = Stroke(4f)
                )

                // Limbs
                PoseSkeleton.limbs.forEach { limb ->
                    val a = pose.keypoints.getOrNull(limb.start)
                    val b = pose.keypoints.getOrNull(limb.end)
                    if (a != null && b != null && a.score >= KEYPOINT_THRESHOLD && b.score >= KEYPOINT_THRESHOLD)
                        drawLine(limb.color.copy(transparency), a.toOffset(), b.toOffset(), 12f, StrokeCap.Round)
                }

                // Keypoints
                pose.keypoints.forEach {
                    if (it.score >= KEYPOINT_THRESHOLD) {
                        drawCircle(Color.Yellow.copy(transparency), 8f, it.toOffset())
                        drawCircle(Color.Black.copy(transparency), 10f, it.toOffset(), style = Stroke(2f))
                    }
                }
            }

            // Head Mask
            if (showHeadMask) {
                val nose = pose.keypoints.getOrNull(0)
                if (nose != null && nose.score >= KEYPOINT_THRESHOLD) {
                    val noseOffset = nose.toOffset()
                    val boxHeight = (pose.box.b - pose.box.t) * scale
                    val headSize = boxHeight * 0.25f // Estimate head size as 1/4 of box height
                    
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            textSize = headSize * 1.5f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        // Draw emoji centered on nose
                        drawText(headMaskEmoji, noseOffset.x, noseOffset.y + (headSize / 2), paint)
                    }
                }
            }

            // Weapons
            if (AppConfig.ENABLE_WEAPON) {
                val leftTrail = leftTrailMap.getOrPut(index) { mutableListOf() }
                val rightTrail = rightTrailMap.getOrPut(index) { mutableListOf() }
                
                val now = System.currentTimeMillis()
                leftTrail.removeAll { now - it.timestamp > 150 }
                rightTrail.removeAll { now - it.timestamp > 150 }

                drawWeaponTrail(leftTrail, Color(0xFF00BFFF)) // Deep Sky Blue
                drawWeaponTrail(rightTrail, Color(0xFFFF00FF)) // Magenta

                // Right Weapon (Wrist 10, Elbow 8)
                drawWeapon(
                    pose, 10, 8, rightWeaponBitmap, scale, isFront, size.width, dx, dy, fruitManager, rightTrail, onHit
                )
                // Left Weapon (Wrist 9, Elbow 7)
                drawWeapon(
                    pose, 9, 7, leftWeaponBitmap, scale, isFront, size.width, dx, dy, fruitManager, leftTrail, onHit
                )
            }
        }

        // Draw Fruits
        fruitManager?.fruits?.forEach { fruit ->
            val screenPos = Offset(transformX(fruit.position.x), transformY(fruit.position.y))
            val fruitSize = AppConfig.FRUIT_BASE_SIZE * scale
            
            if (fruit.isBoomed == 0L) {
                val emoji = if (fruit.isBomb) {
                    "💣"
                } else {
                    when (fruit.type) {
                        0 -> "🍎"
                        1 -> "🍌"
                        2 -> "🥭"
                        3 -> "🍉"
                        4 -> "🍓"
                        5 -> "🍍"
                        else -> "🍎"
                    }
                }
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        textSize = fruitSize
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    // Adjust Y for text centering (approximate)
                    drawText(emoji, screenPos.x, screenPos.y + fruitSize / 3, paint)
                }
            } else {
                if (fruit.isBomb) {
                    // Dark effect: Voids and smoke for bombs
                    val boomDuration = 800f
                    val elapsed = (System.currentTimeMillis() - fruit.isBoomed).toFloat()
                    val boomTime = (elapsed / boomDuration).coerceIn(0f, 1f)
                    
                    val random = kotlin.random.Random(fruit.id)
                    val particleCount = 60
                    val colors = listOf(
                        Color(0xFF000000), // Black
                        Color(0xFF2E0854), // Dark Purple
                        Color(0xFF333333), // Dark Grey
                        Color(0xFF4B0082)  // Indigo
                    )

                    val maxExpansion = fruitSize * (2.0f + random.nextFloat() * 1.0f)
                    
                    for (i in 0 until particleCount) {
                        val angle = random.nextFloat() * 2f * kotlin.math.PI.toFloat()
                        val speedFactor = random.nextFloat() * 0.9f + 0.1f
                        val easedTime = 1f - (1f - boomTime) * (1f - boomTime) * (1f - boomTime)
                        val distance = maxExpansion * speedFactor * easedTime
                        
                        val px = screenPos.x + kotlin.math.cos(angle) * distance
                        val py = screenPos.y + kotlin.math.sin(angle) * distance
                        
                        // Dark smoke rises
                        val rise = boomTime * -80f * scale
                        
                        val pSize = (random.nextFloat() * 10f + 2f) * (1f - boomTime) * scale
                        val pColor = colors[random.nextInt(colors.size)].copy(alpha = 1f - boomTime)
                        
                        drawCircle(
                            color = pColor,
                            radius = pSize,
                            center = Offset(px, py + rise)
                        )
                        
                        // Occasional dark spark
                        if (random.nextFloat() > 0.9f) {
                            drawCircle(
                                color = Color.White.copy(alpha = (1f - boomTime) * 0.3f),
                                radius = pSize * 0.5f,
                                center = Offset(px, py + rise)
                            )
                        }
                    }
                } else {
                    // Boom effect: Spark of Disney sand
                    val boomDuration = 500f
                    val elapsed = (System.currentTimeMillis() - fruit.isBoomed).toFloat()
                    val boomTime = (elapsed / boomDuration).coerceIn(0f, 1f)
                    
                    val random = kotlin.random.Random(fruit.id)
                    val particleCount = 40
                    val colors = listOf(
                        Color(0xFFFFD700), // Gold
                        Color(0xFFFFFACD), // LemonChiffon
                        Color(0xFFFFFFFF), // White
                        Color(0xFFFFE4B5), // Moccasin
                        Color(0xFFFFE119)  // Bright Yellow
                    )

                    // Total size is 1.5~2 times the fruit
                    val maxExpansion = fruitSize * (1.5f + random.nextFloat() * 0.5f)
                    
                    for (i in 0 until particleCount) {
                        val angle = random.nextFloat() * 2f * kotlin.math.PI.toFloat()
                        val speedFactor = random.nextFloat() * 0.8f + 0.2f
                        // Easing out for a more natural "burst"
                        val easedTime = 1f - (1f - boomTime) * (1f - boomTime)
                        val distance = maxExpansion * speedFactor * easedTime
                        
                        // Add some slight wavy motion to the particles
                        val wave = kotlin.math.sin(boomTime * 10f + i) * 3f * scale
                        
                        val px = screenPos.x + kotlin.math.cos(angle) * distance + wave
                        val py = screenPos.y + kotlin.math.sin(angle) * distance + wave
                        
                        // Gravity drift
                        val gravity = boomTime * 40f * scale
                        
                        val pSize = (random.nextFloat() * 4f + 1f) * (1f - boomTime) * scale
                        val pColor = colors[random.nextInt(colors.size)].copy(alpha = 1f - boomTime)
                        
                        drawCircle(
                            color = pColor,
                            radius = pSize,
                            center = Offset(px, py + gravity)
                        )
                        
                        // Occasional extra bright twinkle
                        if (random.nextFloat() > 0.8f) {
                            drawCircle(
                                color = Color.White.copy(alpha = (1f - boomTime) * 0.8f),
                                radius = pSize * 1.5f,
                                center = Offset(px, py + gravity)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWeaponTrail(
    trail: List<WeaponTrailPoint>,
    color: Color
) {
    if (trail.size < 2) return
    
    val path = Path()
    path.moveTo(trail[0].tip.x, trail[0].tip.y)
    for (i in 1 until trail.size) {
        path.lineTo(trail[i].tip.x, trail[i].tip.y)
    }
    for (i in trail.size - 1 downTo 0) {
        path.lineTo(trail[i].base.x, trail[i].base.y)
    }
    path.close()
    
    drawPath(
        path = path,
        color = color.copy(alpha = 0.25f),
        style = Fill
    )
    
    // Glowing edge for the tip
    for (i in 0 until trail.size - 1) {
        val alpha = (1f - i.toFloat() / trail.size) * 0.4f
        drawLine(
            color = Color.White.copy(alpha = alpha),
            start = trail[i].tip,
            end = trail[i+1].tip,
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWeapon(
    pose: PoseDetection,
    wristIdx: Int,
    elbowIdx: Int,
    weaponBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    scale: Float,
    isFront: Boolean,
    canvasWidth: Float,
    dx: Float,
    dy: Float,
    fruitManager: FruitManager?,
    trail: MutableList<WeaponTrailPoint>,
    onHit: (Boolean) -> Unit
) {
    if (weaponBitmap == null) return
    
    val wrist = pose.keypoints.getOrNull(wristIdx)
    val elbow = pose.keypoints.getOrNull(elbowIdx)

    if (wrist != null && wrist.score >= KEYPOINT_THRESHOLD &&
        elbow != null && elbow.score >= KEYPOINT_THRESHOLD
    ) {
        fun transformX(x: Float): Float {
            val scaledX = x * scale
            return if (isFront) canvasWidth - (scaledX + dx) else scaledX + dx
        }
        fun transformY(y: Float): Float = y * scale + dy

        val wristOffset = Offset(transformX(wrist.x), transformY(wrist.y))
        val elbowOffset = Offset(transformX(elbow.x), transformY(elbow.y))
        
        val boxHeight = (pose.box.b - pose.box.t) * scale
        val baseWeaponHeight = boxHeight * 0.55f * AppConfig.WEAPON_SIZE_MULTIPLIER
        val time = System.currentTimeMillis() / 200.0
        val waveFactor = 1.0f + 0.1f * kotlin.math.sin(time).toFloat()
        val weaponHeight = baseWeaponHeight * waveFactor
        val weaponWidth = weaponBitmap.width * (weaponHeight / weaponBitmap.height)

        val diffX = wristOffset.x - elbowOffset.x
        val diffY = wristOffset.y - elbowOffset.y
        val angleRad = kotlin.math.atan2(diffY, diffX)
        val angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()

        val tipX = wristOffset.x + kotlin.math.cos(angleRad) * weaponHeight
        val tipY = wristOffset.y + kotlin.math.sin(angleRad) * weaponHeight
        val tipOffset = Offset(tipX, tipY)
        
        // Update trail
        trail.add(0, WeaponTrailPoint(tipOffset, wristOffset))
        if (trail.size > 15) trail.removeAt(trail.size - 1)

        withTransform({
            rotate(degrees = angleDeg + 90f, pivot = wristOffset)
        }) {
            val gripOffset = weaponHeight * 0.05f
            drawImage(
                image = weaponBitmap,
                dstOffset = IntOffset(
                    (wristOffset.x - weaponWidth / 2).toInt(),
                    (wristOffset.y - weaponHeight + gripOffset).toInt()
                ),
                dstSize = IntSize(weaponWidth.toInt(), weaponHeight.toInt())
            )
        }

        fruitManager?.let { fm ->
            // Use top half of the weapon hit fruit
            // Blade segment is from wristOffset to tipOffset
            // Top half starts at 0.5 * weaponHeight from wrist
            val bladePoints = mutableListOf<Offset>()
            val samples = 5
            for (i in 0 until samples) {
                // t from 0.5 (middle) to 1.0 (tip)
                val t = 0.5f + (i.toFloat() / (samples - 1)) * 0.5f
                val px = wristOffset.x + kotlin.math.cos(angleRad) * (weaponHeight * t)
                val py = wristOffset.y + kotlin.math.sin(angleRad) * (weaponHeight * t)
                
                bladePoints.add(Offset(
                    if (isFront) (canvasWidth - px - dx) / scale else (px - dx) / scale,
                    (py - dy) / scale
                ))
            }
            
            fm.checkCollisions(bladePoints, (weaponWidth / scale) * 0.8f, onHit)
        }
    }
}
