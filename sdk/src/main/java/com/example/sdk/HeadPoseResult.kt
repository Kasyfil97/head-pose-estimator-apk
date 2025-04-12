package com.example.sdk

/**
 * Result class for head pose estimation.
 * Contains the angles and position status.
 */
data class HeadPoseResult(
    /** Pitch angle in degrees (vertical tilting) */
    val pitch: Double,

    /** Yaw angle in degrees (horizontal rotation) */
    val yaw: Double,

    /** Roll angle in degrees (tilting) */
    val roll: Double,

    /** Flag indicating if head is properly positioned based on thresholds */
    val isInPosition: Boolean
)