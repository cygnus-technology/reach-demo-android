package com.reach_android.ui

/**
 * A fragment that controls its own behavior when the Android system back button is pressed.
 * In order for this to work, the parent activity must override onBackPressed and call
 * the currently presented [ConditionalBackFragment] instance's onBackPressed function
 */
interface ConditionalBackFragment {

    /**
     * Called by the parent activity's onBackPressed function override
     *
     * @return true if activity should navigate back, false if not
     */
    fun onBackPressed(): Boolean = true

    /**
     * Called by the parent activity's onSupportNavigateUp function override
     *
     * @return true if activity should navigate up, false if not
     */
    fun onUpPressed(): Boolean = true
}