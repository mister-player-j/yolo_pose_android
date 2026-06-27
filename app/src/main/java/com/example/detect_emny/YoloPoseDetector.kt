package com.example.detect_emeny

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.detect_emeny.config.AppConfig
import com.example.detect_emeny.model.*

class YoloPoseDetector(context: Context) {
    var lastFrameSize = FrameSize(1, 1)
    var lastFps = 0
    var lastMaxScore = 0f
    private var frameCount = 0
    private var lastFpsTs = System.currentTimeMillis()
    private var prevPoses: List<PoseDetection> = emptyList()

    init {
        init(context.assets)
    }

    private external fun init(assetManager: AssetManager): Boolean
    private external fun detect(bitmap: Bitmap): Array<PoseDetection>

    fun detect(proxy: ImageProxy, isFront: Boolean): List<PoseDetection> {
        val bitmap = proxy.toBitmap()
        val rot = proxy.imageInfo.rotationDegrees
        lastFrameSize = if (rot % 180 == 0) FrameSize(bitmap.width, bitmap.height) else FrameSize(bitmap.height, bitmap.width)

        val matrix = Matrix().apply { 
            postRotate(rot.toFloat()) 
        }
        val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        
        val detections = try {
            val res = detect(oriented)
            if (res == null) {
                Log.e("YoloPose", "Native detect returned null")
                emptyList<PoseDetection>()
            } else {
                res.toList()
            }
        } catch (e: Exception) {
            Log.e("YoloPose", "Native detect failed", e)
            emptyList<PoseDetection>()
        }
        
        val smoothed = smoothPoses(detections)
        prevPoses = smoothed
        
        lastMaxScore = smoothed.maxOfOrNull { it.score } ?: 0f
        
        bitmap.recycle()
        oriented.recycle()
        
        frameCount++
        System.currentTimeMillis().let { 
            if (it - lastFpsTs >= 1000) { 
                lastFps = frameCount
                frameCount = 0
                lastFpsTs = it 
            } 
        }
        return smoothed
    }

    private fun smoothPoses(newPoses: List<PoseDetection>): List<PoseDetection> {
        if (prevPoses.isEmpty()) return newPoses

        val alpha = AppConfig.SMOOTHING_FACTOR
        val matchedPrev = mutableSetOf<Int>()
        
        return newPoses.map { newPose ->
            var bestMatchIdx = -1
            var minDir = Float.MAX_VALUE
            
            for (i in prevPoses.indices) {
                if (i in matchedPrev) continue
                val prevPose = prevPoses[i]
                val dx = (newPose.box.l + newPose.box.r) / 2 - (prevPose.box.l + prevPose.box.r) / 2
                val dy = (newPose.box.t + newPose.box.b) / 2 - (prevPose.box.t + prevPose.box.b) / 2
                val dist = dx * dx + dy * dy
                if (dist < minDir) {
                    minDir = dist
                    bestMatchIdx = i
                }
            }

            if (bestMatchIdx != -1 && minDir < (lastFrameSize.width * 0.5f).let { it * it }) {
                matchedPrev.add(bestMatchIdx)
                val prevPose = prevPoses[bestMatchIdx]
                
                val smoothedBox = PoseBox(
                    l = prevPose.box.l + (newPose.box.l - prevPose.box.l) * alpha,
                    t = prevPose.box.t + (newPose.box.t - prevPose.box.t) * alpha,
                    r = prevPose.box.r + (newPose.box.r - prevPose.box.r) * alpha,
                    b = prevPose.box.b + (newPose.box.b - prevPose.box.b) * alpha
                )
                
                val smoothedKeypoints = newPose.keypoints.mapIndexed { ki, newKp ->
                    val prevKp = prevPose.keypoints[ki]
                    PoseKeypoint(
                        x = prevKp.x + (newKp.x - prevKp.x) * alpha,
                        y = prevKp.y + (newKp.y - prevKp.y) * alpha,
                        score = newPose.keypoints[ki].score
                    )
                }
                
                PoseDetection(newPose.score, smoothedBox, smoothedKeypoints)
            } else {
                newPose
            }
        }
    }

    fun close() {}

    companion object {
        init {
            System.loadLibrary("yolo_ncnn")
        }
    }
}
