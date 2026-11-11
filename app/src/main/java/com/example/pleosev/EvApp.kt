package com.example.pleosev

import android.app.Application
import android.content.Context
import com.example.pleosev.di.AppGraph

/** Holds the process-wide [AppGraph]: vehicle and navi connections outlive individual screens. */
class EvApp : Application() {

    /** Registered before any activity starts: the advisor's background watch starts only while the app is on screen. */
    val visibility = AppVisibility()

    val graph: AppGraph by lazy { AppGraph(this, visibility) }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(visibility)
    }
}

val Context.appGraph: AppGraph get() = (applicationContext as EvApp).graph
