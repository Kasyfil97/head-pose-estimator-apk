package com.example.sdk

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.opencv.core.*
import org.opencv.calib3d.Calib3d
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Helper class to estimate head pose (yaw, pitch, roll angles) from face landmarks.
 */
class HeadPoseEstimator(
    private val context: Context,
    private val config: HeadPoseConfig = HeadPoseConfig()
) {
    private val TAG = "HeadPoseEstimator"

    // Cached matrices to avoid reallocations
    private val cameraMat = Mat(3, 3, CvType.CV_64F).apply {
        put(0, 0, config.focalLength, 0.0, config.centerX, 0.0, config.focalLength, config.centerY, 0.0, 0.0, 1.0)
    }
    private val distCoeffs = MatOfDouble(Mat.zeros(4, 1, CvType.CV_64F))
    private val rVec = Mat()
    private val tVec = Mat()
    private val rMat = Mat()

    // Dedicated thread for head pose calculations
    private val headPoseExecutor: Executor = Executors.newSingleThreadExecutor()

    // Cache for model points
    val modelPoints by lazy { loadModelPoints() }

    // Cache for the last calculation time to throttle calculations
    private var lastCalculationTime = 0L
    private val CALCULATION_THROTTLE_MS = 50L  // Process at most every 50ms

    // Cache for recent results
    private var lastHeadPose: Triple<Double, Double, Double>? = null
    private var lastPositionStatus = false

    private fun loadModelPoints(): D2Array<Double> {
        return try {
            context.assets.open("model.txt").bufferedReader().use { reader ->
                val values = reader.readLines()
                    .filter { it.isNotBlank() }
                    .flatMap { it.split("\\s+".toRegex()).mapNotNull { num -> num.toDoubleOrNull() } }

                if (values.size % 3 != 0) {
                    throw Exception("Invalid model file: number of values (${values.size}) is not a multiple of 3")
                }

                val pointArray = values.chunked(3) { triple ->
                    doubleArrayOf(triple[0], triple[1], triple[2])
                }.toTypedArray()

                Log.d(TAG, "Loaded model points: ${pointArray.size} points")

                mk.ndarray(pointArray)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model points: ${e.message}")
            mk.ndarray(arrayOf(doubleArrayOf(0.0, 0.0, 0.0))) // Dummy data
        }
    }

    /**
     * Calculate head pose from face landmarks result.
     *
     * @param result FaceLandmarkerResult from MediaPipe
     * @param imageWidth Width of the input image
     * @param imageHeight Height of the input image
     * @return HeadPoseResult containing pitch, yaw, roll angles and position status
     */
    fun calculateHeadPose(
        result: FaceLandmarkerResult,
        imageWidth: Int,
        imageHeight: Int
    ): HeadPoseResult? {
        // Return null if no face landmarks
        if (result.faceLandmarks().isEmpty()) {
            return null
        }

        // Return cached result if we've calculated recently
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCalculationTime < CALCULATION_THROTTLE_MS && lastHeadPose != null) {
            val (cachedPitch, cachedYaw, cachedRoll) = lastHeadPose!!
            return HeadPoseResult(
                cachedPitch,
                cachedYaw,
                cachedRoll,
                isInThresholds(cachedPitch, cachedYaw, cachedRoll)
            )
        }

        lastCalculationTime = currentTime

        try {
            val landmarks = result.faceLandmarks()[0]

            // Create image points only for the landmarks we need
            val imagePoints = MatOfPoint2f(*Array(modelPoints.shape[0]) { i ->
                Point(
                    (landmarks[i].x() * imageWidth).toDouble(),
                    (landmarks[i].y() * imageHeight).toDouble()
                )
            })

            // Convert model points to OpenCV format - do this once
            val modelMat = MatOfPoint3f(*Array(modelPoints.shape[0]) { i ->
                Point3(modelPoints[i][0], modelPoints[i][1], modelPoints[i][2])
            })

            // Solve PnP problem
            val success = Calib3d.solvePnP(
                modelMat, imagePoints, cameraMat, distCoeffs, rVec, tVec
            )

            if (!success) {
                Log.w(TAG, "solvePnP failed")
                return lastHeadPose?.let {
                    HeadPoseResult(it.first, it.second, it.third, lastPositionStatus)
                }
            }

            // Convert rotation vector to rotation matrix
            Calib3d.Rodrigues(rVec, rMat)

            if (rMat.rows() != 3 || rMat.cols() != 3) {
                Log.w(TAG, "Rotation matrix has invalid shape: ${rMat.rows()}x${rMat.cols()}")
                return lastHeadPose?.let {
                    HeadPoseResult(it.first, it.second, it.third, lastPositionStatus)
                }
            }

            // Convert rotation matrix to Euler angles
            val rotationMatrix = mk.ndarray(Array(3) { i ->
                doubleArrayOf(
                    rMat.get(i, 0)[0],
                    rMat.get(i, 1)[0],
                    rMat.get(i, 2)[0]
                )
            })

            val (pitch, yaw, roll) = rotationMatrixToEulerAngles(rotationMatrix)
            lastHeadPose = Triple(pitch, yaw, roll)

            val isInPosition = isInThresholds(pitch, yaw, roll)
            lastPositionStatus = isInPosition

            return HeadPoseResult(pitch, yaw, roll, isInPosition)

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating head pose: ${e.message}")
            return lastHeadPose?.let {
                HeadPoseResult(it.first, it.second, it.third, lastPositionStatus)
            }
        }
    }

    private fun rotationMatrixToEulerAngles(R: D2Array<Double>): Triple<Double, Double, Double> {
        val sy = kotlin.math.sqrt(R[0][0] * R[0][0] + R[1][0] * R[1][0])

        val pitch: Double
        val yaw: Double
        val roll: Double

        if (sy > 1e-6) {
            pitch = kotlin.math.atan2(R[2][1], R[2][2])
            yaw = kotlin.math.atan2(-R[2][0], sy)
            roll = kotlin.math.atan2(R[1][0], R[0][0])
        } else {
            pitch = kotlin.math.atan2(-R[1][2], R[1][1])
            yaw = kotlin.math.atan2(-R[2][0], sy)
            roll = 0.0
        }

        return Triple(
            Math.toDegrees(pitch),
            Math.toDegrees(yaw),
            Math.toDegrees(roll)
        )
    }

    /**
     * Check if current head pose angles are within configured thresholds.
     */
    fun isInThresholds(pitch: Double, yaw: Double, roll: Double): Boolean {
        return pitch in config.pitchRange.first..config.pitchRange.second &&
                yaw in config.yawRange.first..config.yawRange.second &&
                roll in config.rollRange.first..config.rollRange.second
    }
}