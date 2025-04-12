package com.example.sdk

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.tasks.vision.core.RunningMode

/**
 * Main class for head pose detection.
 * This is the primary interface that developers will use.
 */
class HeadPoseDetector private constructor(
    private val context: Context,
    private val config: HeadPoseConfig,
    private val headPoseListener: HeadPoseListener
) : FaceLandmarkerHelper.LandmarkerListener {

    private val TAG = "HeadPoseDetector"

    // Face landmark detector
    private val faceLandmarkerHelper = FaceLandmarkerHelper(
        minFaceDetectionConfidence = config.faceDetectionConfidence,
        minFaceTrackingConfidence = config.faceTrackingConfidence,
        minFacePresenceConfidence = config.facePresenceConfidence,
        maxNumFaces = config.maxNumFaces,
        currentDelegate = FaceLandmarkerHelper.DELEGATE_CPU,
        runningMode = RunningMode.LIVE_STREAM,
        context = context,
        faceLandmarkerHelperListener = this
    )

    // Head pose estimator
    private val headPoseEstimator = HeadPoseEstimator(context, config)

    // Flag for background processing
    private var isProcessingFrame = false

    /**
     * Process a camera frame to detect head pose.
     *
     * @param imageProxy Camera image to process
     * @param isFrontCamera Whether the image is from front camera
     */
    fun processFrame(imageProxy: ImageProxy, isFrontCamera: Boolean = true) {
        // Skip if already processing a frame
        if (isProcessingFrame) {
            imageProxy.close()
            return
        }

        isProcessingFrame = true

        try {
            // Pass the image to FaceLandmarkerHelper for processing
            faceLandmarkerHelper.detectLiveStream(imageProxy, isFrontCamera)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame: ${e.message}")
            isProcessingFrame = false
            headPoseListener.onError("Failed to process image: ${e.message}")
        }
    }

    /**
     * Release resources used by the detector.
     * Call this when you're done using the detector.
     */
    fun shutdown() {
        faceLandmarkerHelper.clearFaceLandmarker()
    }

    override fun onError(error: String, errorCode: Int) {
        isProcessingFrame = false
        headPoseListener.onError(error)
    }

    override fun onEmpty() {
        isProcessingFrame = false
        headPoseListener.onNoFaceDetected()
    }

    override fun onResults(resultBundle: FaceLandmarkerHelper.ResultBundle) {
        try {
            // Calculate head pose using the face landmarks
            val headPoseResult = headPoseEstimator.calculateHeadPose(
                resultBundle.result,
                resultBundle.inputImageWidth,
                resultBundle.inputImageHeight
            )

            // Notify listener of the head pose result
            if (headPoseResult != null) {
                headPoseListener.onHeadPoseDetected(headPoseResult)
            } else {
                headPoseListener.onNoFaceDetected()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in head pose calculation: ${e.message}")
            headPoseListener.onError("Head pose calculation failed: ${e.message}")
        } finally {
            isProcessingFrame = false
        }
    }

    companion object {
        /**
         * Initialize OpenCV - must be called before creating HeadPoseDetector instance
         */
        fun initOpenCV() {
            System.loadLibrary("opencv_java4")
        }

        /**
         * Create a new instance of HeadPoseDetector
         *
         * @param context Android context
         * @param config Configuration for head pose detection thresholds
         * @param listener Listener for head pose detection events
         * @return HeadPoseDetector instance
         */
        fun create(
            context: Context,
            config: HeadPoseConfig = HeadPoseConfig(),
            listener: HeadPoseListener
        ): HeadPoseDetector {
            return HeadPoseDetector(context, config, listener)
        }
    }

    /**
     * Listener interface for head pose detection events
     */
    interface HeadPoseListener {
        /**
         * Called when head pose is detected
         *
         * @param result Head pose detection result
         */
        fun onHeadPoseDetected(result: HeadPoseResult)

        /**
         * Called when no face is detected in the frame
         */
        fun onNoFaceDetected()

        /**
         * Called when an error occurs during detection
         *
         * @param error Error message
         */
        fun onError(error: String)
    }
}