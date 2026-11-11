package com.example.drivecoach.data

import com.example.drivecoach.model.EndReason
import com.example.drivecoach.model.Gear
import com.example.drivecoach.model.HabitStats
import com.example.drivecoach.model.Sample
import com.example.drivecoach.model.TripKind
import com.example.drivecoach.model.TripRecord
import com.example.drivecoach.model.TripSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class TripStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Stand-in for the Android Keystore in JVM tests. */
    private class SoftwareKeyProvider : KeyProvider {
        var key: SecretKey? = null
        override fun key(): SecretKey = key ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }
        override fun destroy() {
            key = null
        }
    }

    private fun record(id: String, start: Long) = TripRecord(
        summary = TripSummary(id, start, start + 600_000, TripKind.DRIVE, HabitStats(distanceKm = 5f), endReason = EndReason.PARKED, score = 90),
        events = emptyList(),
        timeline = emptyList(),
    )

    @Test
    fun savesAndLoadsEncryptedTrips() {
        val keys = SoftwareKeyProvider()
        val store = TripStore(tmp.root, AeadCodec(keys))
        store.save(record("a", 1_000))
        store.save(record("b", 2_000))
        assertEquals(listOf("b", "a"), store.summaries.value.map { it.id })
        assertEquals(90, store.record("a")!!.summary.score)

        // Nothing readable on disk
        val raw = File(tmp.root, "trips/a.trip").readBytes()
        assertFalse(String(raw).contains("\"score\""))

        // A fresh instance with the same key sees the same index
        val reopened = TripStore(tmp.root, AeadCodec(keys)).apply { load() }
        assertEquals(2, reopened.summaries.value.size)
    }

    @Test
    fun swappedFilesDoNotDecrypt() {
        val store = TripStore(tmp.root, AeadCodec(SoftwareKeyProvider()))
        store.save(record("a", 1_000))
        store.save(record("b", 2_000))
        File(tmp.root, "trips/a.trip").copyTo(File(tmp.root, "trips/b.trip"), overwrite = true)
        assertNull(store.record("b")) // bound to its id by the AAD
    }

    @Test
    fun pendingChunksSurviveAndCanBeRecovered() {
        val store = TripStore(tmp.root, AeadCodec(SoftwareKeyProvider()))
        store.startPending(PendingMeta("p1", 5_000, 100, odoStartKm = 12f))
        store.appendPending("p1", 0, listOf(Sample(100, 1f, Gear.DRIVE), Sample(200, 2f, Gear.DRIVE)))
        store.appendPending("p1", 1, listOf(Sample(300, 3f, Gear.DRIVE)))
        val pending = store.pendingTrips().single()
        assertEquals(12f, pending.meta.odoStartKm)
        assertEquals(listOf(100L, 200L, 300L), pending.samples.map { it.tMs })
        store.dropPending("p1")
        assertTrue(store.pendingTrips().isEmpty())
    }

    @Test
    fun pruneAndWipe() {
        val keys = SoftwareKeyProvider()
        val store = TripStore(tmp.root, AeadCodec(keys))
        val day = 24 * 60 * 60 * 1000L
        val now = 100 * day
        store.save(record("old", now - 40 * day))
        store.save(record("new", now - 2 * day))
        store.prune(now, retentionDays = 30)
        assertEquals(listOf("new"), store.summaries.value.map { it.id })
        assertFalse(File(tmp.root, "trips/old.trip").exists())

        store.wipe()
        assertTrue(store.summaries.value.isEmpty())
        assertFalse(File(tmp.root, "trips").exists())
        assertNull(keys.key) // crypto-shredded
    }
}
