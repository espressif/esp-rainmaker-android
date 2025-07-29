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
import android.content.IntentSender
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.espressif.AppConstants
import com.espressif.rainmaker.R
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Helper class to manage app updates using Google Play's In-App Update API.
 * Supports both immediate and flexible updates.
 */
object AppUpdateHelper {

    const val REQUEST_APP_UPDATE = 500
    private const val TAG = "AppUpdateHelper"

    private var appUpdateManager: AppUpdateManager? = null
    private var installStateUpdatedListener: InstallStateUpdatedListener? = null

    /**
     * Check for app updates. This should be called in the activity's onCreate() or onResume().
     *
     * @param activity The activity context
     * @param isForceUpdate If true, shows immediate update dialog that can't be dismissed
     */
    fun checkForUpdates(activity: Activity, isForceUpdate: Boolean) {

        Log.d(TAG, "Checking for updates.................")
        try {
            if (appUpdateManager == null) {
                appUpdateManager = AppUpdateManagerFactory.create(activity)
            }

            // Create a listener to track the state of the update
            installStateUpdatedListener = InstallStateUpdatedListener { state ->
                when (state.installStatus()) {
                    InstallStatus.DOWNLOADED -> {
                        // Update has been downloaded, prompt user to complete installation
                        showCompletionDialog(activity)
                    }

                    InstallStatus.INSTALLED -> {
                        // Clear update resources
                        clearResources()
                    }

                    InstallStatus.FAILED -> {
                        // Handle the failure
                        clearResources()
                    }

                    else -> {
                        // Handle other states if needed
                    }
                }
            }

            // Register the listener
            installStateUpdatedListener?.let {
                appUpdateManager?.registerListener(it)
            }

            // Returns an intent object that you use to check for an update
            val appUpdateInfoTask = appUpdateManager?.appUpdateInfo

            // Checks that the platform will allow the specified type of update
            appUpdateInfoTask?.addOnSuccessListener { appUpdateInfo ->

                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {

                    // If force update, always show popup. Otherwise, check if user has skipped this version
                    val sharedPreferences =
                        activity.getSharedPreferences(
                            AppConstants.ESP_PREFERENCES,
                            Context.MODE_PRIVATE
                        )
                    val skippedVersion = sharedPreferences.getBoolean(
                        appUpdateInfo.availableVersionCode().toString(), false
                    )

                    Log.d(TAG, "App update is available")
                    Log.d(TAG, "Is update skipped ? $skippedVersion")

                    when {
                        isForceUpdate && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> {
                            // For force updates, use immediate update
                            startImmediateUpdate(activity, appUpdateInfo)
                        }

                        !skippedVersion && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                            // For regular updates, use flexible update
                            startFlexibleUpdate(activity, appUpdateInfo)
                        }
                    }
                } else {
                    Log.d(TAG, "App update is not available")
                }
            }?.addOnFailureListener { e ->
                Log.e(TAG, "Failed to check for updates: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for app update: ${e.message}")
        }
    }

    /**
     * Handle the result of the update flow.
     * This should be called in the activity's onActivityResult().
     *
     * @param activity The activity context
     * @param resultCode The result code returned by the update flow
     */
    fun handleUpdateResult(activity: Activity, resultCode: Int) {
        if (resultCode == Activity.RESULT_CANCELED) {
            // User canceled the update, save this preference
            val sharedPreferences =
                activity.getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()

            appUpdateManager!!.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
                // Store the version code that user chose to skip
                editor.putBoolean(appUpdateInfo.availableVersionCode().toString(), true)
                editor.apply()
            }
        }
    }

    /**
     * Resume update if it was interrupted.
     * This should be called in the activity's onResume().
     *
     * @param activity The activity context
     */
    fun resumeUpdate(activity: Activity) {
        appUpdateManager?.appUpdateInfo?.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                try {
                    appUpdateManager?.startUpdateFlowForResult(
                        appUpdateInfo,
                        AppUpdateType.IMMEDIATE,
                        activity,
                        REQUEST_APP_UPDATE
                    )
                } catch (e: IntentSender.SendIntentException) {
                    Log.e(TAG, "Error resuming update: ${e.message}")
                }
            }
        }
    }

    /**
     * Start an immediate update that can't be cancelled by the user
     */
    private fun startImmediateUpdate(activity: Activity, appUpdateInfo: AppUpdateInfo) {
        try {
            appUpdateManager?.startUpdateFlowForResult(
                appUpdateInfo,
                AppUpdateType.IMMEDIATE,
                activity,
                REQUEST_APP_UPDATE
            )
        } catch (e: IntentSender.SendIntentException) {
            Log.e(TAG, "Error starting immediate update: ${e.message}")
        }
    }

    /**
     * Start a flexible update that can be cancelled by the user
     */
    private fun startFlexibleUpdate(activity: Activity, appUpdateInfo: AppUpdateInfo) {
        val version: String = appUpdateInfo.availableVersionCode().toString()
        showUpdateDialog(version, activity) { accepted ->
            if (accepted) {
                try {
                    appUpdateManager?.startUpdateFlowForResult(
                        appUpdateInfo,
                        AppUpdateType.FLEXIBLE,
                        activity,
                        REQUEST_APP_UPDATE
                    )
                } catch (e: IntentSender.SendIntentException) {
                    Log.e(TAG, "Error starting flexible update: ${e.message}")
                }
            }
        }
    }

    /**
     * Show a dialog to prompt user to update the app
     */
    private fun showUpdateDialog(version: String, activity: Activity, callback: (Boolean) -> Unit) {
        AlertDialog.Builder(activity).apply {
            setTitle(R.string.dialog_title_new_version_available)
            setMessage(R.string.update_message)
            setPositiveButton(R.string.btn_update) { dialog, _ ->
                dialog.dismiss()
                callback(true)
            }
            setNegativeButton(R.string.btn_cancel) { dialog, _ ->
                dialog.dismiss()
                callback(false)

                // User canceled for the update, save this preference
                val sharedPreferences =
                    activity.getSharedPreferences(
                        AppConstants.ESP_PREFERENCES,
                        Context.MODE_PRIVATE
                    )
                val editor = sharedPreferences.edit()
                // Store "true" for the version code that user chose to skip
                editor.putBoolean(version, true)
                editor.apply()
            }
            setCancelable(true)
            show()
        }
    }

    /**
     * Show a dialog to prompt user to complete the update installation
     */
    private fun showCompletionDialog(activity: Activity) {
        AlertDialog.Builder(activity).apply {
            setTitle(R.string.dialog_title_new_version_available)
            setMessage(R.string.update_downloaded_message)
            setPositiveButton(R.string.btn_restart) { _, _ ->
                appUpdateManager?.completeUpdate()
            }
            setCancelable(false)
            show()
        }
    }

    /**
     * Clean up resources
     * This should be called in the activity's onDestroy()
     */
    fun clearResources() {
        installStateUpdatedListener?.let {
            appUpdateManager?.unregisterListener(it)
        }
        appUpdateManager = null
        installStateUpdatedListener = null
    }
}