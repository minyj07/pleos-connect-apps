package com.example.pleosev

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.pleosev.advisor.background.NaviActionActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether one of the app's activities is on screen (started); on Pleos the navi covers it otherwise. */
class AppVisibility : Application.ActivityLifecycleCallbacks {

    private var started = 0
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    override fun onActivityStarted(activity: Activity) {
        if (activity is NaviActionActivity) return // a 0.1 s trampoline to the navi, not the app on screen
        started++
        _visible.value = true
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity is NaviActionActivity) return
        started = (started - 1).coerceAtLeast(0)
        // A relaunch for a configuration change (day/night, window size) stops the old instance before the new
        // one starts: the app never left the screen.
        if (activity.isChangingConfigurations) return
        _visible.value = started > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
