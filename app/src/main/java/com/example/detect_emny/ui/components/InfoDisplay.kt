package com.example.detect_emeny.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.detect_emeny.config.AppConfig
import com.example.detect_emeny.model.PoseDetection

@Composable
fun FpsDisplay(fps: Int, modifier: Modifier = Modifier) {
    if (!AppConfig.ENABLE_FPS_DISPLAY) return
    
    Surface(
        modifier = modifier.padding(16.dp),
        color = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = "FPS: $fps",
            color = Color.Green,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun PositionDisplay(poses: List<PoseDetection>, modifier: Modifier = Modifier) {
    if (!AppConfig.ENABLE_POSITION_DISPLAY || poses.isEmpty()) return

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        poses.forEachIndexed { index, pose ->
            val centerX = (pose.box.l + pose.box.r) / 2
            val centerY = (pose.box.t + pose.box.b) / 2
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(
                    text = "P${index + 1}: (${centerX.toInt()}, ${centerY.toInt()})",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun StatusIndicator(poses: List<PoseDetection>, modifier: Modifier = Modifier) {
    if (!AppConfig.ENABLE_STATUS_DISPLAY || poses.isEmpty()) return
    
    Surface(
        modifier = modifier.padding(top = 48.dp),
        color = Color(0xFF00E676).copy(alpha = 0.9f),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp
    ) {
        Text(
            "POSE DETECTED",
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}
