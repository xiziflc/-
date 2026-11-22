package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CalibrationSettings
import com.example.data.SysfsProbeItem
import com.example.monitor.KeepAliveStatus
import com.example.monitor.ShizukuStatus
import com.example.ui.components.CyberBadge
import com.example.ui.components.CyberOutlinedButton
import com.example.ui.components.CyberPanel
import com.example.ui.components.CyberPrimaryButton
import com.example.ui.components.CyberSectionHeader
import com.example.ui.theme.LocalCyberColors

@Composable
fun SentinelScreen(
    keepAliveStatus: KeepAliveStatus,
    shizukuStatus: ShizukuStatus,
    settings: CalibrationSettings,
    sysfsNodes: List<SysfsProbeItem>,
    onToggleAutoStart: (Boolean) -> Unit,
    onToggleService: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenBatteryOptimization: () -> Unit,
    onOpenOemAutoStart: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onDownloadShizuku: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onApplyShizukuHardening: () -> Unit,
    onRefreshShizuku: () -> Unit,
    onExportGlobalLogs: () -> Unit,
    onRescanSysfs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cyber = LocalCyberColors.current
    val scoreColor = when {
        keepAliveStatus.readinessScore >= 80 -> cyber.signalEmerald
        keepAliveStatus.readinessScore >= 50 -> cyber.acidYellow
        else -> cyber.lucyRose
    }
    val readableSysfsCount = sysfsNodes.count { it.readable }
    val groupedSysfs = remember(sysfsNodes) { sysfsNodes.groupBy { it.vendorGroup } }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "sentinel_header") {
                Column {
                    Text(
                        text = "ICE SHIELD // 免 ROOT 保活",
                        style = MaterialTheme.typography.headlineLarge,
                        color = cyber.lucyPearl
                    )
                    Text(
                        text = "未充电零后台 · 插电 WorkManager 自动唤醒 · 充电期前台常驻不断录",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Readiness Score Card with Lucy Cyberspace Artwork Header
            item(key = "readiness_card") {
                CyberPanel(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = scoreColor
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(94.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_lucy_netdive_card),
                                contentDescription = "Lucy ICE 保活视觉图",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.background.copy(alpha = 0.28f),
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                            )
                                        )
                                    )
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Shield,
                                        contentDescription = "保活就绪度",
                                        tint = scoreColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = "NETRUNNER 保活就绪度",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = cyber.lucyPearl,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "${keepAliveStatus.readinessScore}%",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = scoreColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { keepAliveStatus.readinessScore / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = scoreColor,
                                trackColor = cyber.gridLine
                            )

                            Text(
                                text = "${keepAliveStatus.androidVersion} · ${keepAliveStatus.manufacturerName} ${keepAliveStatus.deviceModel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            CyberPrimaryButton(
                                text = if (keepAliveStatus.foregroundServiceActive) "停止当前前台监控服务" else "立即启动前台监控服务",
                                onClick = onToggleService,
                                accentColor = if (keepAliveStatus.foregroundServiceActive) cyber.monowireCrimson else cyber.neonCyan,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sentinel_toggle_service_button")
                            )
                        }
                    }
                }
            }

            // Shizuku Progressive Guidance Card
            item(key = "shizuku_header") {
                CyberSectionHeader(
                    indexTag = "// 00",
                    title = "Shizuku 进阶保活引导（可选）"
                )
            }

            item(key = "shizuku_card") {
                val shizukuAccent = when {
                    shizukuStatus.isHardened -> cyber.signalEmerald
                    shizukuStatus.isReadyForHardening -> cyber.neonCyan
                    shizukuStatus.isBinderAlive -> cyber.acidYellow
                    else -> cyber.lucyLavender
                }
                val shizukuBadge = when {
                    shizukuStatus.isHardened -> "已加固"
                    shizukuStatus.isReadyForHardening -> "待加固"
                    shizukuStatus.isBinderAlive -> "待授权"
                    shizukuStatus.isInstalled -> "未启动"
                    else -> "未安装"
                }

                CyberPanel(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = shizukuAccent
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Shizuku 免 Root 深度防冻结",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = cyber.lucyPearl
                                )
                                CyberBadge(text = shizukuBadge, color = shizukuAccent)
                            }
                            IconButton(onClick = onRefreshShizuku) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "刷新 Shizuku 状态",
                                    tint = shizukuAccent
                                )
                            }
                        }

                        when {
                            !shizukuStatus.isInstalled -> {
                                Text(
                                    text = "未检测到 Shizuku。如果你在熄屏充电时仍遇到系统杀后台或漏采断点，可安装 Shizuku 一键写入系统白名单（不安装也可使用下方常规保活）。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                CyberOutlinedButton(
                                    text = "了解与下载 Shizuku",
                                    onClick = onDownloadShizuku,
                                    accentColor = cyber.lucyLavender,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            !shizukuStatus.isBinderAlive -> {
                                Text(
                                    text = "已安装 Shizuku，但后台服务尚未启动。请按以下步骤激活：\n1. 打开 Shizuku → 点击「配对」开启开发者选项与无线调试；\n2. 返回 Shizuku 点击「启动」，完成后回到本页。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CyberPrimaryButton(
                                        text = "打开 Shizuku",
                                        onClick = onOpenShizukuApp,
                                        accentColor = cyber.acidYellow,
                                        modifier = Modifier.weight(1f)
                                    )
                                    CyberOutlinedButton(
                                        text = "重新检测",
                                        onClick = onRefreshShizuku,
                                        accentColor = cyber.neonCyan,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            !shizukuStatus.isPermissionGranted -> {
                                Text(
                                    text = "Shizuku 服务已运行！授权后本应用将仅执行：加入系统 Doze 白名单、允许后台运行及提升活跃优先级，绝不修改其他系统文件。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                CyberPrimaryButton(
                                    text = "一键授权 Shizuku",
                                    onClick = onRequestShizukuPermission,
                                    accentColor = cyber.neonCyan,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            else -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CyberBadge(
                                        text = if (shizukuStatus.dozeWhitelisted) "Doze白名单 ✓" else "Doze白名单 ✗",
                                        color = if (shizukuStatus.dozeWhitelisted) cyber.signalEmerald else cyber.acidYellow
                                    )
                                    CyberBadge(
                                        text = if (shizukuStatus.runInBackgroundAllowed) "后台放行 ✓" else "后台放行 ✗",
                                        color = if (shizukuStatus.runInBackgroundAllowed) cyber.signalEmerald else cyber.acidYellow
                                    )
                                    CyberBadge(
                                        text = "桶:${shizukuStatus.standbyBucket}",
                                        color = if (shizukuStatus.standbyBucket.startsWith("ACTIVE")) cyber.signalEmerald else cyber.lucyLavender
                                    )
                                }
                                if (shizukuStatus.lastActionMessage.isNotBlank()) {
                                    Text(
                                        text = shizukuStatus.lastActionMessage,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = shizukuAccent
                                    )
                                }
                                CyberPrimaryButton(
                                    text = if (shizukuStatus.isHardened) "重新执行 Shizuku 保活加固" else "一键执行 Shizuku 保活加固",
                                    onClick = onApplyShizukuHardening,
                                    accentColor = if (shizukuStatus.isHardened) cyber.signalEmerald else cyber.neonCyan,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        CyberOutlinedButton(
                            text = "导出全局运行与保活诊断日志",
                            onClick = onExportGlobalLogs,
                            accentColor = cyber.lucyLavender,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Interactive Keep-Alive Checklist
            item(key = "checklist_header") {
                CyberSectionHeader(
                    indexTag = "// 01",
                    title = "系统权限与唤醒自检"
                )
            }

            item(key = "checklist_items") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CyberPanel(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = if (keepAliveStatus.sentinelWorkArmed) cyber.signalEmerald else cyber.acidYellow,
                        cutSize = 10.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "WorkManager 插电自动拉起",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    CyberBadge(
                                        text = if (keepAliveStatus.sentinelWorkArmed) "已布防" else "未启用",
                                        color = if (keepAliveStatus.sentinelWorkArmed) cyber.signalEmerald else cyber.textMuted
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "通过系统 JobScheduler 充电约束触发：进程被回收后，插上充电器自动唤醒记录完整曲线，拔电后自动释放。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = settings.autoStartOnCharge,
                                onCheckedChange = onToggleAutoStart,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = cyber.signalEmerald
                                ),
                                modifier = Modifier.testTag("sentinel_auto_start_switch")
                            )
                        }
                    }

                    KeepAliveActionCard(
                        title = "通知栏常驻权限",
                        description = "充电期间展示实时功率/协议/温度通知，是免 Root 前台服务保持高优先级的关键。",
                        passed = keepAliveStatus.notificationsEnabled,
                        actionText = if (keepAliveStatus.notificationsEnabled) "通知设置" else "一键授权",
                        onAction = onRequestNotificationPermission,
                        testTag = "sentinel_notification_button"
                    )

                    KeepAliveActionCard(
                        title = "忽略电池优化（无限制）",
                        description = "防止系统 Doze 模式与墓碑机制在息屏快充期间冻结采样线程。",
                        passed = keepAliveStatus.ignoringBatteryOptimizations,
                        actionText = if (keepAliveStatus.ignoringBatteryOptimizations) "已加入白名单" else "去设置无限制",
                        onAction = onOpenBatteryOptimization,
                        testTag = "sentinel_battery_opt_button"
                    )
                }
            }

            // OEM Specific ROM Keep-Alive Guide
            item(key = "rom_guide") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CyberSectionHeader(
                        indexTag = "// 02",
                        title = keepAliveStatus.romGuideTitle,
                        accentColor = cyber.acidYellow
                    )

                    CyberPanel(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = cyber.acidYellow
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            keepAliveStatus.romGuideSteps.forEachIndexed { idx, step ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "0${idx + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = cyber.acidYellow
                                    )
                                    Text(
                                        text = step,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                            CyberOutlinedButton(
                                text = "打开系统自启 / 应用详情页",
                                onClick = onOpenOemAutoStart,
                                accentColor = cyber.acidYellow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sentinel_oem_autostart_button")
                            )
                        }
                    }
                }
            }

            // Grouped Multi-Vendor Kernel Sysfs Probe Diagnostics (OPPO / Xiaomi / Qualcomm PD-PPS)
            item(key = "sysfs_probe") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CyberSectionHeader(
                        indexTag = "// 03",
                        title = "OPPO / 小米 / 坚果 内核协议节点探测",
                        accentColor = cyber.lucyRose,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$readableSysfsCount/${sysfsNodes.size} 可读",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (readableSysfsCount > 0) cyber.signalEmerald else cyber.lucyRose
                                )
                                IconButton(
                                    onClick = onRescanSysfs,
                                    modifier = Modifier.testTag("sentinel_rescan_sysfs_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = "重新探测 Sysfs 节点",
                                        tint = cyber.neonCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    )

                    CyberPanel(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = cyber.lucyRose
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Memory,
                                    contentDescription = null,
                                    tint = cyber.lucyRose,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "免 Root 实测 SuperVOOC、Mi Turbo 与 USB PD/PPS 内核节点开放情况；被 SELinux 拦截时自动切换功率特征识别：",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            groupedSysfs.forEach { (groupName, items) ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = groupName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = cyber.lucyLavender,
                                        fontWeight = FontWeight.Bold
                                    )
                                    items.forEach { item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.nodeName,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                            Text(
                                                text = if (item.readable) item.value ?: "--" else "SELinux 拦截",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (item.readable) cyber.signalEmerald else cyber.textMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun KeepAliveActionCard(
    title: String,
    description: String,
    passed: Boolean,
    actionText: String,
    onAction: () -> Unit,
    testTag: String
) {
    val cyber = LocalCyberColors.current
    val accent = if (passed) cyber.signalEmerald else cyber.signalAmber

    CyberPanel(
        modifier = Modifier.fillMaxWidth(),
        accentColor = accent,
        cutSize = 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (passed) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                        contentDescription = if (passed) "已就绪" else "待配置",
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                CyberBadge(
                    text = if (passed) "OK" else "待配置",
                    color = accent
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CyberOutlinedButton(
                text = actionText,
                onClick = onAction,
                accentColor = accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
            )
        }
    }
}
