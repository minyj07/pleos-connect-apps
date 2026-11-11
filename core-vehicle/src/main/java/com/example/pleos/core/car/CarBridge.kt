package com.example.pleos.core.car

/**
 * Small seam so facades never name `android.car` types in their own bytecode.
 *
 * The `android.car` classes exist only on Android Automotive builds. A facade builds its `Aaos*Bridge`
 * (the only class that references them) after a FEATURE_AUTOMOTIVE check and inside a Throwable catch,
 * because class loading failures are Errors (NoClassDefFoundError), not Exceptions.
 */
interface CarBridge {
    fun start()
    fun stop()
}
