package com.motorider.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.motorider.MainActivity
import com.motorider.R
import com.motorider.data.repository.LocationRepository
import com.motorider.data.repository.SettingsRepository
import com.motorider.ui.theme.DialBackground
import com.motorider.ui.theme.DialLime
import com.motorider.ui.theme.DialTrack
import com.motorider.ui.theme.NeonOrange
import com.motorider.ui.theme.NeonRed
import com.motorider.ui.theme.SurfaceDark
import com.motorider.ui.theme.TextPrimary
import com.motorider.ui.theme.TextSecondary
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

/** Dial / progress fill scale — motorcycle top speed. */
private const val MOTORCYCLE_MAX_MPH = 33.0

@AndroidEntryPoint
class OverlayService : LifecycleService(), ViewModelStoreOwner, SavedStateRegistryOwner {

    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private val windowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private var overlayRoot: FrameLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private val overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Backing fields with unique names to avoid clashes
    private val _serviceViewModelStore = ViewModelStore()
    private val _serviceSavedStateRegistryController = SavedStateRegistryController.create(this)

    override val viewModelStore: ViewModelStore
        get() = _serviceViewModelStore

    override val savedStateRegistry: SavedStateRegistry
        get() = _serviceSavedStateRegistryController.savedStateRegistry

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "motorider_overlay_channel"

        const val ACTION_START = "com.motorider.action.OVERLAY_START"
        const val ACTION_STOP = "com.motorider.action.OVERLAY_STOP"

        /** True after [promoteToForeground] â€” set synchronously on each FGS start. */
        @Volatile
        var isForegroundActive: Boolean = false
            private set

        /** True while overlay window is attached. */
        @Volatile
        var isOverlayActive: Boolean = false
            private set
        
        private const val MIN_WIDTH_DP = 180
        private const val MIN_HEIGHT_DP = 240
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {
        super.onCreate()
        _serviceSavedStateRegistryController.performRestore(null)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // startService(STOP) on a fresh instance still counts as an FGS start on API 34+.
                promoteToForeground()
                super.onStartCommand(intent, flags, startId)
                stopOverlay()
                return START_NOT_STICKY
            }
        }
        // Must run before super and before any async work â€” satisfies every startForegroundService().
        promoteToForeground()
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startOverlay()
            else -> {
                android.util.Log.w(
                    "OverlayService",
                    "onStartCommand without action; foreground only (intent=$intent)"
                )
            }
        }
        return START_NOT_STICKY
    }

    private fun promoteToForeground() {
        startForegroundTyped(
            NOTIFICATION_ID,
            createNotification("Overlay"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        isForegroundActive = true
    }

    private fun startOverlay() {
        if (overlayRoot != null) return

        if (!Settings.canDrawOverlays(this)) {
            android.util.Log.e("OverlayService", "Cannot show overlay: Permission not granted")
            isForegroundActive = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        overlayScope.launch {
            val density = resources.displayMetrics.density
            val dpX = settingsRepository.overlayX.first()
            val dpY = settingsRepository.overlayY.first()
            val dpWidth = settingsRepository.overlayWidth.first()
            val dpHeight = settingsRepository.overlayHeight.first()
            attachOverlayWindow(dpX, dpY, dpWidth, dpHeight, density)
        }
    }

    private fun attachOverlayWindow(
        dpX: Double,
        dpY: Double,
        dpWidth: Double,
        dpHeight: Double,
        density: Float
    ) {
        if (overlayRoot != null) return

        val initialX = (dpX * density).toInt()
        val initialY = (dpY * density).toInt()
        val initialWidth = (dpWidth * density).toInt()
        val initialHeight = (dpHeight * density).toInt()

        android.util.Log.d("OverlayService", "Starting overlay at ($initialX, $initialY) with size ${initialWidth}x${initialHeight} (Density: $density)")

        val root = FrameLayout(this).apply {
            // Set owners on the root of the window tree
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
        }
        
        val params = WindowManager.LayoutParams(
            initialWidth,
            initialHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        // Track window width as Compose state so font scales on resize
        val overlayWidthDp = androidx.compose.runtime.mutableFloatStateOf(dpWidth.toFloat())

        val composeView = ComposeView(this).apply {
            // Also set them here for good measure
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            
            setContent {
                OverlayWindow(
                    locationRepository = locationRepository,
                    settingsRepository = settingsRepository,
                    overlayWidthDp = overlayWidthDp.floatValue,
                    onClose = { stopOverlay() },
                    onMove = { dx, dy ->
                        params.x += dx.roundToInt()
                        params.y += dy.roundToInt()
                        windowManager.updateViewLayout(root, params)
                    },
                    onResize = { dw, dh ->
                        val minWidthPx = (MIN_WIDTH_DP * density).toInt()
                        val minHeightPx = (MIN_HEIGHT_DP * density).toInt()

                        params.width = max(minWidthPx, params.width + dw.roundToInt())
                        params.height = max(minHeightPx, params.height + dh.roundToInt())
                        windowManager.updateViewLayout(root, params)
                        overlayWidthDp.floatValue = params.width.toFloat() / density
                    },
                    onActionFinished = {
                        val currentDensity = resources.displayMetrics.density
                        settingsRepository.setOverlayBounds(
                            params.x.toDouble() / currentDensity,
                            params.y.toDouble() / currentDensity,
                            params.width.toDouble() / currentDensity,
                            params.height.toDouble() / currentDensity
                        )
                    }
                )
            }
        }
        root.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        try {
            windowManager.addView(root, params)
            overlayRoot = root
            overlayParams = params
            isOverlayActive = true
            android.util.Log.d("OverlayService", "Overlay window successfully added to WindowManager")
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error adding overlay window", e)
        }
    }

    private fun stopOverlay() {
        overlayRoot?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        overlayRoot = null
        overlayParams = null
        isOverlayActive = false
        isForegroundActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        overlayScope.cancel()
        removeOverlayWindowOnly()
        isForegroundActive = false
        super.onDestroy()
    }

    /** Detach the floating window without tearing down the service (used from [onDestroy]). */
    private fun removeOverlayWindowOnly() {
        overlayRoot?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayRoot = null
        overlayParams = null
        isOverlayActive = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Overlay UI for speed and radio controls"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

@Composable
private fun CameraWarningSideBars() {
    val infiniteTransition = rememberInfiniteTransition(label = "cameraFlash")
    val warnAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(350),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cameraBarAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(6.dp)
                .alpha(warnAlpha)
                .background(NeonRed, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(6.dp)
                .alpha(warnAlpha)
                .background(NeonRed, RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp))
        )
    }
}

@Composable
private fun OverlaySpeedDial(
    speed: Double,
    unitLabel: String,
    roadLimit: Double?,
    ringColor: Color,
    speedFraction: Float,
    dialSizeDp: Float
) {
    val animatedFraction by animateFloatAsState(
        targetValue = speedFraction.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "dialProgress"
    )

    // Glance-first: large digits; bike tops out at ~33 mph so 2 digits stay huge.
    val speedSize = (dialSizeDp * 0.40f).coerceIn(52f, 100f).sp
    val unitSize = (dialSizeDp * 0.07f).coerceIn(11f, 18f).sp
    val limitSize = (dialSizeDp * 0.22f).coerceIn(36f, 56f).dp
    val strokeDp = (dialSizeDp * 0.05f).coerceIn(8f, 14f).dp

    Box(
        modifier = Modifier.size(dialSizeDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeDp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)
            val startAngle = 135f
            val maxSweep = 270f

            drawArc(
                color = DialTrack,
                startAngle = startAngle,
                sweepAngle = maxSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = ringColor,
                startAngle = startAngle,
                sweepAngle = maxSweep * animatedFraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }

        // Speed sits slightly above center so the limit badge has room in the arc gap.
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = limitSize * 0.55f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${speed.toInt()}",
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = speedSize,
                letterSpacing = (-1).sp,
                maxLines = 1
            )
            Text(
                text = unitLabel,
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = unitSize
            )
        }

        // Road speed-limit sign — pinned in the open bottom of the dial (never clipped by digits).
        if (roadLimit != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
                    .size(limitSize),
                shape = CircleShape,
                color = Color.White,
                border = BorderStroke(3.dp, Color.Red),
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${Math.round(roadLimit).toInt()}",
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = (limitSize.value * 0.45f).sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun DialMediaButton(
    onClick: () -> Unit,
    emphasized: Boolean = false,
    content: @Composable () -> Unit
) {
    val size = if (emphasized) 52.dp else 44.dp
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = SurfaceDark,
        modifier = Modifier.size(size),
        border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.25f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun OverlayWindow(
    locationRepository: LocationRepository,
    settingsRepository: SettingsRepository,
    overlayWidthDp: Float,
    onClose: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
    onActionFinished: () -> Unit
) {
    val speedKmh by locationRepository.currentSpeed.collectAsState()
    val useMph by settingsRepository.useMph.collectAsState()
    val stations by settingsRepository.radioStations.collectAsState()
    val selectedId by settingsRepository.selectedStationId.collectAsState()
    val selectedStation = stations.firstOrNull { it.id == selectedId } ?: stations.firstOrNull()
    val hudMode by settingsRepository.hudMode.collectAsState()
    val cameraDistM by locationRepository.nearestCameraDistance.collectAsState()
    val camerasEnabled by settingsRepository.speedCamerasEnabled.collectAsState()
    val roadLimitKmh by locationRepository.roadSpeedLimit.collectAsState()

    val showRadio = hudMode == SettingsRepository.HudMode.SPEED_RADIO ||
        hudMode == SettingsRepository.HudMode.FULL_HUD
    val showAlerts = hudMode == SettingsRepository.HudMode.SPEED_ALERTS ||
        hudMode == SettingsRepository.HudMode.FULL_HUD

    var isPlaying by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val displaySpeed = if (useMph) speedKmh * 0.621371 else speedKmh
    val unitLabel = if (useMph) "mph" else "km/h"
    val displayRoadLimit = roadLimitKmh?.let { if (useMph) it * 0.621371 else it }

    // Motorcycle max ~33 mph — ring fills at that speed (convert if showing km/h).
    val bikeMaxSpeed = if (useMph) MOTORCYCLE_MAX_MPH else MOTORCYCLE_MAX_MPH / 0.621371
    val speedFraction = (displaySpeed / bikeMaxSpeed).toFloat().coerceIn(0f, 1f)

    val limitValue = displayRoadLimit
    val overLimit = limitValue != null && displaySpeed > limitValue
    val ringColor = when {
        limitValue != null && displaySpeed > limitValue * 1.1 -> NeonRed
        overLimit -> NeonOrange
        else -> DialLime
    }

    val dialSize = (overlayWidthDp * 0.84f).coerceIn(160f, 260f)

    val dragModifier = Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDrag = { change, dragAmount ->
                change.consume()
                onMove(dragAmount.x, dragAmount.y)
            },
            onDragEnd = { onActionFinished() }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        color = DialBackground,
        shadowElevation = 10.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .then(dragModifier)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            val radioIntent = Intent(context, RadioPlayerService::class.java).apply {
                                action = RadioPlayerService.ACTION_STOP
                            }
                            val overlayIntent = Intent(context, OverlayService::class.java).apply {
                                action = OverlayService.ACTION_STOP
                            }
                            val locIntent = Intent(
                                context,
                                LocationForegroundService::class.java
                            ).apply {
                                action = LocationForegroundService.ACTION_STOP
                            }
                            context.startService(radioIntent)
                            context.startService(overlayIntent)
                            context.startService(locIntent)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Stop All",
                            modifier = Modifier.size(18.dp),
                            tint = NeonRed
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(3.dp)
                            .background(
                                TextSecondary.copy(alpha = 0.35f),
                                RoundedCornerShape(2.dp)
                            )
                    )

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close HUD",
                            modifier = Modifier.size(18.dp),
                            tint = TextSecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .then(dragModifier)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            OverlaySpeedDial(
                                speed = displaySpeed,
                                unitLabel = unitLabel,
                                roadLimit = displayRoadLimit,
                                ringColor = ringColor,
                                speedFraction = speedFraction,
                                dialSizeDp = dialSize
                            )
                        }

                        if (showRadio) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = selectedStation?.name ?: "No station",
                                    color = if (isPlaying) DialLime else TextSecondary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DialMediaButton(
                                        onClick = {
                                            val station = selectedStation ?: return@DialMediaButton
                                            val intent = Intent(
                                                context,
                                                RadioPlayerService::class.java
                                            ).apply {
                                                action = RadioPlayerService.ACTION_PLAY
                                                putExtra(
                                                    RadioPlayerService.EXTRA_STATION_NAME,
                                                    station.name
                                                )
                                                putExtra(
                                                    RadioPlayerService.EXTRA_STATION_URL,
                                                    station.url
                                                )
                                            }
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                context.startForegroundService(intent)
                                            } else {
                                                context.startService(intent)
                                            }
                                            isPlaying = true
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            modifier = Modifier.size(24.dp),
                                            tint = TextPrimary
                                        )
                                    }

                                    DialMediaButton(
                                        onClick = {
                                            val intent = Intent(
                                                context,
                                                RadioPlayerService::class.java
                                            ).apply {
                                                action = RadioPlayerService.ACTION_PAUSE
                                            }
                                            context.startService(intent)
                                            isPlaying = false
                                        },
                                        emphasized = true
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Pause,
                                            contentDescription = "Pause",
                                            modifier = Modifier.size(26.dp),
                                            tint = TextPrimary
                                        )
                                    }

                                    DialMediaButton(
                                        onClick = {
                                            if (stations.isEmpty()) return@DialMediaButton
                                            val currentIndex =
                                                stations.indexOfFirst { it.id == selectedId }
                                            val nextIndex = if (currentIndex == -1) {
                                                0
                                            } else {
                                                (currentIndex + 1) % stations.size
                                            }
                                            val nextStation = stations[nextIndex]
                                            settingsRepository.setSelectedStation(nextStation.id)
                                            val intent = Intent(
                                                context,
                                                RadioPlayerService::class.java
                                            ).apply {
                                                action = RadioPlayerService.ACTION_PLAY
                                                putExtra(
                                                    RadioPlayerService.EXTRA_STATION_NAME,
                                                    nextStation.name
                                                )
                                                putExtra(
                                                    RadioPlayerService.EXTRA_STATION_URL,
                                                    nextStation.url
                                                )
                                            }
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                context.startForegroundService(intent)
                                            } else {
                                                context.startService(intent)
                                            }
                                            isPlaying = true
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "Next Station",
                                            modifier = Modifier.size(24.dp),
                                            tint = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(22.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onResize(dragAmount.x, dragAmount.y)
                                    },
                                    onDragEnd = { onActionFinished() }
                                )
                            },
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp, 2.dp)
                                .background(TextSecondary.copy(alpha = 0.45f))
                                .align(Alignment.BottomEnd)
                        )
                        Box(
                            modifier = Modifier
                                .size(2.dp, 10.dp)
                                .background(TextSecondary.copy(alpha = 0.45f))
                                .align(Alignment.BottomEnd)
                        )
                    }
                }
            }

            if (showAlerts && camerasEnabled && cameraDistM != null) {
                CameraWarningSideBars()
            }
        }
    }
}

