package com.example.headposeestimator

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.headposeestimator.databinding.ActivityMainBinding
import com.example.sdk.HeadPoseConfig
import com.example.sdk.HeadPoseDetector
import com.example.sdk.HeadPoseResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), HeadPoseDetector.HeadPoseListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var headPoseDetector: HeadPoseDetector? = null

    // For analyzing camera frames
    private var imageAnalyzer: ImageAnalysis? = null

    // Flag to prevent multiple permission dialogs
    private var permissionRequested = false

    // Launcher for permission request
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(
                this,
                getString(R.string.camera_permission_required),
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize OpenCV
        try {
            HeadPoseDetector.initOpenCV()
            Log.d(TAG, "OpenCV initialized successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to initialize OpenCV: ${e.message}")
            Toast.makeText(this, "Failed to initialize OpenCV", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Create custom head pose configuration with desired thresholds
        val headPoseConfig = HeadPoseConfig(
            // Customize thresholds as needed
            yawRange = Pair(35.0, 49.0),
            pitchRange = Pair(-158.0, -154.0),
            rollRange = Pair(90.0, 102.0),
            // Camera parameters
            focalLength = 1000.0,
            centerX = 640.0,
            centerY = 480.0,
            // Face detection parameters
            faceDetectionConfidence = 0.5f,
            faceTrackingConfidence = 0.5f,
            facePresenceConfidence = 0.5f,
            maxNumFaces = 1
        )

        // Initialize head pose detector with our configuration
        headPoseDetector = HeadPoseDetector.create(
            context = this,
            config = headPoseConfig,
            listener = this
        )

        // Create camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Check and request camera permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else if (!permissionRequested) {
            permissionRequested = true
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Configure camera preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            // Configure image analyzer
            imageAnalyzer = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        // Process frame with our head pose detector
                        headPoseDetector?.processFrame(imageProxy, true)
                    }
                }

            try {
                // Unbind any bound use cases
                cameraProvider.unbindAll()

                // Select front camera
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )

                Log.d(TAG, "Camera started successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
                Toast.makeText(this, "Failed to start camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // HeadPoseListener implementation
    override fun onHeadPoseDetected(result: HeadPoseResult) {
        runOnUiThread {
            // Update UI with head pose information
            binding.yawText.text = String.format("%.1f°", result.yaw)
            binding.pitchText.text = String.format("%.1f°", result.pitch)
            binding.rollText.text = String.format("%.1f°", result.roll)

            // Update position status
            if (result.isInPosition) {
                binding.positionText.text = getString(R.string.in_position)
                binding.positionText.setTextColor(Color.GREEN)
                binding.overlayImage.setColorFilter(Color.GREEN)
            } else {
                binding.positionText.text = getString(R.string.not_in_position)
                binding.positionText.setTextColor(Color.RED)
                binding.overlayImage.setColorFilter(Color.RED)
            }
        }
    }

    override fun onNoFaceDetected() {
        runOnUiThread {
            // Clear head pose data when no face is detected
            binding.yawText.text = getString(R.string.not_available)
            binding.pitchText.text = getString(R.string.not_available)
            binding.rollText.text = getString(R.string.not_available)
            binding.positionText.text = getString(R.string.not_detected)
            binding.positionText.setTextColor(Color.GRAY)
            binding.overlayImage.setColorFilter(Color.GRAY)
        }
    }

    override fun onError(error: String) {
        Log.e(TAG, "Head pose detection error: $error")
        runOnUiThread {
            Toast.makeText(this, "Detection error: $error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Shutdown executor and release resources
        cameraExecutor.shutdown()
        headPoseDetector?.shutdown()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}