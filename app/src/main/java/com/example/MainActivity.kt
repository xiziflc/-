package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.widget.Toast
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
import com.example.data.DiagnosticExporter
import com.example.data.DiagnosticLogger
import com.example.monitor.KeepAliveHelper
import com.example.monitor.ShizukuHelper
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
    private var lastLoggedRefreshRate = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        applyHighestRefreshRate()
        setContent {
            VoltPulseTheme {
                VoltPulseRoot(displayInfoProvider = { getDisplayInfoSummary() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyHighestRefreshRate()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyHighestRefreshRate()
        }
    }

    private fun currentDisplay(): Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display ?: @Suppress("DEPRECATION") windowManager.defaultDisplay
    } else {
        @Suppress("DEPRECATION") windowManager.defaultDisplay
    }

    private fun getDisplayInfoSummary(): String = DiagnosticExporter.describeDisplay(currentDisplay())

    private fun applyHighestRefreshRate() {
        val targetDisplay = currentDisplay() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeMode = targetDisplay.mode
            val bestMode = targetDisplay.supportedModes
                .filter {
                    it.physicalWidth == activeMode.physicalWidth &&
                        it.physicalHeight == activeMode.physicalHeight
                }
                .maxByOrNull { it.refreshRate }
                ?: activeMode

            val attrs = window.attributes
            if (attrs.preferredDisplayModeId != bestMode.modeId ||
                attrs.preferredRefreshRate != bestMode.refreshRate
            ) {
                attrs.preferredDisplayModeId = bestMode.modeId
                attrs.preferredRefreshRate = bestMode.refreshRate
                window.attributes = attrs
            }

            val rateInt = bestMode.refreshRate.toInt()
            if (lastLoggedRefreshRate != rateInt) {
                lastLoggedRefreshRate = rateInt
                DiagnosticLogger.log(
                    "DISPLAY",
                    "Applied preferredDisplayModeId=${bestMode.modeId} (${rateInt}Hz), current=${activeMode.refreshRate.toInt()}Hz"
                )
            }
        }
    }
}

@Composable
private fun VoltPulseRoot(
    displayInfoProvider: () -> String,
    viewModel: VoltPulseViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cyber = LocalCyberColors.current

    var currentTab by rememberSaveable { mutableStateOf(VoltPulseTab.MONITOR) }
    val selectedSession by viewModel.selectedSession.collectAsStateWithLifecycle()

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !viewModel.keepAliveStatus.value.notificationsEnabled) {
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
                    VoltPulseTab.MONITOR -> MonitorTabHost(
                        viewModel = viewModel,
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !viewModel.keepAliveStatus.value.notificationsEnabled
                            ) {
                                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        modifier = contentMod
                    )

                    VoltPulseTab.HISTORY -> HistoryTabHost(
                        viewModel = viewModel,
                        displayInfoProvider = displayInfoProvider,
                        onSwitchToMonitor = { currentTab = VoltPulseTab.MONITOR },
                        modifier = contentMod
                    )

                    VoltPulseTab.SENTINEL -> SentinelTabHost(
                        viewModel = viewModel,
                        displayInfoProvider = displayInfoProvider,
                        onRequestNotifications = requestNotifications,
                        modifier = contentMod
                    )

                    VoltPulseTab.CALIBRATION -> CalibrationTabHost(
                        viewModel = viewModel,
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

@Composable
private fun MonitorTabHost(
    viewModel: VoltPulseViewModel,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val liveWaveform by viewModel.liveWaveform.collectAsStateWithLifecycle()
    val fullSessionCurve by viewModel.fullSessionCurve.collectAsStateWithLifecycle()
    val isServiceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
    val isSentinelArmed by viewModel.isSentinelArmed.collectAsStateWithLifecycle()

    MonitorScreen(
        telemetry = telemetry,
        settings = settings,
        liveWaveform = liveWaveform,
        fullSessionCurve = fullSessionCurve,
        isServiceRunning = isServiceRunning,
        isSentinelArmed = isSentinelArmed,
        onToggleService = {
            if (!isServiceRunning) {
                onRequestNotificationPermission()
            }
            viewModel.toggleMonitorService()
        },
        onSelectDeviceProfile = viewModel::applyDeviceProfile,
        onQuickVoltageMultiplier = viewModel::setVoltageMultiplier,
        onQuickInvertSign = viewModel::setInvertCurrentSign,
        modifier = modifier
    )
}

@Composable
private fun HistoryTabHost(
    viewModel: VoltPulseViewModel,
    displayInfoProvider: () -> String,
    onSwitchToMonitor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val selectedSession by viewModel.selectedSession.collectAsStateWithLifecycle()
    val selectedSamples by viewModel.selectedSessionSamples.collectAsStateWithLifecycle()

    HistoryScreen(
        sessions = sessions,
        ratedCapacityMah = settings.ratedCapacityMah,
        selectedSession = selectedSession,
        selectedSamples = selectedSamples,
        onSelectSession = viewModel::selectSession,
        onDeleteSession = viewModel::deleteSession,
        onExportSession = { session, samples ->
            val report = DiagnosticExporter.buildSessionReport(
                context = context,
                session = session,
                samples = samples,
                keepAliveStatus = viewModel.keepAliveStatus.value,
                shizukuStatus = viewModel.shizukuStatus.value,
                displayInfo = displayInfoProvider()
            )
            DiagnosticExporter.shareReportFile(
                context = context,
                fileNamePrefix = "voltpulse_archive_${session.id}",
                content = report
            ).onFailure {
                DiagnosticExporter.copyToClipboard(context, "VoltPulse诊断报告", report)
                Toast.makeText(context, "分享失败，已自动复制诊断报告到剪贴板", Toast.LENGTH_SHORT).show()
            }
        },
        onCopySessionReport = { session, samples ->
            val report = DiagnosticExporter.buildSessionReport(
                context = context,
                session = session,
                samples = samples,
                keepAliveStatus = viewModel.keepAliveStatus.value,
                shizukuStatus = viewModel.shizukuStatus.value,
                displayInfo = displayInfoProvider()
            )
            DiagnosticExporter.copyToClipboard(context, "VoltPulse诊断报告", report)
            Toast.makeText(context, "已复制 ARCHIVE #${session.id} 诊断报告", Toast.LENGTH_SHORT).show()
        },
        onStartMonitorNow = {
            viewModel.startMonitorServiceIfIdle()
            onSwitchToMonitor()
        },
        modifier = modifier
    )
}

@Composable
private fun SentinelTabHost(
    viewModel: VoltPulseViewModel,
    displayInfoProvider: () -> String,
    onRequestNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keepAliveStatus by viewModel.keepAliveStatus.collectAsStateWithLifecycle()
    val shizukuStatus by viewModel.shizukuStatus.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sysfsNodes by viewModel.sysfsNodes.collectAsStateWithLifecycle()

    SentinelScreen(
        keepAliveStatus = keepAliveStatus,
        shizukuStatus = shizukuStatus,
        settings = settings,
        sysfsNodes = sysfsNodes,
        onToggleAutoStart = viewModel::setAutoStartOnCharge,
        onToggleService = viewModel::toggleMonitorService,
        onRequestNotificationPermission = onRequestNotifications,
        onOpenBatteryOptimization = { KeepAliveHelper.openBatteryOptimizationSettings(context) },
        onOpenOemAutoStart = { KeepAliveHelper.openOemAutoStartOrAppInfo(context) },
        onOpenShizukuApp = { ShizukuHelper.openShizukuApp(context) },
        onDownloadShizuku = { ShizukuHelper.openShizukuDownload(context) },
        onRequestShizukuPermission = viewModel::requestShizukuPermission,
        onApplyShizukuHardening = viewModel::applyShizukuHardening,
        onRefreshShizuku = { viewModel.refreshShizukuStatus() },
        onExportGlobalLogs = {
            val report = DiagnosticExporter.buildGlobalReport(
                context = context,
                sessions = viewModel.sessions.value,
                keepAliveStatus = keepAliveStatus,
                shizukuStatus = shizukuStatus,
                displayInfo = displayInfoProvider()
            )
            DiagnosticExporter.shareReportFile(
                context = context,
                fileNamePrefix = "voltpulse_global_diag",
                content = report
            ).onFailure {
                DiagnosticExporter.copyToClipboard(context, "VoltPulse全局诊断", report)
                Toast.makeText(context, "分享失败，已自动复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        },
        onRescanSysfs = viewModel::rescanSysfs,
        modifier = modifier
    )
}

@Composable
private fun CalibrationTabHost(
    viewModel: VoltPulseViewModel,
    modifier: Modifier = Modifier
) {
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    CalibrationScreen(
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
        modifier = modifier
    )
}
