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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
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

@Composable
fun PoseOverlay(
    poses: List<PoseDetection>,
    frameSize: FrameSize,
    isFront: Boolean,
    leftWeapon: String,
    rightWeapon: String,
    fruitManager: FruitManager? = null,
    showPoseFrame: Boolean = true,
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

        poses.forEach { pose ->
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

            // Weapons
            if (AppConfig.ENABLE_WEAPON) {
                // Right Weapon (Wrist 10, Elbow 8)
                drawWeapon(
                    pose, 10, 8, rightWeaponBitmap, scale, isFront, size.width, dx, dy, fruitManager
                )
                // Left Weapon (Wrist 9, Elbow 7)
                drawWeapon(
                    pose, 9, 7, leftWeaponBitmap, scale, isFront, size.width, dx, dy, fruitManager
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
                // Boom effect
                val boomTime = (System.currentTimeMillis() - fruit.isBoomed) / 500f
                val boomRadius = fruitSize * (1f + boomTime * 2f)
                drawCircle(
                    Color.Yellow.copy(alpha = 1f - boomTime),
                    boomRadius,
                    screenPos
                )
                drawCircle(
                    Color.Red.copy(alpha = 1f - boomTime),
                    boomRadius * 0.8f,
                    screenPos
                )
            }
        }
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
    fruitManager: FruitManager?
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

        val tipX = wristOffset.x + kotlin.math.cos(angleRad) * weaponHeight
        val tipY = wristOffset.y + kotlin.math.sin(angleRad) * weaponHeight
        
        fun untransformX(screenX: Float): Float {
            val scaledX = if (isFront) canvasWidth - screenX - dx else screenX - dx
            return scaledX / scale
        }
        fun untransformY(screenY: Float): Float = (screenY - dy) / scale

        fruitManager?.checkCollisions(
            Offset(untransformX(tipX), untransformY(tipY)),
            (weaponWidth / scale) * 1.5f
        )
    }
}
