package com.aican.biometricattendance.presentation.screens.camera.components

import android.graphics.Bitmap
import android.util.Log
import com.aican.biometricattendance.data.models.camera.FaceBox
import com.aican.biometricattendance.data.models.facelandmark.ResultBundle
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

object CoordinateDebugger {
    private const val TAG = "CoordinateDebug"

    fun logPreviewInfo(previewWidth: Float, previewHeight: Float) {
        Log.d(TAG, """
            📱 PREVIEW SURFACE INFO:
            - Preview dimensions: ${previewWidth}x${previewHeight}
            - Preview aspect ratio: ${previewWidth/previewHeight}
        """.trimIndent())
    }

    fun logImageInfo(resultBundle: ResultBundle) {
        Log.d(TAG, """
            📸 INPUT IMAGE INFO:
            - Input image dimensions: ${resultBundle.inputImageWidth}x${resultBundle.inputImageHeight}
            - Input aspect ratio: ${resultBundle.inputImageWidth.toFloat()/resultBundle.inputImageHeight}
        """.trimIndent())
    }

    fun logFaceDetection(landmarks: List<NormalizedLandmark>, resultBundle: ResultBundle) {
        val minX = landmarks.minOf { it.x() }
        val maxX = landmarks.maxOf { it.x() }
        val minY = landmarks.minOf { it.y() }
        val maxY = landmarks.maxOf { it.y() }

        Log.d(TAG, """
            🎯 FACE DETECTION (Normalized 0-1):
            - Face bounds: left=$minX, top=$minY, right=$maxX, bottom=$maxY
            - Face width: ${maxX - minX}
            - Face height: ${maxY - minY}
            - Face center: x=${(minX + maxX)/2}, y=${(minY + minY)/2}
        """.trimIndent())
    }

    fun logFaceBoxConversion(
        landmarks: List<NormalizedLandmark>,
        resultBundle: ResultBundle,
        previewWidth: Float,
        previewHeight: Float,
        finalFaceBox: FaceBox
    ) {
        val scaleX = previewWidth / resultBundle.inputImageWidth
        val scaleY = previewHeight / resultBundle.inputImageHeight

        val minX = landmarks.minOf { it.x() }
        val maxX = landmarks.maxOf { it.x() }
        val minY = landmarks.minOf { it.y() }
        val maxY = landmarks.maxOf { it.y() }

        // Calculate intermediate steps
        val faceInInputPixels = mapOf(
            "left" to minX * resultBundle.inputImageWidth,
            "top" to minY * resultBundle.inputImageHeight,
            "right" to maxX * resultBundle.inputImageWidth,
            "bottom" to maxY * resultBundle.inputImageHeight
        )

        val faceInPreviewPixels = mapOf(
            "left" to faceInInputPixels["left"]!! * scaleX,
            "top" to faceInInputPixels["top"]!! * scaleY,
            "right" to faceInInputPixels["right"]!! * scaleX,
            "bottom" to faceInInputPixels["bottom"]!! * scaleY
        )

        Log.d(TAG, """
            🔄 FACE BOX CONVERSION:
            - Scale factors: scaleX=$scaleX, scaleY=$scaleY
            
            Step 1 - Normalized to Input Image pixels:
            - left=${faceInInputPixels["left"]}, top=${faceInInputPixels["top"]}
            - right=${faceInInputPixels["right"]}, bottom=${faceInInputPixels["bottom"]}
            
            Step 2 - Input Image to Preview pixels:
            - left=${faceInPreviewPixels["left"]}, top=${faceInPreviewPixels["top"]}
            - right=${faceInPreviewPixels["right"]}, bottom=${faceInPreviewPixels["bottom"]}
            
            Step 3 - Final FaceBox (with padding):
            - left=${finalFaceBox.left}, top=${finalFaceBox.top}
            - right=${finalFaceBox.right}, bottom=${finalFaceBox.bottom}
            - width=${finalFaceBox.right - finalFaceBox.left}
            - height=${finalFaceBox.bottom - finalFaceBox.top}
        """.trimIndent())
    }

    fun logCapturedImageInfo(originalBitmap: Bitmap) {
        Log.d(TAG, """
            📸 CAPTURED IMAGE INFO:
            - Captured image dimensions: ${originalBitmap.width}x${originalBitmap.height}
            - Captured aspect ratio: ${originalBitmap.width.toFloat()/originalBitmap.height}
        """.trimIndent())
    }

    fun logCropCalculation(
        faceBox: FaceBox,
        previewWidth: Float,
        previewHeight: Float,
        originalBitmap: Bitmap,
        actualLeft: Int,
        actualTop: Int,
        actualRight: Int,
        actualBottom: Int
    ) {
        val scaleX = originalBitmap.width.toFloat() / previewWidth
        val scaleY = originalBitmap.height.toFloat() / previewHeight

        Log.d(TAG, """
            ✂️ CROP CALCULATION:
            - Original preview FaceBox: left=${faceBox.left}, top=${faceBox.top}, right=${faceBox.right}, bottom=${faceBox.bottom}
            - Preview dimensions: ${previewWidth}x${previewHeight}
            - Captured image dimensions: ${originalBitmap.width}x${originalBitmap.height}
            - Crop scale factors: scaleX=$scaleX, scaleY=$scaleY
            
            Preview to Image conversion:
            - faceBox.left * scaleX = ${faceBox.left} * $scaleX = ${faceBox.left * scaleX}
            - faceBox.top * scaleY = ${faceBox.top} * $scaleY = ${faceBox.top * scaleY}
            - faceBox.right * scaleX = ${faceBox.right} * $scaleX = ${faceBox.right * scaleX}
            - faceBox.bottom * scaleY = ${faceBox.bottom} * $scaleY = ${faceBox.bottom * scaleY}
            
            Final crop coordinates (clamped):
            - actualLeft=$actualLeft, actualTop=$actualTop
            - actualRight=$actualRight, actualBottom=$actualBottom
            - cropWidth=${actualRight - actualLeft}, cropHeight=${actualBottom - actualTop}
        """.trimIndent())
    }

    fun logOvalGuideInfo(previewWidth: Float, previewHeight: Float) {
        val ovalWidth = previewWidth * 0.75f
        val ovalHeight = ovalWidth * 1.3f
        val left = (previewWidth - ovalWidth) / 2
        val top = (previewHeight - ovalHeight) / 2

        Log.d(TAG, """
            ⭕ OVAL GUIDE INFO:
            - Preview dimensions: ${previewWidth}x${previewHeight}
            - Oval dimensions: ${ovalWidth}x${ovalHeight}
            - Oval position: left=$left, top=$top
            - Oval bounds: right=${left + ovalWidth}, bottom=${top + ovalHeight}
        """.trimIndent())
    }

    fun compareOvalVsFaceBox(
        previewWidth: Float,
        previewHeight: Float,
        faceBox: FaceBox
    ) {
        val ovalWidth = previewWidth * 0.75f
        val ovalHeight = ovalWidth * 1.3f
        val ovalLeft = (previewWidth - ovalWidth) / 2
        val ovalTop = (previewHeight - ovalHeight) / 2
        val ovalRight = ovalLeft + ovalWidth
        val ovalBottom = ovalTop + ovalHeight

        Log.d(TAG, """
            🎯 OVAL vs FACE BOX COMPARISON:
            
            Oval Guide:
            - Bounds: left=$ovalLeft, top=$ovalTop, right=$ovalRight, bottom=$ovalBottom
            - Center: x=${ovalLeft + ovalWidth/2}, y=${ovalTop + ovalHeight/2}
            - Size: ${ovalWidth}x${ovalHeight}
            
            Detected Face Box:
            - Bounds: left=${faceBox.left}, top=${faceBox.top}, right=${faceBox.right}, bottom=${faceBox.bottom}
            - Center: x=${faceBox.left + (faceBox.right - faceBox.left)/2}, y=${faceBox.top + (faceBox.bottom - faceBox.top)/2}
            - Size: ${faceBox.right - faceBox.left}x${faceBox.bottom - faceBox.top}
            
            Differences:
            - Center X difference: ${(ovalLeft + ovalWidth/2) - (faceBox.left + (faceBox.right - faceBox.left)/2)}
            - Center Y difference: ${(ovalTop + ovalHeight/2) - (faceBox.top + (faceBox.bottom - faceBox.top)/2)}
            - Width difference: ${ovalWidth - (faceBox.right - faceBox.left)}
            - Height difference: ${ovalHeight - (faceBox.bottom - faceBox.top)}
        """.trimIndent())
    }
}