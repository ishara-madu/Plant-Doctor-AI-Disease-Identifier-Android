package com.pixeleye.plantdoctor.utils

import android.util.Log
import java.util.concurrent.atomic.AtomicLong

/**
 * Prevents rapid duplicate navigation calls that can cause visual glitches or crashes.
 *
 * Usage:
 * ```
 * if (NavigationDebouncer.canNavigate()) {
 *     navController.navigate("destination")
 * }
 * ```
 */
object NavigationDebouncer {
    private const val TAG = "NavigationDebouncer"
    private const val DEBOUNCE_MS = 600L // Minimum time between navigation actions

    private val lastNavigationTime = AtomicLong(0L)

    /**
     * Checks if enough time has passed since the last navigation to allow a new one.
     * Automatically updates the timestamp if navigation is allowed.
     *
     * @return true if navigation is allowed, false if it should be skipped.
     */
    fun canNavigate(): Boolean {
        val now = System.currentTimeMillis()
        val last = lastNavigationTime.get()

        return if (now - last >= DEBOUNCE_MS) {
            lastNavigationTime.set(now)
            true
        } else {
            Log.d(TAG, "Navigation debounced (last: $last, now: $now)")
            false
        }
    }

    /** Resets the debounce timer (e.g., when returning to a fresh state). */
    fun reset() {
        lastNavigationTime.set(0L)
    }
}
