package com.example.pleosev.advisor.domain

import com.example.pleosev.shared.api.Candidate
import com.example.pleosev.shared.api.ChargerCounts
import com.example.pleosev.shared.api.Freshness
import com.example.pleosev.shared.api.FreshnessLevel
import com.example.pleosev.shared.api.GeoPoint
import com.example.pleosev.shared.api.StationSummary

val GOESAN = GeoPoint(36.8327504, 127.9583201)
val BUSAN_STATION = GeoPoint(35.1151, 129.0422)

fun station(name: String = "괴산휴게소(창원방향)", location: GeoPoint = GOESAN, available: Int = 2, statId: String = "MEFK0202") = StationSummary(
    statId = statId,
    name = name,
    address = "충청북도 괴산군",
    location = location,
    operatorId = "ME",
    operatorName = "환경부",
    compatible = ChargerCounts(total = available + 1, available = available, charging = 1, unknown = 0, outOfService = 0),
    maxKw = 100,
    freshness = Freshness(serverCheckedAtMs = 0L, sourceUpdatedAtMs = 0L, level = FreshnessLevel.LIVE),
)

fun candidate(summary: StationSummary = station()) = Candidate(
    station = summary,
    straightKm = 100.0,
    estRoadKm = 118.0,
    estDetourMin = 4.0,
    estArrivalSocPct = 18.4,
    score = 66.5,
    reasons = emptyList(),
    warnings = emptyList(),
)
