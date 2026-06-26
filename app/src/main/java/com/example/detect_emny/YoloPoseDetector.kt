package com.example.detect_emny

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy

data class FrameSize(val width: Int, val height: Int)
data class PoseDetection(val score: Float, val box: PoseBox, val keypoints: List<PoseKeypoint>)
data class PoseBox(val l: Float, val t: Float, val r: Float, val b: Float)
data class PoseKeypoint(val x: Float, val y: Float, val score: Float)

class YoloPoseDetector(context: Context) {
    var lastFrameSize = FrameSize(1, 1)
    var lastFps = 0
    var lastMaxScore = 0f
    private var frameCount = 0
    private var lastFpsTs = System.currentTimeMillis()

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
        
        lastMaxScore = detections.maxOfOrNull { it.score } ?: 0f
        
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
        return detections
    }

    fun close() {}

    companion object {
        init {
            System.loadLibrary("yolo_ncnn")
        }
    }
}
