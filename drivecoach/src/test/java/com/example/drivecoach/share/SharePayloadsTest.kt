package com.example.drivecoach.share

import com.example.drivecoach.analysis.PeriodSummary
import com.example.drivecoach.analysis.SyntheticDrive
import com.example.drivecoach.model.EndReason
import com.example.drivecoach.model.EventType
import com.example.drivecoach.model.HabitStats
import com.example.drivecoach.model.ParkingSession
import com.example.drivecoach.model.TripKind
import com.example.drivecoach.model.TripRecord
import com.example.drivecoach.model.TripSummary
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharePayloadsTest {

    private val now = 1_800_000_000_000L
    private val day = PeriodSummary.DAY_MS

    /** Harsh start, a steady stretch, then a hard stop with the brake pressed. */
    private fun cityTrip(holdSeconds: Double = 120.0): TripRecord {
        val d = SyntheticDrive()
        d.ramp(50.0, 4.0).hold(holdSeconds)
        d.brake = true
        d.ramp(0.0, 4.0)
        d.brake = false
        d.hold(2.0)
        return assertNotNull(SyntheticDrive.analyze(d.samples))
    }

    @Test
    fun tripPayloadCarriesTheReport() {
        val record = cityTrip()
        val t = assertNotNull(SharePayloads.trip(record, now, 540).trip)
        assertTrue(t.drive)
        assertEquals(record.summary.stats.distanceKm, t.distanceKm, 0.05f)
        assertEquals(record.events.size, t.moments.size)
        assertTrue(t.events.keys.all { k -> EventType.entries.any { it.name == k } }, "event keys are EventType names: ${t.events}")
        assertTrue(t.moments.any { it.type == EventType.HARD_STOP.name || it.type == EventType.HARSH_BRAKE.name })
        assertNotNull(t.brake)
        assertTrue(t.tips.isNotEmpty())
    }

    @Test
    fun tripPayloadLeavesOutOdometerSteeringAndIds() {
        val base = cityTrip()
        val record = base.copy(summary = base.summary.copy(id = "trip-7f3a9c", odoStartKm = 12345.6f, odoEndKm = 12347.2f))
        val json = SharePayloads.encode(SharePayloads.trip(record, now, 540)).toString(Charsets.UTF_8)
        for (secret in listOf("trip-7f3a9c", "12345", "odo", "steer")) assertFalse(secret in json, "payload contains $secret")
    }

    @Test
    fun timelineIsCappedAndKeepsBrakeMarks() {
        val record = cityTrip(holdSeconds = 1_500.0)
        assertTrue(record.timeline.size > SharePayloads.MAX_TIMELINE_POINTS)
        val tl = assertNotNull(SharePayloads.trip(record, now, 0).trip?.timeline)
        assertTrue(tl.tS.size <= SharePayloads.MAX_TIMELINE_POINTS)
        assertEquals(tl.tS.size, tl.kmh.size)
        assertEquals(tl.tS.size, tl.brake?.size)
        assertTrue(tl.brake!!.contains(1), "the final stop is braked")
        assertEquals(50, tl.kmh.max(), "mean of each bucket stays on the 50 km/h plateau")
    }

    @Test
    fun periodCountsRecentDrivesNewestFirstAndManoeuvreParking() {
        val recent = summary("a", now - 1 * day, TripKind.DRIVE, parking = parking(reverse = true))
        val newer = summary("b", now - 3_600_000L, TripKind.DRIVE)
        val manoeuvre = summary("c", now - 2 * day, TripKind.MANEUVER, parking = parking(reverse = false))
        val old = summary("d", now - 40 * day, TripKind.DRIVE)
        val p = assertNotNull(SharePayloads.period(listOf(recent, manoeuvre, old, newer), 30, now, 540).period)
        assertEquals(2, p.trips)
        assertEquals(listOf(newer.startEpochMs, recent.startEpochMs), p.drives.map { it.startEpochMs })
        assertEquals(2, p.parking?.sessions)
        assertEquals(0.5f, p.parking?.reverseShare)
        assertEquals(2, p.events[EventType.HARD_STOP.name])
        assertEquals(EventType.entries.size, p.per10Km.size)
        assertEquals(5, p.weeks.size)
    }

    @Test
    fun payloadSurvivesSealingAndDecoding() {
        val payload = SharePayloads.trip(cityTrip(), now, 540)
        val sealed = ShareCrypto.seal(SharePayloads.encode(payload))
        assertEquals(payload, SharePayloads.decode(ShareCrypto.open(sealed.blob, sealed.key)))
    }

    /** Writes fixed test vectors for checking the phone viewer in a browser (share-server, docs/DRIVECOACH.md). */
    @Test
    fun writeViewerTestVectors() {
        val dir = File("build/share-fixture").apply { mkdirs() }
        val trip = SharePayloads.trip(cityTrip(holdSeconds = 600.0), now, 540)
        val period = SharePayloads.period(
            listOf(summary("a", now - day, TripKind.DRIVE, parking = parking(true)), summary("b", now - 9 * day, TripKind.DRIVE)),
            30, now, 540,
        )
        for ((name, payload) in listOf("trip" to trip, "period" to period)) {
            val sealed = ShareCrypto.seal(SharePayloads.encode(payload), FixedRandom())
            File(dir, "$name.bin").writeBytes(sealed.blob)
            File(dir, "$name.key").writeText(ShareLink.base64Url(sealed.key))
            File(dir, "$name.json").writeBytes(SharePayloads.encode(payload))
        }
        assertTrue(File(dir, "trip.bin").length() > 0)
    }

    private fun summary(id: String, start: Long, kind: TripKind, parking: ParkingSession? = null) = TripSummary(
        id = id,
        startEpochMs = start,
        endEpochMs = start + 900_000L,
        kind = kind,
        stats = HabitStats(distanceKm = if (kind == TripKind.DRIVE) 12f else 0.1f, durationS = 900f, movingS = 800f, brakeKnown = true, brakePresses = 6),
        events = if (kind == TripKind.DRIVE) mapOf(EventType.HARD_STOP to 1) else emptyMap(),
        score = if (kind == TripKind.DRIVE) 80 else null,
        parking = parking,
        endReason = EndReason.PARKED,
    )

    private fun parking(reverse: Boolean) = ParkingSession(durationS = 40f, distanceM = 12f, directionChanges = 3, stops = 2, maxSteerPct = 95, endedInReverse = reverse)
}
