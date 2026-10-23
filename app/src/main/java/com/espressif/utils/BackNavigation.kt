// Copyright 2026 Espressif Systems (Shanghai) PTE LTD
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

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/**
 * Helpers for system back navigation.
 *
 * Once an app targets Android 16 (API 36), the system no longer calls Activity.onBackPressed()
 * and no longer dispatches KEYCODE_BACK: back goes through OnBackInvokedDispatcher and, unless the
 * Activity registered a callback there, the system finishes the Activity by itself. Anything that
 * must run before a screen is left therefore has to be registered through OnBackPressedDispatcher.
 * AndroidX connects it to both paths: the system OnBackInvokedDispatcher that Android 16 uses for
 * apps targeting API 36, and the legacy KEYCODE_BACK / onBackPressed() path of older releases, so
 * one registration behaves the same on every supported release.
 *
 * While a callback is enabled, the system does not play its predictive back animation for that
 * screen. Use this only where the action must run on every back navigation, and keep callbacks
 * disabled when there is nothing to intercept.
 */
object BackNavigation {

    /**
     * Runs [action] when the user navigates back, then performs the default back behaviour:
     * popping the fragment back stack if there is one, otherwise finishing the Activity.
     *
     * The callback is bound to the Activity's lifecycle and removed automatically on destroy.
     */
    @JvmStatic
    fun runBeforeDefaultBack(activity: ComponentActivity, action: Runnable) {
        val dispatcher = activity.onBackPressedDispatcher
        dispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                action.run()
                // Disable this callback so the dispatcher falls through to the default behaviour,
                // then re-enable it in case the Activity stays on screen (fragment pop).
                isEnabled = false
                dispatcher.onBackPressed()
                isEnabled = true
            }
        })
    }
}
