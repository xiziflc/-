package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.monitor.KeepAliveHelper
import com.example.ui.VoltPulseViewModel
import com.example.ui.components.CyberGridBackdrop
import com.example.ui.screens.CalibrationScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.MonitorScreen
import com.example.ui.screens.SentinelScreen
import com.example.ui.theme.LocalCyberColors
import com.example.ui.theme.VoltPulseTheme

private enum class VoltPulseTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    MONITOR("monitor", "遥测", Icons.Filled.Bolt, Icons.Outlined.Bolt),
    HISTORY("history", "日志", Icons.Filled.History, Icons.Outlined.History),
    SENTINEL("sentinel", "保活", Icons.Filled.Shield, Icons.Outlined.Shield),
    CALIBRATION("calibration", "校准", Icons.Filled.Tune, Icons.Outlined.Tune)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            VoltPulseTheme {
                VoltPulseRoot()
            }
        }
    }
}

@Composable
private fun VoltPulseRoot(
    viewModel: VoltPulseViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cyber = LocalCyberColors.current

    var currentTab by rememberSaveable { mutableStateOf(VoltPulseTab.MONITOR) }

    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val liveWaveform by viewModel.liveWaveform.collectAsStateWithLifecycle()
    val fullSessionCurve by viewModel.fullSessionCurve.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val selectedSession by viewModel.selectedSession.collectAsStateWithLifecycle()
    val selectedSamples by viewModel.selectedSessionSamples.collectAsStateWithLifecycle()
    val isServiceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
    val isSentinelArmed by viewModel.isSentinelArmed.collectAsStateWithLifecycle()
    val keepAliveStatus by viewModel.keepAliveStatus.collectAsStateWithLifecycle()
    val sysfsNodes by viewModel.sysfsNodes.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshKeepAliveStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshKeepAliveStatus()
        if (!granted) {
            KeepAliveHelper.openNotificationSettings(context)
        }
    }

    val requestNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !keepAliveStatus.notificationsEnabled) {
            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            KeepAliveHelper.openNotificationSettings(context)
        }
    }

    if (currentTab != VoltPulseTab.MONITOR && selectedSession == null) {
        BackHandler { currentTab = VoltPulseTab.MONITOR }
    }

    CyberGridBackdrop(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            val screenContent: @Composable (Modifier) -> Unit = { contentMod ->
                when (currentTab) {
                    VoltPulseTab.MONITOR -> MonitorScreen(
                        telemetry = telemetry,
                        settings = settings,
                        liveWaveform = liveWaveform,
                        fullSessionCurve = fullSessionCurve,
                        isServiceRunning = isServiceRunning,
                        isSentinelArmed = isSentinelArmed,
                        onToggleService = {
                            if (!isServiceRunning &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !keepAliveStatus.notificationsEnabled
                            ) {
                                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.toggleMonitorService()
                        },
                        onSelectDeviceProfile = viewModel::applyDeviceProfile,
                        onQuickVoltageMultiplier = viewModel::setVoltageMultiplier,
                        onQuickInvertSign = viewModel::setInvertCurrentSign,
                        modifier = contentMod
                    )

                    VoltPulseTab.HISTORY -> HistoryScreen(
                        sessions = sessions,
                        ratedCapacityMah = settings.ratedCapacityMah,
                        selectedSession = selectedSession,
                        selectedSamples = selectedSamples,
                        onSelectSession = viewModel::selectSession,
                        onDeleteSession = viewModel::deleteSession,
                        onStartMonitorNow = {
                            viewModel.startMonitorServiceIfIdle()
                            currentTab = VoltPulseTab.MONITOR
                        },
                        modifier = contentMod
                    )

                    VoltPulseTab.SENTINEL -> SentinelScreen(
                        keepAliveStatus = keepAliveStatus,
                        settings = settings,
                        sysfsNodes = sysfsNodes,
                        onToggleAutoStart = viewModel::setAutoStartOnCharge,
                        onToggleService = viewModel::toggleMonitorService,
                        onRequestNotificationPermission = requestNotifications,
                        onOpenBatteryOptimization = { KeepAliveHelper.openBatteryOptimizationSettings(context) },
                        onOpenOemAutoStart = { KeepAliveHelper.openOemAutoStartOrAppInfo(context) },
                        onRescanSysfs = viewModel::rescanSysfs,
                        modifier = contentMod
                    )

                    VoltPulseTab.CALIBRATION -> CalibrationScreen(
                        telemetry = telemetry,
                        settings = settings,
                        onSelectDeviceProfile = viewModel::applyDeviceProfile,
                        onVoltageMultiplierChange = viewModel::setVoltageMultiplier,
                        onCurrentMultiplierChange = viewModel::setCurrentMultiplier,
                        onInvertSignChange = viewModel::setInvertCurrentSign,
                        onUnitModeChange = viewModel::setCurrentUnitMode,
                        onRatedCapacityChange = viewModel::setRatedCapacityMah,
                        onEnableAlertsChange = viewModel::setEnableAlerts,
                        onTempThresholdChange = viewModel::setTempAlertThreshold,
                        onTargetPctChange = viewModel::setTargetChargeAlertPct,
                        onResetCalibration = viewModel::resetCalibration,
                        modifier = contentMod
                    )
                }
            }

            if (useNavigationRail) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        contentColor = cyber.neonCyan,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        VoltPulseTab.entries.forEach { tab ->
                            val selected = tab == currentTab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = cyber.neonCyan,
                                    selectedTextColor = cyber.neonCyan,
                                    indicatorColor = cyber.lucyLavender.copy(alpha = 0.22f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_rail_${tab.route}")
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        screenContent(Modifier.fillMaxSize())
                    }
                }
            } else {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                            tonalElevation = 0.dp
                        ) {
                            VoltPulseTab.entries.forEach { tab ->
                                val selected = tab == currentTab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = cyber.neonCyan,
                                        selectedTextColor = cyber.neonCyan,
                                        indicatorColor = cyber.lucyLavender.copy(alpha = 0.22f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("nav_tab_${tab.route}")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    screenContent(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}
