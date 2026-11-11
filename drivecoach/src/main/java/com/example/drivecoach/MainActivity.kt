package com.example.drivecoach

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.drivecoach.record.RecorderController
import com.example.drivecoach.ui.DriveCoachRoot
import com.example.drivecoach.ui.MainViewModel
import com.example.drivecoach.ui.theme.DriveCoachTheme

class MainActivity : ComponentActivity() {

    private val app get() = application as DriveCoachApp

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            Log.i(TAG, "permissions: $result")
            app.vehicle.source.onPermissionsChanged()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DriveCoachTheme {
                val vm: MainViewModel = viewModel()
                DriveCoachRoot(
                    vm = vm,
                    onConsentAccepted = {
                        RecorderController.sync(this, app.settings.values.value)
                        requestRuntimePermissions(force = true)
                    },
                    // Store policy (CCP): Back on the top screen hides the app instead of finishing it.
                    onHide = { moveTaskToBack(true) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        app.vehicle.source.acquire()
        val v = app.settings.values.value
        if (v.consented) {
            RecorderController.sync(this, v)
            requestRuntimePermissions(force = false)
        }
    }

    override fun onStop() {
        app.vehicle.source.release()
        super.onStop()
    }

    /**
     * CAR_SPEED is a dangerous AAOS permission (speed is the core signal); notifications are needed for
     * the "report ready" message. Asked only after consent.
     */
    private fun requestRuntimePermissions(force: Boolean) {
        if (app.permissionsAsked && !force) return
        app.permissionsAsked = true
        val wanted = buildList {
            add(CAR_SPEED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = wanted.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            runCatching { requestPermissions.launch(missing.toTypedArray()) }
                .onFailure { Log.w(TAG, "permission request failed: ${it.message}") }
        }
    }

    private companion object {
        const val TAG = "MainActivity"
        const val CAR_SPEED = "android.car.permission.CAR_SPEED"
    }
}
