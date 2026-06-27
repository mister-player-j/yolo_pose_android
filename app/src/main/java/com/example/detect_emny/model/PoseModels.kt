package com.example.detect_emeny.model

import androidx.compose.ui.graphics.Color

data class FrameSize(val width: Int, val height: Int)
data class PoseDetection(val score: Float, val box: PoseBox, val keypoints: List<PoseKeypoint>)
data class PoseBox(val l: Float, val t: Float, val r: Float, val b: Float)
data class PoseKeypoint(val x: Float, val y: Float, val score: Float)

data class Limb(val start: Int, val end: Int, val color: Color)

object PoseSkeleton {
    val limbs = listOf(
        Limb(5, 6, Color(0xFFFFEB3B)), Limb(11, 12, Color(0xFFFFEB3B)), 
        Limb(5, 11, Color(0xFFFFEB3B)), Limb(6, 12, Color(0xFFFFEB3B)),
        Limb(5, 7, Color(0xFF00E5FF)), Limb(7, 9, Color(0xFF00E5FF)), 
        Limb(6, 8, Color(0xFFFF4081)), Limb(8, 10, Color(0xFFFF4081)),
        Limb(11, 13, Color(0xFF00E676)), Limb(13, 15, Color(0xFF00E676)), 
        Limb(12, 14, Color(0xFFFFAB40)), Limb(14, 16, Color(0xFFFFAB40)),
        Limb(0, 1, Color(0xFFE1BEE7)), Limb(0, 2, Color(0xFFE1BEE7))
    )
    val keypointNames = listOf(
        "Nose", "L-Eye", "R-Eye", "L-Ear", "R-Ear", 
        "L-Shoulder", "R-Shoulder", "L-Elbow", "R-Elbow", 
        "L-Wrist", "R-Wrist", "L-Hip", "R-Hip", 
        "L-Knee", "R-Knee", "L-Ankle", "R-Ankle"
    )
}

const val KEYPOINT_THRESHOLD = 0.40f
