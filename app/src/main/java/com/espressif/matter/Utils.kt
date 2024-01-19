/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.espressif.matter

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.google.protobuf.Timestamp
import java.io.File
import java.lang.Long.max
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Variety of constants and utility functions used in the app. */

// -------------------------------------------------------------------------------------------------
// Various constants

lateinit var VERSION_NAME: String
lateinit var APP_NAME: String

// -------------------------------------------------------------------------------------------------
// Display helper functions

/** Enumeration of statuses for an asynchronous [com.google.android.gms.tasks.Task]. */
sealed class TaskStatus {
    /** The task has not been started. */
    object NotStarted : TaskStatus()

    /** The task has been started, and has not yet completed with a result. */
    object InProgress : TaskStatus()

    /**
     * The task completed with an exception.
     *
     * @param cause the cause of the failure
     */
    class Failed(val message: String, val cause: Throwable?) : TaskStatus()

    /**
     * The task completed successfully.
     *
     * @param statusMessage a message to be displayed in the UI
     */
    class Completed(val statusMessage: String) : TaskStatus()
}

/** Enumeration of actions to take a background work alert dialog. */
sealed class BackgroundWorkAlertDialogAction {
    /** Background work has started, show the dialog. */
    class Show(val title: String, val message: String) : BackgroundWorkAlertDialogAction()

    /** Background work has completed, hide the dialog. */
    object Hide : BackgroundWorkAlertDialogAction()
}

/** Useful when investigating lifecycle events in logcat. */
fun lifeCycleEvent(event: String): String {
    return "[*** LifeCycle ***] $event"
}

/** Set the strings for DeviceType. */
//lateinit var DeviceTypeStrings: MutableMap<Device.DeviceType, String>
//
//fun setDeviceTypeStrings(unspecified: String, light: String, outlet: String, unknown: String) {
//    DeviceTypeStrings =
//        mutableMapOf(
//            Device.DeviceType.TYPE_UNSPECIFIED to unspecified,
//            Device.DeviceType.TYPE_LIGHT to light,
//            Device.DeviceType.TYPE_OUTLET to outlet,
//            Device.DeviceType.TYPE_UNKNOWN to unknown,
//        )
//}
//
///** Converts the Device.DeviceType enum to a string used in the UI. */
//fun Device.DeviceType.displayString(): String {
//    return DeviceTypeStrings[this]!!
//}
//
//fun convertToAppDeviceType(matterDeviceType: Long): Device.DeviceType {
//    return when (matterDeviceType) {
//        256L -> Device.DeviceType.TYPE_LIGHT // 0x0100 On/Off Light
//        266L -> Device.DeviceType.TYPE_OUTLET // 0x010a (On/Off Plug-in Unit)
//        else -> Device.DeviceType.TYPE_UNKNOWN
//    }
//}

/** Converts the "isOnline" boolean into a proper string for the UI. */
fun isOnlineDisplayString(isOnline: Boolean): String {
    return if (isOnline) "Online" else "Offline"
}

/** Converts the "isOn" boolean into a proper string for the UI. */
fun isOnDisplayString(isOn: Boolean): String {
    return if (isOn) "ON" else "OFF"
}

/** Converts the combo of "isOnline" and "isOn" into a proper string for the UI. */
fun stateDisplayString(isOnline: Boolean, isOn: Boolean): String {
    return if (!isOnline) {
        "OFFLINE"
    } else {
        if (isOn) "ON" else "OFF"
    }
}

fun stringToBoolean(s: String): Boolean {
    val boolValue =
        when (s) {
            "true",
            "True",
            "TRUE" -> true
            else -> false
        }
    return boolValue
}

fun intentSenderToString(intentSender: IntentSender?): String {
    if (intentSender == null) {
        return "null"
    }
    return "creatorPackage [${intentSender.creatorPackage}]"
}

// -------------------------------------------------------------------------------------------------
// System helper functions

fun isMultiAdminCommissioning(intent: Intent): Boolean {
    return intent.action == "com.google.android.gms.home.matter.ACTION_COMMISSION_DEVICE"
}

/**
 * The Matter APIs make use of SharedPreferences. Useful to print what they are when the app starts.
 */
fun displayPreferences(context: Context) {
    val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
    if (prefsDir.exists() && prefsDir.isDirectory) {
        Log.d("Matter-Utils", "*** Preference Files ***")
        val list: Array<String> = prefsDir.list()
        for (element in list) {
            Log.d("Matter-Utils", "*** [${element}] ***")
            val sharedPreferencesFileKey = element.substringBefore(".xml")
            Log.d("Matter-Utils", "*** FileKey: [${sharedPreferencesFileKey}] ***")
            val sharedPreferences =
                context.getSharedPreferences(sharedPreferencesFileKey, Context.MODE_PRIVATE)
            val allPreferences = sharedPreferences.all
            for ((key, value) in allPreferences.entries) Log.d("Matter-Utils", "$key [$value]")
        }
        return
    } else {
        Log.d("Matter-Utils", "prefsDir does not exist: $prefsDir")
        return
    }
}

/** Returns a com.google.protobuf.Timestamp for the current time. */
fun getTimestampForNow(): Timestamp {
    val now = Instant.now()
    return Timestamp.newBuilder().setSeconds(now.epochSecond).setNanos(now.nano).build()
}

/**
 * Formats a com.google.protobuf.Timestamp according to the specified pattern. If _pattern is null,
 * then the default is "MM.dd.yy HH:mm:ss".
 */
private const val TIMESTAMP_DEFAULT_FORMAT_PATTERN = "MM.dd.yy HH:mm:ss"

fun formatTimestamp(timestamp: Timestamp, _pattern: String?): String {
    val pattern = _pattern ?: TIMESTAMP_DEFAULT_FORMAT_PATTERN
    val timestampFormatter = DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.of("UTC"))
    return timestampFormatter.format(Instant.ofEpochSecond(timestamp.seconds))
}

/**
 * Used in the context of StateFlow with MutableLists to ensure changes to the mutable lists trigger
 * data changes for observers. See
 * https://stackoverflow.com/questions/70905480/mutablestateflow-not-working-with-mutablelist
 */
fun <T> MutableList<T>.mapButReplace(targetItem: T, newItem: T) = map {
    Log.d("Matter-Utils", "mapButReplace targetItem [${targetItem}] newItem [${newItem}]")
    if (it == targetItem) {
        Log.d("Matter-Utils", "setting newItem for [${it}] to [${newItem}]")
        newItem
    } else {
        Log.d("Matter-Utils", "setting newItem")
        it
    }
}

/** Generates a random number to be used as a device identifier during device commissioning */
fun generateNextDeviceId(): Long {
    val secureRandom =
        try {
            SecureRandom.getInstance("SHA1PRNG")
        } catch (ex: Exception) {
            Log.e("Matter-Utils", "Failed to instantiate SecureRandom with SHA1PRNG")
            ex.printStackTrace()
            // instantiate with the default algorithm
            SecureRandom()
        }

    return max(abs(secureRandom.nextLong()), 1)
}

/**
 * Strip the link-local portion of an IP Address. Was needed to handle
 * https://github.com/google-home/sample-app-for-matter-android/issues/15. For example:
 * ```
 *    "fe80::84b1:c2f6:b1b7:67d4%wlan0"
 * ```
 *
 * becomes
 *
 * ```
 *    ""fe80::84b1:c2f6:b1b7:67d4"
 * ```
 *
 * The "%wlan0" at the end of the link-local ip address is stripped.
 */
fun stripLinkLocalInIpAddress(ipAddress: String): String {
    return ipAddress.replace("%.*".toRegex(), "")
}

// -------------------------------------------------------------------------------------------------
// Constants

// -------------------------------------------------------------------------------------------------
// Constants used when creating devices on the app's fabric.

// Shared device creation
const val SHARED_DEVICE_NAME_PREFIX = "Shared-"
const val SHARED_DEVICE_NAME_SUFFIX = ""
const val SHARED_DEVICE_ROOM_PREFIX = "Room-"

// Temporary device name used when commissioning the device to the 3P fabric.
const val REAL_DEVICE_NAME_PREFIX = "Real-"

// -------------------------------------------------------------------------------------------------
// Dialogs

fun showAlertDialog(alertDialog: AlertDialog, title: String?, message: String?) {
    if (title != null) {
        alertDialog.setTitle(title)
    }
    if (message != null) {
        alertDialog.setMessage(message)
    }
    alertDialog.show()
}

data class ErrorInfo(val title: String?, val message: String?)

// Used by ViewModel to communicate a UI action to be processed by a Fragment.
data class UiAction(val id: String, val data: String? = null)

// -------------------------------------------------------------------------------------------------
// Device Sharing constants

// How long a commissioning window for Device Sharing should be open.
const val OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS = 180

// Discriminator
const val DISCRIMINATOR = 123

// Iteration
const val ITERATION = 10000L

// Iteration
const val SETUP_PIN_CODE = 11223344L

// Minimum time required to handle the multi-admin commissioning
// intent just received.
const val MIN_COMMISSIONING_WINDOW_EXPIRATION_SECONDS = 20

// -------------------------------------------------------------------------------------------------
// Constants to modify the behavior of the app.

// Whether the on/off switch is disabled when the device is offline.
const val ON_OFF_SWITCH_DISABLED_WHEN_DEVICE_OFFLINE = false

// ----- Periodic monitoring of device state changes -----

// Modes supported for monitoring state changes.
enum class StateChangesMonitoringMode {
    // Subscription is what should normally be used.
    Subscription,

    // Left for historical reasons when we had issues with Subscription.
    PeriodicRead
}

val STATE_CHANGES_MONITORING_MODE = StateChangesMonitoringMode.Subscription

// Intervals for PeriodicRead mode.
const val PERIODIC_READ_INTERVAL_HOME_SCREEN_SECONDS = 10
const val PERIODIC_READ_INTERVAL_DEVICE_SCREEN_SECONDS = 2

// ----- Device Sharing -----

// Whether DeviceSharing does commissioning with GPS.
// Alternative is using DNS-SD to discover the device and get its IP address, and then
// do the standard 3P commissioning.
const val DEVICE_SHARING_WITH_GPS = true

// Which API should be used for opening the commissioning window for DeviceSharing.
enum class OpenCommissioningWindowApi {
    ChipDeviceController,
    AdministratorCommissioningCluster
}

// Which method should be used to generate identifiers for devices being commissioned
enum class DeviceIdGenerator {
    Random,
    Incremental
}

/**
 * Indicates the status of a node's commissioning window. Useful in the context of "multi-admin"
 * when a temporary commissioning window must be open for a target commissioner. That's because
 * sometimes multi-admin may fail with the target commissioner (especially in a testing environment)
 * and the temporary commissioning window can then stay open for a substantial amount of time (e.g.
 * 3 minutes) preventing a new "multi-admin" to fail until that temporary commissioning window is
 * closed. Checking on the status of the commissioning window beforehand makes it possible to close
 * the currently open temporary commissioning window before trying to open a new one. [status] is
 * the enum value returned by reading the WindowStatusAttribute of the "Administrator Commissioning
 * Cluster". (See spec section "11.18.6.1. CommissioningWindowStatus enum").
 */
enum class CommissioningWindowStatus(val status: Int) {
    /** Commissioning window not open */
    WindowNotOpen(0),

    /** An Enhanced Commissioning Method window is open */
    EnhancedWindowOpen(1),

    /** A Basic Commissioning Method window is open */
    BasicWindowOpen(2)
}

val OPEN_COMMISSIONING_WINDOW_API = OpenCommissioningWindowApi.ChipDeviceController
