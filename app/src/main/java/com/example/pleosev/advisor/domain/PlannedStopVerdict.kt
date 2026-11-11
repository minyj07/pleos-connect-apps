package com.example.pleosev.advisor.domain

import com.example.pleosev.shared.api.FreshnessLevel
import com.example.pleosev.shared.api.StationSummary

/** Live verdict on the charging stop already in the route. */
enum class PlannedStopVerdict {
    OK,
    BUSY,
    UNKNOWN,
    NOT_FOUND,
    NO_MATCHING_CHARGER, // no charger there meets the driver's minimum power: its counts say nothing about free ones
    UNREACHABLE; // nearly empty and the stop is out of range ([Reach]): nearby stations are recommended instead

    companion object {
        /** BUSY also covers a station whose compatible chargers are all out of service. */
        fun of(planned: StationSummary?): PlannedStopVerdict = when {
            planned == null -> NOT_FOUND
            // The server counts only chargers that meet the prefs (minimum power): 0 of 0 is not "full".
            planned.compatible.total == 0 -> NO_MATCHING_CHARGER
            planned.freshness.level == FreshnessLevel.STALE -> UNKNOWN
            planned.compatible.available > 0 -> OK
            planned.compatible.charging == 0 && planned.compatible.unknown > 0 -> UNKNOWN
            else -> BUSY
        }
    }
}
