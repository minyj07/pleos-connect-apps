package com.example.drivecoach

import android.app.Application
import com.example.drivecoach.data.AeadCodec
import com.example.drivecoach.data.AndroidKeystoreKeyProvider
import com.example.drivecoach.data.Settings
import com.example.drivecoach.data.ShareStore
import com.example.drivecoach.data.TripStore
import com.example.drivecoach.record.RecorderState
import com.example.drivecoach.share.HttpShareApi
import com.example.drivecoach.share.ShareLinkConfig
import com.example.drivecoach.share.ShareManager
import com.example.drivecoach.vehicle.VehicleEnvironment
import com.example.drivecoach.vehicle.VehicleSourceFactory
import com.example.pleos.core.car.UxRestrictionsMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** Process-wide singletons: one vehicle connection, one trip store, one IO thread. */
class DriveCoachApp : Application() {

    lateinit var settings: Settings
        private set
    lateinit var tripStore: TripStore
        private set
    lateinit var shares: ShareManager
        private set
    lateinit var vehicle: VehicleEnvironment
        private set
    lateinit var ux: UxRestrictionsMonitor
        private set

    /** All file and crypto work, in order. */
    val io: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "drivecoach-io") }

    /** Network work ("폰으로 보기"): outlives screens, never blocks [io] with a slow server. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val recorderState = MutableStateFlow<RecorderState>(RecorderState.Off)

    /** Runtime permissions are asked once per process, not on every return to the app. */
    var permissionsAsked = false

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        tripStore = TripStore(filesDir, AeadCodec(AndroidKeystoreKeyProvider()))
        val shareStore = ShareStore(File(filesDir, "shares/index"), AeadCodec(AndroidKeystoreKeyProvider("drivecoach.shares.v1")))
        shares = ShareManager(
            api = HttpShareApi(BuildConfig.SHARE_BASE_URL),
            store = shareStore,
            config = ShareLinkConfig(
                uploadBaseUrl = BuildConfig.SHARE_BASE_URL,
                viewBaseUrl = BuildConfig.SHARE_VIEW_BASE_URL.ifBlank { BuildConfig.SHARE_BASE_URL },
                trustServerViewUrl = BuildConfig.SHARE_TRUST_SERVER_VIEW_URL,
            ),
            loadTrip = { id -> onIo { tripStore.record(id) } },
            summaries = { tripStore.summaries.value },
        )
        io.execute {
            // Separately: a failing trip prune must not keep the link list (delete tokens) unread.
            runCatching { shareStore.load(System.currentTimeMillis()) }
            runCatching {
                tripStore.load()
                tripStore.prune(System.currentTimeMillis(), settings.values.value.retentionDays)
            }
            // Deletes that didn't reach the server last time (offline, or the app was closed).
            appScope.launch { runCatching { shares.flushDeletes() } }
        }
        vehicle = VehicleSourceFactory.create(this)
        ux = UxRestrictionsMonitor(this).also { it.start() }
    }

    /** Runs [block] on the [io] thread (file and crypto order) and resumes with its result, or null if it threw. */
    suspend fun <T> onIo(block: () -> T): T? = suspendCancellableCoroutine { cont ->
        io.execute { cont.resume(runCatching(block).getOrNull()) }
    }
}
