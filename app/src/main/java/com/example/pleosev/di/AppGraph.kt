package com.example.pleosev.di

import android.app.Application
import android.content.Intent
import android.util.Log
import com.example.pleos.core.car.UxRestrictionsMonitor
import com.example.pleosev.AppVisibility
import com.example.pleosev.BuildConfig
import com.example.pleosev.advisor.AdvisorMonitor
import com.example.pleosev.advisor.ChargingAdvisor
import com.example.pleosev.advisor.background.AdvisorNotifier
import com.example.pleosev.data.VehicleStateHolder
import com.example.pleosev.data.remote.AppUpdateChecker
import com.example.pleosev.data.remote.KtorAdvisorApi
import com.example.pleosev.data.settings.AdvisorSettings
import com.example.pleosev.navi.FakeNaviRepository
import com.example.pleosev.navi.NaviHolder
import com.example.pleosev.navi.NaviRepository
import com.example.pleosev.navi.PleosNaviRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide objects, created once by EvApp. The vehicle and navi connections and the charging
 * advisor live as long as the process (not a screen), so the advisor keeps watching while the navi covers
 * the app; AdvisorMonitor keeps the process alive for that (background watch) and posts the alerts.
 */
class AppGraph(private val app: Application, visibility: AppVisibility) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val uxRestrictions = UxRestrictionsMonitor(app).also { it.start() }

    val vehicle = VehicleStateHolder(app, scope, uxRestrictions).also { it.start() }

    val navi: NaviRepository = NaviHolder(
        primary = PleosNaviRepository(app, ::bringNaviToFront),
        scope = scope,
        demoFactory = if (BuildConfig.DEBUG) ::FakeNaviRepository else null,
    ).also { it.start() }

    val settings = AdvisorSettings(app)

    private val api = KtorAdvisorApi(BuildConfig.ADVISOR_BASE_URL)

    val advisor = ChargingAdvisor(
        scope = scope,
        vehicle = vehicle.state,
        navi = navi,
        prefs = settings.prefs,
        api = api,
    ).also { it.start() }

    val updates = AppUpdateChecker(api, scope, BuildConfig.VERSION_CODE)

    val monitor = AdvisorMonitor(
        app = app,
        scope = scope,
        advisor = advisor,
        vehicle = vehicle.state,
        navi = navi.state,
        prefs = settings.prefs,
        visible = visibility.visible,
        notifier = AdvisorNotifier(app),
    ).also { it.start() }

    /** Shows the default navigation app; Pleos navigation apps declare CATEGORY_APP_MAPS. */
    private fun bringNaviToFront() {
        val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }.onFailure { Log.w(TAG, "cannot show the navi: ${it.message}") }
    }

    private companion object {
        const val TAG = "AppGraph"
    }
}
