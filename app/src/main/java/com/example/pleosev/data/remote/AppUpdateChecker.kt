package com.example.pleosev.data.remote

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Pleos App Market does not update apps by itself: the app checks for a newer release when it starts
 * (once per process, against the relay server, without any data about the car or the driver) and offers
 * the market page (Pleos app update guide).
 */
class AppUpdateChecker(
    private val api: AdvisorApi,
    private val scope: CoroutineScope,
    private val installedVersionCode: Int,
) {
    private val _newerVersionCode = MutableStateFlow<Int?>(null)

    /** The latest market version when it is newer than this build and the driver has not answered yet. */
    val newerVersionCode: StateFlow<Int?> = _newerVersionCode.asStateFlow()

    private var checked = false

    fun checkOnce() {
        if (checked) return
        checked = true
        scope.launch {
            when (val result = api.appVersion()) {
                is ApiResult.Ok -> _newerVersionCode.value = result.value.latestVersionCode?.takeIf { it > installedVersionCode }
                // Retried with the next process start; the app works without it.
                is ApiResult.Failure -> Log.w(TAG, "version check failed: ${result.kind}")
            }
        }
    }

    /** "Yes" or "No" on the prompt: not asked again until the next start. */
    fun answered() {
        _newerVersionCode.value = null
    }

    private companion object {
        const val TAG = "AppUpdateChecker"
    }
}
