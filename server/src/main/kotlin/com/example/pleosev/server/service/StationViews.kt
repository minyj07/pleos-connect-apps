package com.example.pleosev.server.service

import com.example.pleosev.server.store.ChargerKey
import com.example.pleosev.server.store.ChargerRecord
import com.example.pleosev.server.store.StationRecord
import com.example.pleosev.server.store.StationStore
import com.example.pleosev.server.sync.CoverageTracker
import com.example.pleosev.shared.api.ChargePrefs
import com.example.pleosev.shared.api.ChargerDto
import com.example.pleosev.shared.api.Freshness
import com.example.pleosev.shared.api.StationDetail
import com.example.pleosev.shared.api.StationSummary
import com.example.pleosev.shared.charger.ChargerStates
import com.example.pleosev.shared.charger.ChargerTypes
import com.example.pleosev.shared.charger.FreshnessRules
import java.time.Clock

/** Builds API views of stored stations: compatible-charger counts, power and freshness. */
class StationViews(
    private val store: StationStore,
    private val coverage: CoverageTracker,
    private val clock: Clock,
) {
    fun isCompatible(charger: ChargerRecord, prefs: ChargePrefs): Boolean =
        charger.connectors.any { it in prefs.connectors } && outputKw(charger) >= prefs.minKw

    fun outputKw(charger: ChargerRecord): Int = charger.outputKw ?: ChargerTypes.defaultOutputKw(charger.connectors)

    fun summary(station: StationRecord, prefs: ChargePrefs, nowMs: Long = clock.millis()): StationSummary {
        val compatible = station.chargers.filter { isCompatible(it, prefs) }
        val statuses = compatible.map { store.status(ChargerKey(station.statId, it.chgerId)) }
        val states = statuses.map { ChargerStates.of(it?.stat, it?.statUpdAtMs, nowMs, it?.nowStartAtMs) }
        val checkedAt = coverage.lastSuccessAtMs
        return StationSummary(
            statId = station.statId,
            name = station.name,
            address = station.address,
            location = station.location,
            operatorId = station.operatorId,
            operatorName = station.operatorName,
            useTime = station.useTime,
            parkingFree = station.parkingFree,
            restricted = station.restricted,
            compatible = ChargerStates.count(states),
            maxKw = compatible.maxOfOrNull { outputKw(it) },
            freshness = Freshness(
                serverCheckedAtMs = checkedAt,
                sourceUpdatedAtMs = statuses.mapNotNull { it?.statUpdAtMs }.maxOrNull(),
                level = FreshnessRules.level(checkedAt, nowMs, coverage.coverageOk),
            ),
        )
    }

    fun detail(station: StationRecord, prefs: ChargePrefs, nowMs: Long = clock.millis()) = StationDetail(
        summary = summary(station, prefs, nowMs),
        chargers = station.chargers.map { charger ->
            val status = store.status(ChargerKey(station.statId, charger.chgerId))
            ChargerDto(
                chgerId = charger.chgerId,
                typeCode = charger.typeCode,
                connectors = charger.connectors,
                outputKw = outputKw(charger),
                state = ChargerStates.of(status?.stat, status?.statUpdAtMs, nowMs, status?.nowStartAtMs),
                statUpdAtMs = status?.statUpdAtMs,
                lastStartAtMs = status?.lastStartAtMs,
                lastEndAtMs = status?.lastEndAtMs,
                nowStartAtMs = status?.nowStartAtMs,
            )
        },
        restrictionNote = station.restrictionNote,
        operatorCall = station.operatorCall,
        note = station.note,
    )
}
