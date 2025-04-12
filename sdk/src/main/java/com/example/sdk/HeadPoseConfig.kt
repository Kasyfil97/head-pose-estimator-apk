package com.example.sdk

/**
 * Configuration class for HeadPoseDetector.
 * Allows developers to customize thresholds for determining head pose status.
 */
data class HeadPoseConfig(
    /**
     * Yaw (horizontal) angle threshold range in degrees.
     * The head is considered in position when yaw angle is within this range.
     */
    val yawRange: Pair<Double, Double> = Pair(35.0, 49.0),

    /**
     * Pitch (vertical) angle threshold range in degrees.
     * The head is considered in position when pitch angle is within this range.
     */
    val pitchRange: Pair<Double, Double> = Pair(-158.0, -154.0),

    /**
     * Roll (tilt) angle threshold range in degrees.
     * The head is considered in position when roll angle is within this range.
     */
    val rollRange: Pair<Double, Double> = Pair(90.0, 102.0),

    /**
     * Focal length for camera calibration.
     */
    val focalLength: Double = 1000.0,

    /**
     * Camera center X coordinate.
     */
    val centerX: Double = 640.0,

    /**
     * Camera center Y coordinate.
     */
    val centerY: Double = 480.0,

    /**
     * Face detection confidence threshold.
     */
    val faceDetectionConfidence: Float = FaceLandmarkerHelper.DEFAULT_FACE_DETECTION_CONFIDENCE,

    /**
     * Face tracking confidence threshold.
     */
    val faceTrackingConfidence: Float = FaceLandmarkerHelper.DEFAULT_FACE_TRACKING_CONFIDENCE,

    /**
     * Face presence confidence threshold.
     */
    val facePresenceConfidence: Float = FaceLandmarkerHelper.DEFAULT_FACE_PRESENCE_CONFIDENCE,

    /**
     * Maximum number of faces to detect.
     */
    val maxNumFaces: Int = FaceLandmarkerHelper.DEFAULT_NUM_FACES
)