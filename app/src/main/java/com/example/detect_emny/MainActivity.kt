package com.example.detect_emny

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.util.Log
import android.util.Range
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.detect_emny.ui.theme.Detect_emnyTheme
import java.util.concurrent.Executors

private const val TAG = "YoloPose"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            Detect_emnyTheme { PoseCameraScreen() }
        }
    }
}

@Composable
private fun PoseCameraScreen() {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }

    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.CAMERA) }

    @SuppressLint("UnsafeOptInUsageError")
    if (hasPermission) CameraPoseView() else Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
        Text("Camera permission is required.", color = Color.White)
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun CameraPoseView() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val haptic = LocalHapticFeedback.current
    
    val detector = remember { try { YoloPoseDetector(context) } catch (e: Exception) { null } }
    
    var poses by remember { mutableStateOf<List<PoseDetection>>(emptyList()) }
    var frameSize by remember { mutableStateOf(FrameSize(1, 1)) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var maxScore by remember { mutableStateOf(0f) }
    var fps by remember { mutableStateOf(0) }

    LaunchedEffect(poses.isNotEmpty()) { if (poses.isNotEmpty()) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(2f)) {
            key(lensFacing) {
                AndroidView(
                    factory = { PreviewView(it).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        val providerFuture = ProcessCameraProvider.getInstance(view.context)
                        providerFuture.addListener({
                            val provider = providerFuture.get()
                            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                            
                            // Check for 60 FPS support
                            val cameraInfo = provider.getAvailableCameraInfos().firstOrNull { 
                                selector.filter(listOf(it)).isNotEmpty() 
                            }
                            
                            val bestFpsRange = cameraInfo?.let { info ->
                                val characteristics = Camera2CameraInfo.from(info).getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                                characteristics?.find { it.upper >= 60 }
                            }

                            val previewBuilder = Preview.Builder()
                            val analysisBuilder = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)

                            if (bestFpsRange != null) {
                                Log.d(TAG, "Enabling High Frame Mode: $bestFpsRange")
                                Camera2Interop.Extender(previewBuilder).setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, bestFpsRange)
                                Camera2Interop.Extender(analysisBuilder).setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, bestFpsRange)
                            }

                            val preview = previewBuilder.build().apply { surfaceProvider = view.surfaceProvider }
                            val analysis = analysisBuilder.build().also {
                                it.setAnalyzer(cameraExecutor) { proxy ->
                                    detector?.let { det ->
                                        val result = det.detect(proxy, lensFacing == CameraSelector.LENS_FACING_FRONT)
                                        mainExecutor.execute {
                                            frameSize = det.lastFrameSize
                                            poses = result
                                            maxScore = det.lastMaxScore
                                            fps = det.lastFps
                                        }
                                    }
                                    proxy.close()
                                }
                            }
                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                            } catch (e: Exception) {
                                Log.e(TAG, "Bind failed", e)
                            }
                        }, mainExecutor)
                    }
                )
            }

            PoseOverlay(poses, frameSize, lensFacing == CameraSelector.LENS_FACING_FRONT, Modifier.fillMaxSize())

            Surface(
                modifier = Modifier.padding(16.dp).align(Alignment.TopStart),
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("FPS: $fps", color = Color.Green, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }

            if (poses.isNotEmpty()) Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
                color = Color(0xFF00E676).copy(alpha = 0.9f),
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 8.dp
            ) {
                Text("POSE DETECTED", color = Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp) )
            }

            Button(
                onClick = { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                Text(if (lensFacing == CameraSelector.LENS_FACING_FRONT) "Back" else "Front")
            }
        }

        Surface(Modifier.fillMaxWidth().weight(1f), color = MaterialTheme.colorScheme.surfaceVariant) {
            LazyColumn(Modifier.padding(16.dp)) {
                item {
                    Text("Pose Detection (NCNN YOLO11n)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Max Confidence: ${"%.4f".format(maxScore)}", style = MaterialTheme.typography.labelSmall)
                }
                itemsIndexed(poses) { i, pose ->
                    Column(Modifier.padding(top = 8.dp)) {
                        Text("Person ${i + 1} (Score: ${"%.2f".format(pose.score)})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        pose.keypoints.forEachIndexed { ki, kp ->
                            if (kp.score >= KEYPOINT_THRESHOLD) Row(Modifier.fillMaxWidth().padding(start = 8.dp), Arrangement.SpaceBetween) {
                                Text("${PoseSkeleton.keypointNames[ki]}:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(100.dp))
                                Text("x: ${kp.x.toInt()}, y: ${kp.y.toInt()} (${"%.2f".format(kp.score)})", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PoseOverlay(poses: List<PoseDetection>, frameSize: FrameSize, isFront: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (frameSize.width <= 1 || frameSize.height <= 1) return@Canvas
        val scale = if (frameSize.width.toFloat()/frameSize.height > size.width/size.height) size.height/frameSize.height else size.width/frameSize.width
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
            val left = transformX(pose.box.l)
            val right = transformX(pose.box.r)
            val top = transformY(pose.box.t)
            val bottom = transformY(pose.box.b)
            
            drawRect(
                color = Color(0xFF00E676).copy(0.5f),
                topLeft = Offset(minOf(left, right), top),
                size = Size(kotlin.math.abs(right - left), bottom - top),
                style = Stroke(4f)
            )

            PoseSkeleton.limbs.forEach { limb ->
                val a = pose.keypoints.getOrNull(limb.start); val b = pose.keypoints.getOrNull(limb.end)
                if (a != null && b != null && a.score >= KEYPOINT_THRESHOLD && b.score >= KEYPOINT_THRESHOLD)
                    drawLine(limb.color.copy(0.5f), a.toOffset(), b.toOffset(), 12f, StrokeCap.Round)
            }
            pose.keypoints.forEach { if (it.score >= KEYPOINT_THRESHOLD) {
                drawCircle(Color.Yellow.copy(0.5f), 8f, it.toOffset())
                drawCircle(Color.Black.copy(0.5f), 10f, it.toOffset(), style = Stroke(2f))
            } }
        }
    }
}

private data class Limb(val start: Int, val end: Int, val color: Color)

private object PoseSkeleton {
    val limbs = listOf(
        Limb(5, 6, Color(0xFFFFEB3B)), Limb(11, 12, Color(0xFFFFEB3B)), Limb(5, 11, Color(0xFFFFEB3B)), Limb(6, 12, Color(0xFFFFEB3B)),
        Limb(5, 7, Color(0xFF00E5FF)), Limb(7, 9, Color(0xFF00E5FF)), Limb(6, 8, Color(0xFFFF4081)), Limb(8, 10, Color(0xFFFF4081)),
        Limb(11, 13, Color(0xFF00E676)), Limb(13, 15, Color(0xFF00E676)), Limb(12, 14, Color(0xFFFFAB40)), Limb(14, 16, Color(0xFFFFAB40)),
        Limb(0, 1, Color(0xFFE1BEE7)), Limb(0, 2, Color(0xFFE1BEE7))
    )
    val keypointNames = listOf("Nose", "L-Eye", "R-Eye", "L-Ear", "R-Ear", "L-Shoulder", "R-Shoulder", "L-Elbow", "R-Elbow", "L-Wrist", "R-Wrist", "L-Hip", "R-Hip", "L-Knee", "R-Knee", "L-Ankle", "R-Ankle")
}

private const val KEYPOINT_THRESHOLD = 0.40f
