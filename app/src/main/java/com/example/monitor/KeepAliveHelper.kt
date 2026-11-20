package com.example.monitor

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class KeepAliveStatus(
    val notificationsEnabled: Boolean = false,
    val ignoringBatteryOptimizations: Boolean = false,
    val sentinelWorkArmed: Boolean = false,
    val foregroundServiceActive: Boolean = false,
    val manufacturerName: String = Build.MANUFACTURER.orEmpty(),
    val deviceModel: String = Build.MODEL.orEmpty(),
    val androidVersion: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
) {
    val readinessScore: Int
        get() {
            var score = 0
            if (notificationsEnabled) score += 35
            if (ignoringBatteryOptimizations) score += 35
            if (sentinelWorkArmed) score += 20
            if (foregroundServiceActive) score += 10
            return score.coerceAtMost(100)
        }

    val romGuideTitle: String
        get() = when {
            manufacturerName.contains("xiaomi", true) ||
                manufacturerName.contains("redmi", true) ||
                manufacturerName.contains("poco", true) -> "HyperOS / MIUI 保活建议"
            manufacturerName.contains("oppo", true) ||
                manufacturerName.contains("realme", true) ||
                manufacturerName.contains("oneplus", true) -> "ColorOS / 一加保活建议"
            manufacturerName.contains("vivo", true) ||
                manufacturerName.contains("iqoo", true) -> "OriginOS / iQOO 保活建议"
            manufacturerName.contains("huawei", true) ||
                manufacturerName.contains("honor", true) -> "HarmonyOS / MagicOS 保活建议"
            manufacturerName.contains("samsung", true) -> "One UI 保活建议"
            manufacturerName.contains("smartisan", true) ||
                deviceModel.contains("DT1901", true) -> "Smartisan OS (坚果 Pro 3) 保活建议"
            else -> "通用 Android 免 Root 保活建议"
        }

    val romGuideSteps: List<String>
        get() = when {
            manufacturerName.contains("xiaomi", true) ||
                manufacturerName.contains("redmi", true) ||
                manufacturerName.contains("poco", true) -> listOf(
                "小米 13 / HyperOS：应用信息 → 省电策略 → 选择「无限制」",
                "应用信息 → 开启「自启动」，允许 WorkManager 插电拉起",
                "多任务界面长按 VoltPulse 卡片点击「锁定」防止清理"
            )
            manufacturerName.contains("oppo", true) ||
                manufacturerName.contains("realme", true) ||
                manufacturerName.contains("oneplus", true) -> listOf(
                "OPPO Find X8 Ultra / ColorOS：应用信息 → 耗电管理 → 开启「允许完全后台行为」与「允许自启动」",
                "多任务界面点击右上角菜单 →「锁定」本应用",
                "电池设置 → 更多设置 → 关闭「睡眠待机优化」避免夜间 SuperVOOC 断录"
            )
            manufacturerName.contains("vivo", true) ||
                manufacturerName.contains("iqoo", true) -> listOf(
                "应用信息 → 耗电 → 选择「允许高耗电 / 后台高耗电」",
                "i管家 → 应用管理 → 权限管理 → 开启「自启动」",
                "多任务界面下拉卡片「锁定」应用"
            )
            manufacturerName.contains("huawei", true) ||
                manufacturerName.contains("honor", true) -> listOf(
                "电池 → 应用启动管理 → 关闭自动管理，手动开启「允许自启动 / 后台活动」",
                "多任务界面下拉卡片加锁，防止锁屏内存清理"
            )
            manufacturerName.contains("samsung", true) -> listOf(
                "应用信息 → 电池 → 设为「不受限制」",
                "设备维护 → 电池 → 后台使用限制 → 将本应用加入「永不休眠」"
            )
            manufacturerName.contains("smartisan", true) ||
                deviceModel.contains("DT1901", true) -> listOf(
                "坚果 Pro 3 / Smartisan OS：设置 → 电池与省电 → 后台应用管理 → 将 VoltPulse 设为「不限制」",
                "应用权限管理 → 开启「自启动权限」",
                "多任务界面下拉本应用卡片加锁，保障 PD/PPS 全程曲线不断录"
            )
            else -> listOf(
                "电池优化中将 VoltPulse 设为「不受限制」",
                "保持通知栏权限开启，充电期间通过前台常驻通知提升进程优先级",
                "开启 WorkManager 插电自动拉起，平时不占后台、插电自动唤醒"
            )
        }
}

object KeepAliveHelper {

    fun checkStatus(
        context: Context,
        sentinelArmed: Boolean,
        serviceRunning: Boolean
    ): KeepAliveStatus {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignoringOpt = pm.isIgnoringBatteryOptimizations(context.packageName)

        val notifPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val notifEnabled = notifPermissionGranted &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

        return KeepAliveStatus(
            notificationsEnabled = notifEnabled,
            ignoringBatteryOptimizations = ignoringOpt,
            sentinelWorkArmed = sentinelArmed,
            foregroundServiceActive = serviceRunning
        )
    }

    @SuppressLint("BatteryLife")
    fun openBatteryOptimizationSettings(context: Context) {
        val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(directIntent) }
            .onFailure {
                runCatching { context.startActivity(fallbackIntent) }
                    .onFailure { openAppDetailsSettings(context) }
            }
    }

    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        runCatching { context.startActivity(intent) }
            .onFailure { openAppDetailsSettings(context) }
    }

    /**
     * Attempts to launch OEM-specific Auto-Start / Background Control screens,
     * falling back safely to standard Android App Info details if unavailable.
     */
    fun openOemAutoStartOrAppInfo(context: Context) {
        val candidates = listOf(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            ComponentName(
                "com.oplus.safecenter",
                "com.oplus.safecenter.permission.startup.StartupAppListActivity"
            ),
            ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            ),
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
        )

        for (component in candidates) {
            val intent = Intent().apply {
                this.component = component
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (resolved != null) {
                val started = runCatching { context.startActivity(intent); true }.getOrDefault(false)
                if (started) return
            }
        }
        openAppDetailsSettings(context)
    }
}
