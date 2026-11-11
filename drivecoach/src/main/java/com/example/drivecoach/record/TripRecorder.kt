package com.example.drivecoach.record

import com.example.drivecoach.analysis.Thresholds
import com.example.drivecoach.analysis.TripAnalyzer
import com.example.drivecoach.analysis.TripSegmenter
import com.example.drivecoach.data.PendingMeta
import com.example.drivecoach.data.TripStore
import com.example.drivecoach.model.EndReason
import com.example.drivecoach.model.Gear
import com.example.drivecoach.model.Sample
import com.example.drivecoach.model.TripSummary
import com.example.drivecoach.vehicle.LiveVehicleState
import com.example.drivecoach.vehicle.SampleListener
import java.util.UUID
import java.util.concurrent.Executor

sealed interface RecorderState {
    /** No consent, recording switched off, or the service is not running. */
    data object Off : RecorderState

    data object Waiting : RecorderState

    data class Recording(val sinceEpochMs: Long) : RecorderState
}

/**
 * Turns the live signal stream into saved trips: segmentation, analysis, crash-safe chunks.
 *
 * Runs on the vehicle source's thread (cheap, O(1) per sample); all file work goes to [io].
 */
class TripRecorder(
    private val store: TripStore,
    private val io: Executor,
    private val clock: Clock,
    private val listener: Listener,
    private val thresholds: Thresholds = Thresholds.DEFAULT,
) : SampleListener, TripSegmenter.Listener {

    interface Clock {
        fun wallMs(): Long
        fun monoMs(): Long
    }

    interface Listener {
        fun onRecordingChanged(state: RecorderState)
        fun onTripSaved(summary: TripSummary)
    }

    private val segmenter = TripSegmenter(this)
    private var analyzer: TripAnalyzer? = null
    private var meta: PendingMeta? = null
    private val chunk = ArrayList<Sample>()
    private var chunkSeq = 0
    private var lastFlushMs = 0L
    private var lastSampleMs = Long.MIN_VALUE
    private var lastGear = Gear.UNKNOWN
    private var latestOdometerKm: Float? = null

    override fun onSample(state: LiveVehicleState) {
        state.odometerKm?.let { latestOdometerKm = it }
        val s = state.toSample() ?: return
        // A demo drive runs ahead of the real clock; when it ends, real samples look "older".
        if (lastSampleMs != Long.MIN_VALUE && s.tMs + BACKWARDS_TOLERANCE_MS < lastSampleMs) segmenter.reset()
        lastSampleMs = s.tMs
        segmenter.add(s)
    }

    /** Periodic check (also right after the head unit wakes up) for trips whose signals stopped. */
    fun tick() = segmenter.tick(clock.monoMs())

    override fun onTripStart(first: Sample, preRoll: List<Sample>) {
        val startT = preRoll.firstOrNull()?.tMs ?: first.tMs
        val startEpoch = clock.wallMs() - (clock.monoMs() - startT).coerceAtLeast(0)
        val m = PendingMeta("$startEpoch-${UUID.randomUUID().toString().take(8)}", startEpoch, startT, latestOdometerKm)
        meta = m
        analyzer = TripAnalyzer(m.id, startEpoch, startT, latestOdometerKm, thresholds)
        chunk.clear()
        chunkSeq = 0
        lastFlushMs = startT
        lastGear = first.gear
        io.execute { runCatching { store.startPending(m) } }
        (preRoll + first).forEach(::add)
        listener.onRecordingChanged(RecorderState.Recording(startEpoch))
    }

    override fun onTripSample(s: Sample) {
        add(s)
        val enteredPark = s.gear == Gear.PARK && lastGear != Gear.PARK
        lastGear = s.gear
        if (s.tMs - lastFlushMs >= FLUSH_INTERVAL_MS || enteredPark || s.ignitionOff) flush(s.tMs)
    }

    override fun onTripEnd(endTMs: Long, reason: EndReason) {
        val a = analyzer ?: return
        val m = meta ?: return
        analyzer = null
        meta = null
        chunk.clear()
        val record = a.finish(endTMs, reason, latestOdometerKm)
        io.execute {
            runCatching {
                if (record != null) {
                    store.save(record)
                    listener.onTripSaved(record.summary)
                }
            }
            runCatching { store.dropPending(m.id) }
        }
        listener.onRecordingChanged(RecorderState.Waiting)
    }

    /** The service is stopping: save what is buffered; the trip is recovered next time. */
    fun close() {
        if (meta != null) flush(lastSampleMs)
    }

    /** Finishes trips cut off by a process death by replaying their saved samples. */
    fun recoverPending() {
        io.execute {
            val pending = runCatching { store.pendingTrips() }.getOrDefault(emptyList())
            for (p in pending) {
                val a = TripAnalyzer(p.meta.id, p.meta.startEpochMs, p.meta.startTMs, p.meta.odoStartKm, thresholds)
                p.samples.forEach(a::add)
                val end = p.samples.lastOrNull()?.tMs ?: p.meta.startTMs
                runCatching {
                    a.finish(end, EndReason.RECOVERED)?.let {
                        store.save(it)
                        listener.onTripSaved(it.summary)
                    }
                }
                runCatching { store.dropPending(p.meta.id) }
            }
        }
    }

    private fun add(s: Sample) {
        analyzer?.add(s)
        chunk += s
    }

    private fun flush(nowT: Long) {
        val m = meta ?: return
        lastFlushMs = nowT
        if (chunk.isEmpty()) return
        val batch = ArrayList(chunk)
        chunk.clear()
        val seq = chunkSeq++
        io.execute { runCatching { store.appendPending(m.id, seq, batch) } }
    }

    private companion object {
        const val FLUSH_INTERVAL_MS = 60_000L
        const val BACKWARDS_TOLERANCE_MS = 1_000L
    }
}
