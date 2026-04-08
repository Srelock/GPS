package com.motorider.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

@AndroidEntryPoint
class OverlayService : LifecycleService(), ViewModelStoreOwner, SavedStateRegistryOwner {

    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private val windowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private var overlayRoot: FrameLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null

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
        
        private const val MIN_WIDTH_DP = 160
        private const val MIN_HEIGHT_DP = 120
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
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startOverlay()
            ACTION_STOP -> stopOverlay()
        }
        return START_STICKY
    }

    private fun startOverlay() {
        if (overlayRoot != null) return
        if (!Settings.canDrawOverlays(this)) {
            android.util.Log.e("OverlayService", "Cannot show overlay: Permission not granted")
            stopSelf()
            return
        }

        startForeground(NOTIFICATION_ID, createNotification("Overlay running"))

        val density = resources.displayMetrics.density
        // Fetch logical (DP) values and convert to Pixels
        val dpX = runBlocking { settingsRepository.overlayX.first() }
        val dpY = runBlocking { settingsRepository.overlayY.first() }
        val dpWidth = runBlocking { settingsRepository.overlayWidth.first() }
        val dpHeight = runBlocking { settingsRepository.overlayHeight.first() }

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
                MaterialTheme {
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
                            // Update the state so Compose recomposes with new size
                            overlayWidthDp.floatValue = params.width.toFloat() / density
                        },
                        onActionFinished = {
                            // Convert back to logical DP for storage
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
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopOverlay()
        super.onDestroy()
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
private fun PulsingStatus() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = Modifier
            .size(6.dp)
            .alpha(alpha)
            .background(com.motorider.ui.theme.NeonGreen, androidx.compose.foundation.shape.CircleShape)
    )
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

    // Real-time playing state check via Service (rough proxy)
    var isPlaying by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        color = com.motorider.ui.theme.DarkBackground.copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.motorider.ui.theme.NeonCyan.copy(alpha = 0.4f)),
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Futuristic Header / Drag Area — Taller for easier grabbing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(com.motorider.ui.theme.CardBackground.copy(alpha = 0.5f))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onMove(dragAmount.x, dragAmount.y)
                            },
                            onDragEnd = { onActionFinished() }
                        )
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val context = androidx.compose.ui.platform.LocalContext.current
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            // Stop All from Overlay
                            val radioIntent = Intent(context, RadioPlayerService::class.java).apply { action = RadioPlayerService.ACTION_STOP }
                            val overlayIntent = Intent(context, OverlayService::class.java).apply { action = OverlayService.ACTION_STOP }
                            val locIntent = Intent(context, LocationForegroundService::class.java).apply { action = LocationForegroundService.ACTION_STOP }
                            context.startService(radioIntent)
                            context.startService(overlayIntent)
                            context.startService(locIntent)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close, 
                            contentDescription = "Stop All",
                            modifier = Modifier.size(20.dp),
                            tint = com.motorider.ui.theme.NeonRed
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "HUD",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = com.motorider.ui.theme.NeonCyan
                    )
                }
                
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close HUD Only",
                        modifier = Modifier.size(18.dp),
                        tint = com.motorider.ui.theme.TextSecondary
                    )
                }
            }

            // Main Display Area - Also made draggable for easier movement
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onMove(dragAmount.x, dragAmount.y)
                            },
                            onDragEnd = { onActionFinished() }
                        )
                    }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Speed Readout — scales with overlay window size
                    // Uses overlayWidthDp from WindowManager so it recomposes on resize
                    val scaledSpeedSize = (overlayWidthDp / 3.2f).coerceIn(40f, 180f).sp
                    val scaledUnitSize = (overlayWidthDp / 14f).coerceIn(10f, 32f).sp
                    val unitPadding = (overlayWidthDp / 30f).coerceIn(4f, 20f).dp

                    val displaySpeed = speedKmh * 0.621371
                    val roadLimit by locationRepository.roadSpeedLimit.collectAsState()
                    val displayRoadLimit = roadLimit?.let { it * 0.621371 }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Speed Limit Sign (Now on the LEFT)
                        displayRoadLimit?.let { limit ->
                            Surface(
                                modifier = Modifier
                                    .size((scaledSpeedSize.value * 0.45f).dp),
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = androidx.compose.ui.graphics.Color.White,
                                border = androidx.compose.foundation.BorderStroke(3.dp, androidx.compose.ui.graphics.Color.Red),
                                shadowElevation = 4.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${Math.round(limit).toInt()}",
                                        color = androidx.compose.ui.graphics.Color.Black,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = (scaledSpeedSize.value * 0.22f).sp
                                    )
                                }
                            }
                            Spacer(Modifier.width(20.dp))
                        }

                        // 2. Current Speed (Now on the RIGHT)
                        Text(
                            text = "${displaySpeed.toInt()}",
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = scaledSpeedSize,
                                fontWeight = FontWeight.Black,
                                color = com.motorider.ui.theme.NeonCyan
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(
                        modifier = Modifier.fillMaxWidth(0.6f).align(Alignment.CenterHorizontally),
                        thickness = 1.dp,
                        color = com.motorider.ui.theme.NeonCyan.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Media Status
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isPlaying) {
                                PulsingStatus()
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = selectedStation?.name ?: "NO STATION",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isPlaying) com.motorider.ui.theme.NeonGreen else com.motorider.ui.theme.TextSecondary,
                                maxLines = 1
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            
                            // Play Button
                            Surface(
                                onClick = {
                                    val station = selectedStation ?: return@Surface
                                    val intent = Intent(context, RadioPlayerService::class.java).apply {
                                        action = RadioPlayerService.ACTION_PLAY
                                        putExtra(RadioPlayerService.EXTRA_STATION_NAME, station.name)
                                        putExtra(RadioPlayerService.EXTRA_STATION_URL, station.url)
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                                    isPlaying = true
                                },
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = if (isPlaying) com.motorider.ui.theme.NeonGreen.copy(alpha = 0.1f) else com.motorider.ui.theme.CardBackground,
                                modifier = Modifier.size(44.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isPlaying) com.motorider.ui.theme.NeonGreen else com.motorider.ui.theme.TextSecondary.copy(alpha = 0.5f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(id = android.R.drawable.ic_media_play),
                                        contentDescription = "Play",
                                        modifier = Modifier.size(24.dp),
                                        tint = if (isPlaying) com.motorider.ui.theme.NeonGreen else com.motorider.ui.theme.TextPrimary
                                    )
                                }
                            }

                            Spacer(Modifier.width(20.dp))

                            // Pause Button
                            Surface(
                                onClick = {
                                    val intent = Intent(context, RadioPlayerService::class.java).apply {
                                        action = RadioPlayerService.ACTION_PAUSE
                                    }
                                    context.startService(intent)
                                    isPlaying = false
                                },
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = com.motorider.ui.theme.CardBackground,
                                modifier = Modifier.size(44.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, com.motorider.ui.theme.TextSecondary.copy(alpha = 0.5f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(id = android.R.drawable.ic_media_pause),
                                        contentDescription = "Pause",
                                        modifier = Modifier.size(24.dp),
                                        tint = com.motorider.ui.theme.TextPrimary
                                    )
                                }
                            }
                            
                            Spacer(Modifier.width(20.dp))
                            
                            // Next Station Button
                            Surface(
                                onClick = {
                                    if (stations.isEmpty()) return@Surface
                                    val currentIndex = stations.indexOfFirst { it.id == selectedId }
                                    val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % stations.size
                                    val nextStation = stations[nextIndex]
                                    
                                    // 1. Update selected station in settings so it reflects everywhere
                                    settingsRepository.setSelectedStation(nextStation.id)
                                    
                                    // 2. Play the new station immediately
                                    val intent = Intent(context, RadioPlayerService::class.java).apply {
                                        action = RadioPlayerService.ACTION_PLAY
                                        putExtra(RadioPlayerService.EXTRA_STATION_NAME, nextStation.name)
                                        putExtra(RadioPlayerService.EXTRA_STATION_URL, nextStation.url)
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                                    isPlaying = true
                                },
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = com.motorider.ui.theme.CardBackground,
                                modifier = Modifier.size(44.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, com.motorider.ui.theme.TextSecondary.copy(alpha = 0.5f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.SkipNext,
                                        contentDescription = "Next Station",
                                        modifier = Modifier.size(24.dp),
                                        tint = com.motorider.ui.theme.TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // Precision Resize Handle
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
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
                            .background(com.motorider.ui.theme.NeonCyan.copy(alpha = 0.6f))
                            .align(Alignment.BottomEnd)
                    )
                    Box(
                        modifier = Modifier
                            .size(2.dp, 10.dp)
                            .background(com.motorider.ui.theme.NeonCyan.copy(alpha = 0.6f))
                            .align(Alignment.BottomEnd)
                    )
                }
            }
        }
    }
}

