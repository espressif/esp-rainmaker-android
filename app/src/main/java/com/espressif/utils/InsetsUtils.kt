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
import android.app.Dialog
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.espressif.rainmaker.R
import com.google.android.material.appbar.AppBarLayout

/**
 * Helpers for keeping content clear of the system bars.
 *
 * From Android 15 (API 35) onwards, apps that target SDK 35 or higher are laid out edge to edge
 * with no way to opt out permanently, so every window is drawn behind the status bar and the
 * navigation bar. Any view pinned to the bottom of the screen — a bottom navigation bar, an action
 * button, the last row of a list — ends up underneath the gesture pill or the 3-button navigation
 * bar unless the corresponding inset is applied as padding.
 *
 * These helpers read the live inset rather than a hard coded margin, so the same layout works on a
 * device using gesture navigation (~24dp), one using 3-button navigation (~48dp) and one with no
 * navigation bar at all.
 *
 * Every helper records the view's padding the first time it runs and always computes from that
 * baseline, so repeated inset dispatches (rotation, keyboard, split screen) do not accumulate.
 * None of them consume the insets, which leaves sibling views free to apply their own.
 */
object InsetsUtils {

    /** Insets that content must never be drawn under. */
    private val CONTENT_INSETS =
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

    private data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /**
     * Returns the view's padding as it was before any inset was applied to it.
     *
     * The baseline is cached on the view itself via a tag rather than in a map keyed by view, which
     * would hold a strong reference to every view it had ever seen and leak the whole Activity.
     * Caching also makes a second call on the same view idempotent: it re-reads the original XML
     * padding instead of treating the already inset padding as the new baseline.
     */
    private fun baselineOf(view: View): Padding {
        (view.getTag(R.id.insets_baseline_padding) as? Padding)?.let { return it }
        val padding = Padding(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
        view.setTag(R.id.insets_baseline_padding, padding)
        return padding
    }

    /**
     * Applies the bottom system bar inset as bottom padding, on top of whatever bottom padding the
     * view already declares in XML.
     *
     * Use this for a view that sits at the bottom of the screen and should keep its background
     * painted all the way down behind the navigation bar — a [com.google.android.material.bottomnavigation.BottomNavigationView]
     * or a bottom button container, for example.
     */
    @JvmStatic
    fun applyBottomInset(view: View) {
        val baseline = baselineOf(view)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(CONTENT_INSETS)
            v.setPadding(
                baseline.left,
                baseline.top,
                baseline.right,
                baseline.bottom + insets.bottom
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(view)
    }

    /**
     * Applies the bottom system bar inset as bottom padding and turns off clipping, so a scrolling
     * container keeps painting through the navigation bar while its last item can still be scrolled
     * clear of it.
     *
     * Use this for a [androidx.recyclerview.widget.RecyclerView], [android.widget.ScrollView] or
     * [androidx.core.widget.NestedScrollView]. Padding alone is not enough: with the default
     * `clipToPadding="true"` the padded strip is dead space that the content can never scroll into.
     */
    @JvmStatic
    fun applyBottomInsetToScrollable(view: View) {
        if (view is ViewGroup) {
            view.clipToPadding = false
        }
        applyBottomInset(view)
    }

    /**
     * Keeps an Activity's content clear of the navigation bar.
     *
     * Call once per Activity, after `setContentView`. [EspApplication] wires this up for every
     * Activity automatically, so screens do not normally need to call it themselves.
     *
     * Only the content views that Material deliberately over-measures are padded. A root
     * [CoordinatorLayout] marked `fitsSystemWindows` already lays its ordinary children out inside
     * the insets on all four sides, so padding those as well would leave a double gap.
     *
     * The one case it does not cover is a child using
     * `app:layout_behavior="@string/appbar_scrolling_view_behavior"` *alongside an [AppBarLayout]
     * sibling*. Material's `HeaderScrollingViewBehavior` then offsets the child against that header,
     * measuring it taller by the vertical insets and positioning it ignoring the bottom one, on the
     * assumption that the app wants its content to scroll underneath the system bars and will add
     * the bottom padding itself. That assumption is what leaves buttons and last list rows stranded
     * under the navigation bar.
     *
     * Without an [AppBarLayout] sibling the same behavior finds no header to offset against and
     * falls back to CoordinatorLayout's own inset aware layout, which has already applied the bottom
     * inset — so the header has to be present before padding is the right thing to do. Screens like
     * the home screen nest their toolbar deeper in the tree and land in that second case.
     */
    @JvmStatic
    fun applyToActivityContent(activity: Activity) {
        val root = (activity.findViewById<View>(android.R.id.content) as? ViewGroup)
            ?.getChildAt(0) as? CoordinatorLayout ?: return

        val hasHeader = (0 until root.childCount).any { root.getChildAt(it) is AppBarLayout }
        if (!hasHeader) return

        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            val behavior = (child.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior
            if (behavior is AppBarLayout.ScrollingViewBehavior) {
                applyBottomInsetToScrollable(child)
            }
        }
    }

    /**
     * Keeps a dialog above the soft keyboard.
     *
     * Dialog windows are laid out edge to edge on Android 15+ too, and fit only the navigation bar.
     * `SOFT_INPUT_ADJUST_RESIZE` then no longer reaches the window manager: a centred dialog stays
     * where it is and the keyboard slides up over its lower half, which on a shorter screen is the
     * OK and Cancel buttons. Setting the fit types explicitly to the system bars plus the IME puts the
     * dialog back above the keyboard, which is what the window manager used for an adjust-resize
     * window before edge to edge. Older versions still honour
     * `SOFT_INPUT_ADJUST_RESIZE`, so that is set explicitly as well, keeping whatever keyboard
     * visibility state the caller already chose.
     *
     * Call before `show()`. While adding the window, `show()` marks it as navigated forward to, and
     * that mark is what lets `SOFT_INPUT_STATE_VISIBLE` open the keyboard on its own. Changing the
     * window's attributes after `show()` replaces them without the mark, so the keyboard stays
     * closed until the user taps the field.
     */
    @JvmStatic
    fun keepDialogAboveKeyboard(dialog: Dialog) {
        val window = dialog.window ?: return
        val state = window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE
        window.setSoftInputMode(state or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val attrs = window.attributes
            attrs.fitInsetsTypes = attrs.fitInsetsTypes or WindowInsets.Type.ime()
            window.attributes = attrs
        }
    }
}
