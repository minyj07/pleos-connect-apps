package com.example.pleosev.advisor.background

import android.content.Intent
import com.example.pleosev.advisor.StationRef
import com.example.pleosev.advisor.domain.NaviActionKind
import com.example.pleosev.shared.api.GeoPoint

/** The station and action of an alert button, carried in its intent. */
internal object AlertExtras {

    private const val STAT_ID = "stat_id"
    private const val NAME = "name"
    private const val LAT = "lat"
    private const val LON = "lon"
    private const val ADDRESS = "address"
    private const val KIND = "kind"

    fun put(intent: Intent, station: StationRef, kind: NaviActionKind): Intent = intent
        .putExtra(STAT_ID, station.statId)
        .putExtra(NAME, station.name)
        .putExtra(LAT, station.location.lat)
        .putExtra(LON, station.location.lon)
        .putExtra(ADDRESS, station.address)
        .putExtra(KIND, kind.name)

    fun station(intent: Intent): StationRef? {
        val statId = intent.getStringExtra(STAT_ID) ?: return null
        val name = intent.getStringExtra(NAME) ?: return null
        if (!intent.hasExtra(LAT) || !intent.hasExtra(LON)) return null
        val location = GeoPoint(intent.getDoubleExtra(LAT, 0.0), intent.getDoubleExtra(LON, 0.0))
        return StationRef(statId, name, location, intent.getStringExtra(ADDRESS).orEmpty())
    }

    fun kind(intent: Intent): NaviActionKind? =
        intent.getStringExtra(KIND)?.let { name -> NaviActionKind.entries.firstOrNull { it.name == name } }
}
