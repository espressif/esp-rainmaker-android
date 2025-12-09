// Copyright 2025 Espressif Systems (Shanghai) PTE LTD
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.espressif.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.espressif.AppConstants
import com.espressif.ui.Utils
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Manager class to handle Google Play In-App Review functionality
 * Shows review prompts based on specific user actions and conditions
 */
class InAppReviewManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "InAppReviewManager"

        // SharedPreferences keys for tracking review conditions
        private const val PREF_REVIEW_SCHEDULE_SHOWN = "review_schedule_shown"
        private const val PREF_REVIEW_SCENE_SHOWN = "review_scene_shown"
        private const val PREF_REVIEW_AUTOMATION_SHOWN = "review_automation_shown"
        private const val PREF_REVIEW_DEVICE_SHOWN = "review_device_shown"
        private const val PREF_DEVICE_ADD_COUNT = "device_add_count"

        @Volatile
        private var INSTANCE: InAppReviewManager? = null

        fun getInstance(context: Context): InAppReviewManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: InAppReviewManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val reviewManager: ReviewManager by lazy {
        ReviewManagerFactory.create(context)
    }

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE)
    }

    /**
     * Review trigger conditions
     */
    enum class ReviewTrigger {
        FIRST_SCHEDULE_SUCCESS,
        FIRST_SCENE_SUCCESS,
        FIRST_AUTOMATION_SUCCESS,
        SECOND_DEVICE_SUCCESS
    }

    /**
     * Request in-app review for first successful schedule creation
     */
    fun requestReviewForFirstSchedule(activity: Activity) {
        if (!hasReviewBeenShown(ReviewTrigger.FIRST_SCHEDULE_SUCCESS)) {
            Log.d(TAG, "Requesting review for first schedule creation")
            requestInAppReview(activity, ReviewTrigger.FIRST_SCHEDULE_SUCCESS)
        } else {
            Log.d(TAG, "Review already shown for first schedule creation")
        }
    }

    /**
     * Request in-app review for first successful scene creation
     */
    fun requestReviewForFirstScene(activity: Activity) {
        if (!hasReviewBeenShown(ReviewTrigger.FIRST_SCENE_SUCCESS)) {
            Log.d(TAG, "Requesting review for first scene creation")
            requestInAppReview(activity, ReviewTrigger.FIRST_SCENE_SUCCESS)
        } else {
            Log.d(TAG, "Review already shown for first scene creation")
        }
    }

    /**
     * Request in-app review for first successful automation creation
     */
    fun requestReviewForFirstAutomation(activity: Activity) {
        if (!hasReviewBeenShown(ReviewTrigger.FIRST_AUTOMATION_SUCCESS)) {
            Log.d(TAG, "Requesting review for first automation creation")
            requestInAppReview(activity, ReviewTrigger.FIRST_AUTOMATION_SUCCESS)
        } else {
            Log.d(TAG, "Review already shown for first automation creation")
        }
    }

    /**
     * Track device addition and request review on second successful device addition
     */
    fun trackDeviceAddition(activity: Activity) {
        val currentCount = sharedPreferences.getInt(PREF_DEVICE_ADD_COUNT, 0)
        val newCount = currentCount + 1

        sharedPreferences.edit()
            .putInt(PREF_DEVICE_ADD_COUNT, newCount)
            .apply()

        Log.d(TAG, "Device addition count: $newCount")

        if (newCount == 2 && !hasReviewBeenShown(ReviewTrigger.SECOND_DEVICE_SUCCESS)) {
            Log.d(TAG, "Requesting review for second device addition")
            requestInAppReview(activity, ReviewTrigger.SECOND_DEVICE_SUCCESS)
        }
    }

    /**
     * Check if review has already been shown for a specific trigger
     */
    private fun hasReviewBeenShown(trigger: ReviewTrigger): Boolean {
        val prefKey = when (trigger) {
            ReviewTrigger.FIRST_SCHEDULE_SUCCESS -> PREF_REVIEW_SCHEDULE_SHOWN
            ReviewTrigger.FIRST_SCENE_SUCCESS -> PREF_REVIEW_SCENE_SHOWN
            ReviewTrigger.FIRST_AUTOMATION_SUCCESS -> PREF_REVIEW_AUTOMATION_SHOWN
            ReviewTrigger.SECOND_DEVICE_SUCCESS -> PREF_REVIEW_DEVICE_SHOWN
        }
        return sharedPreferences.getBoolean(prefKey, false)
    }

    /**
     * Mark that review has been shown for a specific trigger
     */
    private fun markReviewAsShown(trigger: ReviewTrigger) {
        val prefKey = when (trigger) {
            ReviewTrigger.FIRST_SCHEDULE_SUCCESS -> PREF_REVIEW_SCHEDULE_SHOWN
            ReviewTrigger.FIRST_SCENE_SUCCESS -> PREF_REVIEW_SCENE_SHOWN
            ReviewTrigger.FIRST_AUTOMATION_SUCCESS -> PREF_REVIEW_AUTOMATION_SHOWN
            ReviewTrigger.SECOND_DEVICE_SUCCESS -> PREF_REVIEW_DEVICE_SHOWN
        }

        sharedPreferences.edit()
            .putBoolean(prefKey, true)
            .apply()

        Log.d(TAG, "Marked review as shown for trigger: $trigger")
    }

    /**
     * Request Google Play In-App Review
     */
    private fun requestInAppReview(activity: Activity, trigger: ReviewTrigger) {
        if (activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "Activity is finishing or destroyed, skipping review request")
            return
        } else if (!Utils.isPlayServicesAvailable(activity.applicationContext)) {
            Log.w(TAG, "Google Play services are not available")
            return
        }

        Log.d(TAG, "Requesting in-app review for trigger: $trigger")

        val request = reviewManager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo: ReviewInfo = task.result
                Log.d(TAG, "Review flow request successful, launching review")

                val flow = reviewManager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener { launchTask ->
                    if (launchTask.isSuccessful) {
                        Log.d(TAG, "Review flow launched successfully")
                        // Mark as shown regardless of user action in the review dialog
                        markReviewAsShown(trigger)
                    } else {
                        Log.e(TAG, "Failed to launch review flow", launchTask.exception)
                        // Still mark as shown to avoid repeated attempts
                        markReviewAsShown(trigger)
                    }
                }
            } else {
                Log.e(TAG, "Failed to request review flow", task.exception)
                // Mark as shown to avoid repeated failed attempts
                markReviewAsShown(trigger)
            }
        }
    }

    /**
     * Reset all review tracking (for testing purposes)
     */
    fun resetReviewTracking() {
        sharedPreferences.edit()
            .putBoolean(PREF_REVIEW_SCHEDULE_SHOWN, false)
            .putBoolean(PREF_REVIEW_SCENE_SHOWN, false)
            .putBoolean(PREF_REVIEW_AUTOMATION_SHOWN, false)
            .putBoolean(PREF_REVIEW_DEVICE_SHOWN, false)
            .putInt(PREF_DEVICE_ADD_COUNT, 0)
            .apply()

        Log.d(TAG, "Reset all review tracking")
    }

    /**
     * Get current device addition count (for debugging)
     */
    fun getDeviceAdditionCount(): Int {
        return sharedPreferences.getInt(PREF_DEVICE_ADD_COUNT, 0)
    }

    /**
     * Check if all review conditions have been triggered
     */
    fun hasAllReviewsBeenShown(): Boolean {
        return hasReviewBeenShown(ReviewTrigger.FIRST_SCHEDULE_SUCCESS) &&
                hasReviewBeenShown(ReviewTrigger.FIRST_SCENE_SUCCESS) &&
                hasReviewBeenShown(ReviewTrigger.FIRST_AUTOMATION_SUCCESS) &&
                hasReviewBeenShown(ReviewTrigger.SECOND_DEVICE_SUCCESS)
    }
}
