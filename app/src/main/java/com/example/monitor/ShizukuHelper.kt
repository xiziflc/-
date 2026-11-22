package com.example.monitor

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.example.data.DiagnosticLogger
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

data class ShizukuStatus(
    val isInstalled: Boolean = false,
    val isBinderAlive: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val dozeWhitelisted: Boolean = false,
    val runInBackgroundAllowed: Boolean = false,
    val standbyBucket: String = "未知",
    val lastActionMessage: String = ""
) {
    val isReadyForHardening: Boolean
        get() = isInstalled && isBinderAlive && isPermissionGranted

    val isHardened: Boolean
        get() = isReadyForHardening && dozeWhitelisted && runInBackgroundAllowed
}

object ShizukuHelper {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val REQUEST_CODE_SHIZUKU = 9104

    fun isShizukuInstalled(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                SHIZUKU_PACKAGE,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        }
        true
    }.getOrDefault(false)

    fun checkStatus(context: Context, lastMessage: String = ""): ShizukuStatus {
        val installed = isShizukuInstalled(context)
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val granted = if (binderAlive) {
            runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
        } else {
            false
        }

        if (!binderAlive || !granted) {
            return ShizukuStatus(
                isInstalled = installed,
                isBinderAlive = binderAlive,
                isPermissionGranted = granted,
                lastActionMessage = lastMessage
            )
        }

        val pkg = context.packageName
        val wlOut = execShell("cmd deviceidle whitelist").getOrDefault("")
        val dozeWhitelisted = wlOut.lines().any { it.contains(pkg) }

        val appopsOut = execShell("appops get $pkg RUN_ANY_IN_BACKGROUND").getOrDefault("")
        val runInBgAllowed = appopsOut.contains("allow", ignoreCase = true)

        val bucketOut = execShell("am get-standby-bucket $pkg").getOrDefault("").trim()
        val bucketLabel = when {
            bucketOut.endsWith("10") || bucketOut.contains("ACTIVE", true) -> "ACTIVE(10)"
            bucketOut.endsWith("20") || bucketOut.contains("WORKING_SET", true) -> "WORKING_SET(20)"
            bucketOut.endsWith("30") || bucketOut.contains("FREQUENT", true) -> "FREQUENT(30)"
            bucketOut.endsWith("40") || bucketOut.contains("RARE", true) -> "RARE(40)"
            bucketOut.endsWith("45") || bucketOut.contains("RESTRICTED", true) -> "RESTRICTED(45)"
            bucketOut.isNotBlank() -> bucketOut
            else -> "未知"
        }

        return ShizukuStatus(
            isInstalled = installed,
            isBinderAlive = true,
            isPermissionGranted = true,
            dozeWhitelisted = dozeWhitelisted,
            runInBackgroundAllowed = runInBgAllowed,
            standbyBucket = bucketLabel,
            lastActionMessage = lastMessage
        )
    }

    fun requestPermission() {
        runCatching {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
                DiagnosticLogger.log("SHIZUKU", "Requested Shizuku permission")
            }
        }.onFailure {
            DiagnosticLogger.log("SHIZUKU", "requestPermission failed: ${it.message}")
        }
    }

    fun applyKeepAliveHardening(context: Context): ShizukuStatus {
        val pkg = context.packageName
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            return checkStatus(context, "Shizuku 服务未运行，请先启动 Shizuku")
        }
        if (runCatching { Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED }.getOrDefault(true)) {
            requestPermission()
            return checkStatus(context, "请先在弹窗中允许 Shizuku 授权")
        }

        val commands = listOf(
            "cmd deviceidle whitelist +$pkg",
            "appops set $pkg RUN_IN_BACKGROUND allow",
            "appops set $pkg RUN_ANY_IN_BACKGROUND allow",
            "appops set $pkg WAKE_LOCK allow",
            "am set-standby-bucket $pkg active"
        )

        val results = commands.map { cmd ->
            val res = execShell(cmd)
            DiagnosticLogger.log("SHIZUKU", "exec `$cmd` -> ${res.getOrElse { "ERR: ${it.message}" }.trim()}")
            res.isSuccess
        }

        val msg = if (results.all { it }) {
            "已完成 Doze 白名单、后台运行与活跃桶加固"
        } else {
            "部分加固命令未返回成功 (${results.count { it }}/${results.size})，详情见诊断日志"
        }
        return checkStatus(context, msg)
    }

    fun openShizukuApp(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(launchIntent) }
        } else {
            openShizukuDownload(context)
        }
    }

    fun openShizukuDownload(context: Context) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://shizuku.rikka.app/zh-hans/download/")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        runCatching { context.startActivity(intent) }
    }

    private fun execShell(command: String): Result<String> = runCatching {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        ).apply { isAccessible = true }
        val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
        val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
        val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()
        val exitCode = process.waitFor()
        if (exitCode != 0 && stdout.isBlank() && stderr.isNotBlank()) {
            error("exit=$exitCode: $stderr")
        }
        stdout.ifBlank { stderr }
    }
}
