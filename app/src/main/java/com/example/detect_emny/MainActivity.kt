package com.example.detect_emeny

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import com.example.detect_emeny.config.AppConfig
import com.example.detect_emeny.model.*
import com.example.detect_emeny.ui.components.*
import com.example.detect_emeny.ui.theme.Detect_emenyTheme
import com.example.detect_emeny.util.SoundManager
import java.util.concurrent.Executors

private const val TAG = "YoloPose"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            Detect_emenyTheme { PoseCameraScreen() }
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
    
    // USB Management
    val usbManager = remember { context.getSystemService(Context.USB_SERVICE) as UsbManager }
    var usbDevice by remember { mutableStateOf<UsbDevice?>(null) }
    var usbConnection by remember { mutableStateOf<UsbDeviceConnection?>(null) }
    var usbEndpointOut by remember { mutableStateOf<UsbEndpoint?>(null) }

    val usbHelper = remember {
        UsbManagerHelper(context) { device, connection, endpoint ->
            mainExecutor.execute {
                usbConnection?.close()
                usbDevice = device
                usbConnection = connection
                usbEndpointOut = endpoint
            }
        }
    }

    val detector = remember { try { YoloPoseDetector(context) } catch (e: Exception) { null } }
    val soundManager = remember { SoundManager(context) }

    DisposableEffect(context) {
        usbHelper.register()
        onDispose { 
            usbHelper.unregister()
            usbHelper.shutdown()
            cameraExecutor.shutdown() 
            soundManager.release()
        }
    }
    
    var poses by remember { mutableStateOf<List<PoseDetection>>(emptyList()) }
    val displayMetrics = context.resources.displayMetrics
    var frameSize by remember { mutableStateOf(FrameSize(displayMetrics.widthPixels, displayMetrics.heightPixels)) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var maxScore by remember { mutableStateOf(0f) }
    var fps by remember { mutableStateOf(0) }
    var leftWeapon by remember { mutableStateOf("") }
    var rightWeapon by remember { mutableStateOf("sward2.png") }
    var showPoseFrame by remember { mutableStateOf(false) }
    var showGameConfig by remember { mutableStateOf(false) }
    var showHeadMask by remember { mutableStateOf(false) }
    var currentHeadMask by remember { mutableStateOf("😎") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    val fruitManager = remember { FruitManager() }
    val scoreManager = remember { ScoreManager(context) }
    var showLeaderboard by remember { mutableStateOf(false) }
    var showSaveSummary by remember { mutableStateOf(false) }
    var lastSavedScore by remember { mutableStateOf(0) }
    var lastSavedBombs by remember { mutableStateOf(0) }
    var gameTick by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { time ->
                if (frameSize.width > 1 && frameSize.height > 1) {
                    fruitManager.update(frameSize.width.toFloat(), frameSize.height.toFloat())
                    gameTick = time // Force recomposition every frame
                }
            }
        }
    }

    LaunchedEffect(poses.isNotEmpty()) { 
        if (poses.isNotEmpty()) haptic.performHapticFeedback(HapticFeedbackType.LongPress) 
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = if (AppConfig.ENABLE_INFO_DISPLAY) {
                Modifier.fillMaxWidth().weight(2f)
            } else {
                Modifier.fillMaxWidth().weight(1f)
            }
        ) {
            key(lensFacing) {
                AndroidView(
                    factory = { PreviewView(it).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        val providerFuture = ProcessCameraProvider.getInstance(view.context)
                        providerFuture.addListener({
                            val provider = providerFuture.get()
                            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                            
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

            PoseOverlay(
                poses = poses,
                frameSize = frameSize,
                isFront = lensFacing == CameraSelector.LENS_FACING_FRONT,
                leftWeapon = leftWeapon,
                rightWeapon = rightWeapon,
                fruitManager = fruitManager,
                showPoseFrame = showPoseFrame,
                showHeadMask = showHeadMask,
                headMaskEmoji = currentHeadMask,
                onHit = { isBomb ->
                    if (isBomb) soundManager.playBomb() else soundManager.playSlice()
                },
                gameTick = gameTick
            )

            // Score Display
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .clickable { showLeaderboard = true },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "SCORE: ${fruitManager.totalScore}",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.shadow(8.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "BOMBS: ${fruitManager.bombHitCount}",
                        color = Color.Red.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp).shadow(4.dp)
                    )
                }
                Button(
                    onClick = {
                        lastSavedScore = fruitManager.totalScore
                        lastSavedBombs = fruitManager.bombHitCount
                        scoreManager.addScore(lastSavedScore, lastSavedBombs)
                        showSaveSummary = true
                        fruitManager.totalScore = 0
                        fruitManager.bombHitCount = 0
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.5f)),
                    modifier = Modifier.padding(top = 4.dp).height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Save & Reset", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                }
            }

            if (showLeaderboard) {
                AlertDialog(
                    onDismissRequest = { showLeaderboard = false },
                    title = { Text("Top 10 Scores") },
                    text = {
                        val topScores = remember { scoreManager.getTopScores() }
                        LazyColumn {
                            itemsIndexed(topScores) { index, record ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("${index + 1}. Score: ${record.score}", fontWeight = FontWeight.Bold)
                                        Text("Bombs Hit: ${record.bombCount}", color = Color.Red.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                                    }
                                    val date = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
                                        .format(java.util.Date(record.timestamp))
                                    Text(date, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (topScores.isEmpty()) {
                                item { Text("No scores recorded yet.") }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showLeaderboard = false }) { Text("Close") }
                    }
                )
            }

            if (showSaveSummary) {
                AlertDialog(
                    onDismissRequest = { showSaveSummary = false },
                    title = { Text("Game Over / Saved") },
                    text = {
                        Column {
                            Text("Your final score: $lastSavedScore", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(8.dp))
                            Text("Bombs hit: $lastSavedBombs", color = Color.Red, style = MaterialTheme.typography.bodyLarge)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSaveSummary = false }) { Text("OK") }
                    }
                )
            }

            // Right side selector
            WeaponSelector(
                label = "Right",
                currentWeapon = rightWeapon,
                onWeaponChange = { rightWeapon = it },
                modifier = Modifier.align(Alignment.CenterEnd).padding(top = 160.dp)
            )

            // Left side selector
            WeaponSelector(
                label = "Left",
                currentWeapon = leftWeapon,
                onWeaponChange = { leftWeapon = it },
                modifier = Modifier.align(Alignment.CenterStart).padding(top = 160.dp)
            )

            FpsDisplay(fps, Modifier.align(Alignment.TopStart))
            
            PositionDisplay(poses, Modifier.align(Alignment.TopEnd).padding(top = 40.dp))

            StatusIndicator(poses, Modifier.align(Alignment.TopCenter))

            Button(
                onClick = { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                Text(if (lensFacing == CameraSelector.LENS_FACING_FRONT) "Back" else "Front")
            }

            Button(
                onClick = { showPoseFrame = !showPoseFrame },
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
            ) {
                Text(if (showPoseFrame) "Hide Pose" else "Show Pose")
            }

            Button(
                onClick = { showGameConfig = true },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            ) {
                Text("Game Config")
            }

            // Head Mask Toggle & Picker
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row {
                    Button(
                        onClick = { showHeadMask = !showHeadMask },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showHeadMask) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.6f)
                        )
                    ) {
                        Text(if (showHeadMask) "Mask On" else "Mask Off")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { showEmojiPicker = true },
                        enabled = showHeadMask
                    ) {
                        Text(currentHeadMask)
                    }
                }
            }
        }

        if (showEmojiPicker) {
            AlertDialog(
                onDismissRequest = { showEmojiPicker = false },
                title = { Text("Choose Head Mask") },
                text = {
                    Column {
                        val emojis = AppConfig.HEAD_MASKS
                        val rows = emojis.chunked(4)
                        rows.forEach { rowEmojis ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                rowEmojis.forEach { emoji ->
                                    TextButton(onClick = {
                                        currentHeadMask = emoji
                                        showEmojiPicker = false
                                    }) {
                                        Text(emoji, fontSize = 32.sp)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showEmojiPicker = false }) { Text("Close") }
                }
            )
        }

        if (showGameConfig) {
            AlertDialog(
                onDismissRequest = { showGameConfig = false },
                title = { Text("Game Settings") },
                text = {
                    Column(Modifier.fillMaxWidth()) {
                        Text("Fruits per second: ${"%.1f".format(1000f / fruitManager.spawnIntervalMin)}")
                        Slider(
                            value = 1000f / fruitManager.spawnIntervalMin,
                            onValueChange = { 
                                val interval = (1000f / it).toLong()
                                fruitManager.spawnIntervalMin = interval
                                fruitManager.spawnIntervalMax = interval + 200L
                            },
                            valueRange = 0.5f..10f
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Min Speed: ${fruitManager.speedMin.toInt()}")
                        Slider(
                            value = fruitManager.speedMin,
                            onValueChange = { fruitManager.speedMin = it },
                            valueRange = 50f..500f
                        )
                        Text("Max Speed: ${fruitManager.speedMax.toInt()}")
                        Slider(
                            value = fruitManager.speedMax,
                            onValueChange = { fruitManager.speedMax = it },
                            valueRange = 50f..1000f
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Bomb Rate: ${"%.1f".format(fruitManager.bombRate * 100)}%")
                        Slider(
                            value = fruitManager.bombRate,
                            onValueChange = { fruitManager.bombRate = it },
                            valueRange = 0f..0.2f
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showGameConfig = false }) { Text("Done") }
                }
            )
        }

        if (AppConfig.ENABLE_INFO_DISPLAY) {
            Surface(Modifier.fillMaxWidth().weight(1f), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column {
                    LazyColumn(Modifier.weight(1f).padding(16.dp)) {
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

                    UsbControlView(
                        usbManager = usbManager,
                        usbDevice = usbDevice,
                        usbConnection = usbConnection,
                        onConnectRequest = { usbHelper.requestPermission(it) },
                        onSendMessage = { usbHelper.sendMsg(usbConnection, usbEndpointOut, it) }
                    )
                }
            }
        }
    }
}
