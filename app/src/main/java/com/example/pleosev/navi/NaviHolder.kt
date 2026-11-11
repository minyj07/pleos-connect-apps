package com.example.pleosev.navi

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Process-wide navi access. Uses [primary] (NaviHelper); in DEBUG builds, when the Navi Service is
 * unavailable (no stock navi, e.g. a plain emulator), switches once to [demoFactory] so the advisor UI
 * can still be developed. Mirrors VehicleStateHolder's demo fallback.
 */
class NaviHolder(
    private val primary: NaviRepository,
    private val scope: CoroutineScope,
    private val demoFactory: (() -> NaviRepository)?,
) : NaviRepository {

    @Volatile private var current: NaviRepository = primary
    private var followJob: Job? = null
    private var started = false

    private val _state = MutableStateFlow(NaviState())
    override val state: StateFlow<NaviState> = _state.asStateFlow()

    override fun start() {
        if (started) return
        started = true
        follow(primary)
        primary.start()
    }

    private fun follow(repo: NaviRepository) {
        followJob?.cancel()
        followJob = scope.launch {
            repo.state.collect { s ->
                val factory = demoFactory
                if (repo === primary && s.connection == NaviConnection.UNAVAILABLE && factory != null) {
                    Log.w(TAG, "Navi Service unavailable (${s.lastError}); switching to demo navi")
                    primary.stop()
                    val demo = factory()
                    current = demo
                    demo.start()
                    follow(demo)
                    return@collect
                }
                _state.value = s
            }
        }
    }

    override fun stop() = current.stop()

    override fun refresh() = current.refresh()

    override suspend fun estimateTrip(target: NaviTarget) = current.estimateTrip(target)

    override fun routeTo(target: NaviTarget) = current.routeTo(target)

    override fun addWaypoint(target: NaviTarget, slot: WaypointSlot) = current.addWaypoint(target, slot)

    override fun removeWaypoint(slot: WaypointSlot) = current.removeWaypoint(slot)

    override fun searchInNavi(keyword: String) = current.searchInNavi(keyword)

    override fun showNearbyChargers() = current.showNearbyChargers()

    private companion object {
        const val TAG = "NaviHolder"
    }
}
