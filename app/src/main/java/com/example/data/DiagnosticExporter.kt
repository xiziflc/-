package com.example.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.Display
import androidx.core.content.FileProvider
import com.example.data.db.ChargingSessionEntity
import com.example.data.db.TelemetrySampleEntity
import com.example.monitor.KeepAliveStatus
import com.example.monitor.ShizukuStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object DiagnosticExporter {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val fileStampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun buildSessionReport(
        context: Context,
        session: ChargingSessionEntity,
        samples: List<TelemetrySampleEntity>,
        keepAliveStatus: KeepAliveStatus,
        shizukuStatus: ShizukuStatus,
        displayInfo: String
    ): String = buildString {
        val nowStr = isoFormat.format(Date())
        appendLine("# VoltPulse 充电断点诊断报告")
        appendLine("- 导出时间: $nowStr")
        appendLine("- 设备: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("- 系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("- 显示刷新率: $displayInfo")
        appendLine()

        appendKeepAliveSection(keepAliveStatus, shizukuStatus)

        appendLine("## 会话摘要 (ARCHIVE #${session.id})")
        appendLine("- 开始时间: ${isoFormat.format(Date(session.startTimeMs))}")
        appendLine("- 结束时间: ${isoFormat.format(Date(session.endTimeMs))}")
        appendLine("- 持续时长: ${(session.durationMs / 1000L)}s | 完成状态: ${session.isCompleted}")
        appendLine("- 电量变化: ${session.startLevelPct}% -> ${session.endLevelPct}% (+${session.levelDeltaPct}%)")
        appendLine("- 供电 / 协议: ${session.plugTypeLabel} / ${session.protocolLabel}")
        appendLine("- 功率(峰/均): ${fmt(session.peakPowerWatts)}W / ${fmt(session.avgPowerWatts)}W")
        appendLine("- 温度(峰/均): ${fmt(session.peakTempC)}°C / ${fmt(session.avgTempC)}°C")
        appendLine("- 充入电量: ${fmt(session.mahAdded, 0)} mAh | 原始样本计数: ${session.sampleCount} | 落库点数: ${samples.size}")
        appendLine()

        appendAnomalyAnalysis(samples)

        appendLine("## 完整采样点 CSV (${samples.size} 行)")
        appendLine("```csv")
        appendLine("idx,timestampMs,time,dtSec,socPct,powerW,currentMa,voltageV,tempC")
        var prevTs = samples.firstOrNull()?.timestampMs ?: 0L
        samples.forEachIndexed { idx, s ->
            val dtSec = if (idx == 0) 0.0 else (s.timestampMs - prevTs) / 1000.0
            prevTs = s.timestampMs
            appendLine(
                "$idx,${s.timestampMs},${isoFormat.format(Date(s.timestampMs))}," +
                    "${String.format(Locale.US, "%.1f", dtSec)},${s.levelPct}," +
                    "${fmt(s.powerWatts)},${fmt(s.currentMa, 0)},${fmt(s.voltageV, 3)},${fmt(s.temperatureC)}"
            )
        }
        appendLine("```")
        appendLine()

        appendDiagnosticLogs()
    }

    fun buildGlobalReport(
        context: Context,
        sessions: List<ChargingSessionEntity>,
        keepAliveStatus: KeepAliveStatus,
        shizukuStatus: ShizukuStatus,
        displayInfo: String
    ): String = buildString {
        appendLine("# VoltPulse 全局运行诊断报告")
        appendLine("- 导出时间: ${isoFormat.format(Date())}")
        appendLine("- 设备: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("- 系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("- 显示刷新率: $displayInfo")
        appendLine()

        appendKeepAliveSection(keepAliveStatus, shizukuStatus)

        appendLine("## 历史会话概览 (共 ${sessions.size} 条，展示最近 20 条)")
        sessions.take(20).forEach { s ->
            appendLine(
                "- #${s.id} [${isoFormat.format(Date(s.startTimeMs))}] " +
                    "${s.startLevelPct}%->${s.endLevelPct}% | " +
                    "峰${fmt(s.peakPowerWatts)}W/均${fmt(s.avgPowerWatts)}W | " +
                    "时长${s.durationMs / 1000L}s | 完成=${s.isCompleted} | ${s.protocolLabel}"
            )
        }
        appendLine()

        appendDiagnosticLogs()
    }

    private fun StringBuilder.appendKeepAliveSection(
        keepAliveStatus: KeepAliveStatus,
        shizukuStatus: ShizukuStatus
    ) {
        appendLine("## 保活与 Shizuku 状态")
        appendLine("- 保活就绪度: ${keepAliveStatus.readinessScore}%")
        appendLine("- 通知权限: ${keepAliveStatus.notificationsEnabled}")
        appendLine("- 系统电池优化白名单: ${keepAliveStatus.ignoringBatteryOptimizations}")
        appendLine("- WorkManager 哨兵: ${keepAliveStatus.sentinelWorkArmed}")
        appendLine("- 前台服务运行中: ${keepAliveStatus.foregroundServiceActive}")
        appendLine("- Shizuku 安装: ${shizukuStatus.isInstalled} | 服务运行: ${shizukuStatus.isBinderAlive} | 已授权: ${shizukuStatus.isPermissionGranted}")
        appendLine("- Shizuku 加固详情: Doze白名单=${shizukuStatus.dozeWhitelisted}, 后台运行=${shizukuStatus.runInBackgroundAllowed}, 活跃桶=${shizukuStatus.standbyBucket}")
        appendLine()
    }

    private fun StringBuilder.appendAnomalyAnalysis(samples: List<TelemetrySampleEntity>) {
        appendLine("## 自动断点与异常扫描")
        if (samples.size < 2) {
            appendLine("- 样本不足 2 个，无法分析。")
            appendLine()
            return
        }

        val gaps = mutableListOf<String>()
        val drops = mutableListOf<String>()
        var zeroPowerStreakStart: Long? = null
        var zeroPowerCount = 0

        for (i in 1 until samples.size) {
            val prev = samples[i - 1]
            val curr = samples[i]
            val dtSec = (curr.timestampMs - prev.timestampMs) / 1000.0
            if (dtSec >= 15.0) {
                gaps.add(
                    "点#${i - 1}->#$i (${isoFormat.format(Date(prev.timestampMs))} -> ${isoFormat.format(Date(curr.timestampMs))}): " +
                        "中断 ${String.format(Locale.US, "%.1f", dtSec)}s | " +
                        "电量 ${prev.levelPct}%->${curr.levelPct}% | " +
                        "功率 ${fmt(prev.powerWatts)}W->${fmt(curr.powerWatts)}W"
                )
            }
            val dP = curr.powerWatts - prev.powerWatts
            if (dP <= -8.0f || (prev.powerWatts >= 5.0f && curr.powerWatts < 1.0f)) {
                drops.add(
                    "点#${i - 1}->#$i (${isoFormat.format(Date(curr.timestampMs))}): " +
                        "功率骤降 ${fmt(prev.powerWatts)}W -> ${fmt(curr.powerWatts)}W | " +
                        "温度 ${fmt(curr.temperatureC)}°C | 电量 ${curr.levelPct}%"
                )
            }
            if (curr.powerWatts < 1.0f && abs(curr.currentMa) < 120f) {
                if (zeroPowerStreakStart == null) zeroPowerStreakStart = curr.timestampMs
                zeroPowerCount++
            }
        }

        appendLine("- 采样间隙 (>=15s): ${gaps.size} 处")
        gaps.take(20).forEach { appendLine("  - $it") }
        appendLine("- 功率骤降/掉零点: ${drops.size} 处")
        drops.take(20).forEach { appendLine("  - $it") }
        if (zeroPowerCount > 0 && zeroPowerStreakStart != null) {
            appendLine("- 低功率/0W 点数: $zeroPowerCount 个 (首次出现于 ${isoFormat.format(Date(zeroPowerStreakStart))})")
        }
        appendLine()
    }

    private fun StringBuilder.appendDiagnosticLogs() {
        val events = DiagnosticLogger.getEventsSnapshot(200)
        appendLine("## 系统诊断事件日志 (最近 ${events.size} 条)")
        appendLine("```text")
        if (events.isEmpty()) {
            appendLine("(暂无记录)")
        } else {
            events.forEach { appendLine(it) }
        }
        appendLine("```")
    }

    fun shareReportFile(context: Context, fileNamePrefix: String, content: String): Result<File> = runCatching {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "${fileNamePrefix}_${fileStampFormat.format(Date())}.txt"
        val outFile = File(exportDir, fileName)
        outFile.writeText(content)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "导出 VoltPulse 诊断日志").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        outFile
    }

    fun copyToClipboard(context: Context, label: String, content: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, content))
    }

    fun describeDisplay(display: Display?): String {
        if (display == null) return "unknown"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val active = display.mode
            val modes = display.supportedModes
                .filter { it.physicalWidth == active.physicalWidth && it.physicalHeight == active.physicalHeight }
                .map { "${it.refreshRate.toInt()}Hz(id=${it.modeId})" }
                .distinct()
                .joinToString("/")
            "当前=${active.refreshRate.toInt()}Hz (${active.physicalWidth}x${active.physicalHeight}, id=${active.modeId}) | 同分辨率支持=[$modes]"
        } else {
            "refreshRate=${display.refreshRate.toInt()}Hz"
        }
    }

    private fun fmt(v: Float, decimals: Int = 1): String =
        String.format(Locale.US, "%.${decimals}f", v)
}
