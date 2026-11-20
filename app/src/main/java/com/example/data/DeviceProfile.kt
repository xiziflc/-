package com.example.data

import android.os.BatteryManager
import android.os.Build

enum class DeviceProfileId(val displayName: String, val shortCode: String) {
    AUTO("自动识别当前设备", "AUTO"),
    OPPO_FIND_X8_ULTRA("OPPO Find X8 Ultra", "FIND X8U"),
    XIAOMI_13("小米 13", "MI 13"),
    NUT_PRO_3("坚果 Pro 3", "NUT PRO 3")
}

enum class ProtocolKind(
    val shortLabel: String,
    val fullLabel: String,
    val isProprietary: Boolean
) {
    SUPERVOOC("SuperVOOC", "OPPO SuperVOOC 私有闪充", true),
    MI_TURBO("Mi Turbo", "小米澎湃秒充 私有协议", true),
    PPS("USB PPS", "USB PD 3.0 PPS 调压快充", false),
    PD("USB PD", "USB PD 通用快充", false),
    WIRELESS_PRIVATE("私有无线", "私有无线闪充协议", true),
    STANDARD("DCP / USB", "常规 5V 充电", false),
    TRICKLE("TRICKLE", "满电涓流养护", false),
    DISCHARGING("OFFLINE", "未接入电源 / 放电中", false)
}

enum class ProtocolDetectionSource(val label: String) {
    SYSFS_KERNEL("内核节点直读"),
    POWER_SIGNATURE("功率特征推断"),
    IDLE("待机监测")
}

data class ProtocolSupportSpec(
    val kind: ProtocolKind,
    val maxWatts: Int,
    val note: String
)

data class DeviceHardwareProfile(
    val id: DeviceProfileId,
    val displayName: String,
    val cellArchitecture: String,
    val defaultCapacityMah: Int,
    val defaultVoltageMultiplier: Float,
    val defaultCurrentMultiplier: Float,
    val maxWiredWatts: Int,
    val maxPpsWatts: Int,
    val maxPdWatts: Int,
    val proprietaryName: String?,
    val protocolSpecs: List<ProtocolSupportSpec>,
    val calibrationTip: String
)

data class DetectedProtocol(
    val kind: ProtocolKind,
    val label: String,
    val source: ProtocolDetectionSource,
    val detail: String
)

object DeviceProfileCatalog {

    val oppoFindX8Ultra = DeviceHardwareProfile(
        id = DeviceProfileId.OPPO_FIND_X8_ULTRA,
        displayName = "OPPO Find X8 Ultra",
        cellArchitecture = "串联双电芯 · 6100mAh 硅碳",
        defaultCapacityMah = 6100,
        defaultVoltageMultiplier = 2.0f,
        defaultCurrentMultiplier = 1.0f,
        maxWiredWatts = 100,
        maxPpsWatts = 55,
        maxPdWatts = 27,
        proprietaryName = "100W SuperVOOC",
        protocolSpecs = listOf(
            ProtocolSupportSpec(ProtocolKind.SUPERVOOC, 100, "11V/9.1A 私有闪充"),
            ProtocolSupportSpec(ProtocolKind.PPS, 55, "55W 宽幅 PPS / UFCS"),
            ProtocolSupportSpec(ProtocolKind.PD, 27, "9V/3A 通用 PD"),
            ProtocolSupportSpec(ProtocolKind.WIRELESS_PRIVATE, 50, "50W AirVOOC")
        ),
        calibrationTip = "Find X8 Ultra 采用串联双电芯架构，系统接口常仅报单电芯电压 (~4.4V)，已自动启用电压 ×2.0 校准还原真实 100W 功率。"
    )

    val xiaomi13 = DeviceHardwareProfile(
        id = DeviceProfileId.XIAOMI_13,
        displayName = "小米 13",
        cellArchitecture = "单电芯 2:1 电荷泵 · 4500mAh",
        defaultCapacityMah = 4500,
        defaultVoltageMultiplier = 1.0f,
        defaultCurrentMultiplier = 1.0f,
        maxWiredWatts = 67,
        maxPpsWatts = 33,
        maxPdWatts = 27,
        proprietaryName = "67W Mi Turbo",
        protocolSpecs = listOf(
            ProtocolSupportSpec(ProtocolKind.MI_TURBO, 67, "魔改 PD+PPS 私有握手"),
            ProtocolSupportSpec(ProtocolKind.PPS, 33, "33W 通用 PPS"),
            ProtocolSupportSpec(ProtocolKind.PD, 27, "9V/3A 通用 PD 3.0"),
            ProtocolSupportSpec(ProtocolKind.WIRELESS_PRIVATE, 50, "50W 小米无线秒充")
        ),
        calibrationTip = "小米 13 为单电芯电荷泵方案，电压保持 ×1.0；澎湃/MIUI 内核常将充电电流上报为负值，系统已自动修正为正向充电读数。"
    )

    val nutPro3 = DeviceHardwareProfile(
        id = DeviceProfileId.NUT_PRO_3,
        displayName = "坚果 Pro 3",
        cellArchitecture = "高通单电芯 · 4000mAh",
        defaultCapacityMah = 4000,
        defaultVoltageMultiplier = 1.0f,
        defaultCurrentMultiplier = 1.0f,
        maxWiredWatts = 18,
        maxPpsWatts = 18,
        maxPdWatts = 18,
        proprietaryName = null,
        protocolSpecs = listOf(
            ProtocolSupportSpec(ProtocolKind.PPS, 18, "QC4+ / PPS 18W 调压"),
            ProtocolSupportSpec(ProtocolKind.PD, 18, "USB PD 3.0 9V/2A"),
            ProtocolSupportSpec(ProtocolKind.STANDARD, 10, "5V/2A DCP 标准充")
        ),
        calibrationTip = "坚果 Pro 3 (骁龙 855+) 支持标准 18W QC4+ / USB PD / PPS，单电芯架构使用 ×1.0 校准，老机型内核对 usb/real_type 节点开放度通常更高。"
    )

    val genericAutoFallback = DeviceHardwareProfile(
        id = DeviceProfileId.AUTO,
        displayName = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "通用 Android 设备" },
        cellArchitecture = "自动探测电池架构",
        defaultCapacityMah = 5000,
        defaultVoltageMultiplier = 1.0f,
        defaultCurrentMultiplier = 1.0f,
        maxWiredWatts = 65,
        maxPpsWatts = 45,
        maxPdWatts = 27,
        proprietaryName = "多协议兼容",
        protocolSpecs = listOf(
            ProtocolSupportSpec(ProtocolKind.SUPERVOOC, 100, "OPPO 私有通道"),
            ProtocolSupportSpec(ProtocolKind.MI_TURBO, 67, "小米私有通道"),
            ProtocolSupportSpec(ProtocolKind.PPS, 55, "USB PPS 通道"),
            ProtocolSupportSpec(ProtocolKind.PD, 27, "USB PD 通道")
        ),
        calibrationTip = "未匹配到三款预设机型型号，当前按通用参数运行；可在下方一键切换为 Find X8 Ultra / 小米 13 / 坚果 Pro 3 预设。"
    )

    val selectableProfiles: List<DeviceHardwareProfile> = listOf(
        oppoFindX8Ultra,
        xiaomi13,
        nutPro3
    )

    fun detectCurrentDeviceProfile(): DeviceHardwareProfile {
        val model = Build.MODEL.orEmpty().lowercase()
        val device = Build.DEVICE.orEmpty().lowercase()
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()

        return when {
            model.contains("pku110") || model.contains("x8 ultra") ||
                model.contains("find x8") || (manufacturer.contains("oppo") && model.contains("x8")) ->
                oppoFindX8Ultra
            model.contains("2211133") || device.contains("fuxi") ||
                (manufacturer.contains("xiaomi") && model.contains("13") && !model.contains("13t")) ->
                xiaomi13
            model.contains("dt1901") || device.contains("delta") ||
                manufacturer.contains("smartisan") || model.contains("nut pro 3") ->
                nutPro3
            else -> genericAutoFallback
        }
    }

    fun resolveProfile(id: DeviceProfileId): DeviceHardwareProfile {
        return when (id) {
            DeviceProfileId.AUTO -> detectCurrentDeviceProfile()
            DeviceProfileId.OPPO_FIND_X8_ULTRA -> oppoFindX8Ultra
            DeviceProfileId.XIAOMI_13 -> xiaomi13
            DeviceProfileId.NUT_PRO_3 -> nutPro3
        }
    }
}

object ChargingProtocolAnalyzer {

    fun analyze(
        profile: DeviceHardwareProfile,
        isPlugged: Boolean,
        isFull: Boolean,
        pluggedType: Int,
        powerWatts: Float,
        sysfsHints: Map<String, String>
    ): DetectedProtocol {
        if (!isPlugged) {
            return DetectedProtocol(
                kind = ProtocolKind.DISCHARGING,
                label = "OFFLINE // 电池放电",
                source = ProtocolDetectionSource.IDLE,
                detail = "未接入充电器"
            )
        }
        if (isFull || powerWatts < 1.2f) {
            return DetectedProtocol(
                kind = ProtocolKind.TRICKLE,
                label = "TRICKLE // 满电涓流",
                source = ProtocolDetectionSource.POWER_SIGNATURE,
                detail = "恒压末端养护阶段"
            )
        }

        // Layer 1: Direct non-root readable kernel sysfs signals
        val voocActive = sysfsHints["voocchg_ing"]?.trim()
        val oplusFast = sysfsHints["oplus_fastchg"]?.trim()
        if (voocActive == "1" || oplusFast == "1" || oplusFast?.contains("vooc", true) == true) {
            return DetectedProtocol(
                kind = ProtocolKind.SUPERVOOC,
                label = "SuperVOOC 私有闪充",
                source = ProtocolDetectionSource.SYSFS_KERNEL,
                detail = "内核 vooc 节点确认握手"
            )
        }

        val miQcType = sysfsHints["mi_quick_charge"]?.trim()?.toIntOrNull()
        if (miQcType != null && miQcType >= 2) {
            val tier = when (miQcType) {
                4 -> "Hyper / 67W+ 澎湃秒充"
                3 -> "Mi Turbo 私有秒充"
                else -> "小米闪充协议"
            }
            return DetectedProtocol(
                kind = ProtocolKind.MI_TURBO,
                label = tier,
                source = ProtocolDetectionSource.SYSFS_KERNEL,
                detail = "quick_charge_type = $miQcType"
            )
        }

        val usbRealType = sysfsHints["usb_real_type"]?.uppercase().orEmpty()
        val pdActive = sysfsHints["usb_pd_active"]?.trim()
        if (usbRealType.contains("PPS") || pdActive == "2") {
            return DetectedProtocol(
                kind = ProtocolKind.PPS,
                label = "USB PD PPS 调压快充",
                source = ProtocolDetectionSource.SYSFS_KERNEL,
                detail = "usb/real_type = ${usbRealType.ifBlank { "PD_PPS" }}"
            )
        }
        if (usbRealType.contains("USB_PD") || pdActive == "1") {
            return DetectedProtocol(
                kind = ProtocolKind.PD,
                label = "USB PD 通用快充",
                source = ProtocolDetectionSource.SYSFS_KERNEL,
                detail = "usb/real_type = ${usbRealType.ifBlank { "USB_PD" }}"
            )
        }

        // Layer 2: Wireless check
        if (pluggedType == BatteryManager.BATTERY_PLUGGED_WIRELESS) {
            return if (powerWatts >= 14f) {
                val label = when (profile.id) {
                    DeviceProfileId.OPPO_FIND_X8_ULTRA -> "AirVOOC 私有无线闪充"
                    DeviceProfileId.XIAOMI_13 -> "小米私有无线秒充"
                    else -> "高速无线快充"
                }
                DetectedProtocol(
                    kind = ProtocolKind.WIRELESS_PRIVATE,
                    label = label,
                    source = ProtocolDetectionSource.POWER_SIGNATURE,
                    detail = "高功率无线感应链路"
                )
            } else {
                DetectedProtocol(
                    kind = ProtocolKind.STANDARD,
                    label = "Qi 标准无线充电",
                    source = ProtocolDetectionSource.POWER_SIGNATURE,
                    detail = "基础 5W–10W 无线链路"
                )
            }
        }

        // Layer 3: Device-aware power envelope signature inference when SELinux hides sysfs
        return when (profile.id) {
            DeviceProfileId.OPPO_FIND_X8_ULTRA -> when {
                powerWatts >= 56f -> DetectedProtocol(
                    kind = ProtocolKind.SUPERVOOC,
                    label = "SuperVOOC 私有闪充",
                    source = ProtocolDetectionSource.POWER_SIGNATURE,
                    detail = "功率突破 55W PPS 上限，判定 OPPO 私有通道"
                )
                powerWatts >= 28f -> DetectedProtocol(
                    kind = ProtocolKind.PPS,
                    label = "55W PPS / SuperVOOC 调压",
                    source = ProtocolDetectionSource.POWER_SIGNATURE,
                    detail = "28W–55W 中高功率调压区间"
                )
                powerWatts >= 13f -> DetectedProtocol(
                    kind = ProtocolKind.PD,
                    label = "USB PD 通用快充",
                    source = ProtocolDetectionSource.POWER_SIGNATURE,
                    detail = "9V 档位通用 PD 功率特征"
                )
                else -> DetectedProtocol(
                    kind = ProtocolKind.STANDARD,
                    label = "USB 常规充电",
                    source = ProtocolDetectionSource.POWER_SIGNATURE,
                    detail = "5V 基础供电档位"
                )
            }

            DeviceProfileId.XIAOMI_13 -> when {
                powerWatts >= 35f -> DetectedProtocol(
                    kind = ProtocolKind.MI_TURBO,
                    label = "Mi Turbo 67W 澎湃秒充",
                    source = ProtocolDetectionSource.POWER_SIGNATURE,
                    detail = "功率高于 33W 通用 PPS，判定小米私有握手"
                )
                powerWatts >= 22f -> DetectedProtocol(
                    kind = ProtocolKind.PPS,
                    label = "USB PD PPS 快充",
                    source = ProtocolDetectionSource.POWER_SIGNATURE,
                    detail = "22W–33W 电荷泵 PPS 调压区间"
                )
                powerWatts >= 12f -> DetectedProtocol(
                    kind = ProtocolKind.PD,
                    label = "USB PD 3.0 快充",
                    source = ProtocolDetectionSource.POWER_SIGNATURE,
                    detail = "9V 通用 PD 功率区间"
                )
                else -> DetectedProtocol(
                    kind = ProtocolKind.STANDARD,
                    label = "USB 常规充电",
                    source = ProtocolDetectionSource.POWER_SIGNATURE,
                    detail = "5V 基础供电档位"
                )
            }

            DeviceProfileId.NUT_PRO_3 -> when {
                powerWatts >= 14.5f -> DetectedProtocol(
                    kind = ProtocolKind.PPS,
                    label = "QC4+ / USB PPS (18W)",
                    source = ProtocolDetectionSource.POWER_SIGNATURE,
                    detail = "接近 18W 满速调压档"
                )
                powerWatts >= 10.5f -> DetectedProtocol(
                    kind = ProtocolKind.PD,
                    label = "USB PD 3.0 快充",
                    source = ProtocolDetectionSource.POWER_SIGNATURE,
                    detail = "9V 档位快充特征"
                )
                else -> DetectedProtocol(
                    kind = ProtocolKind.STANDARD,
                    label = "5V 标准充电 (DCP)",
                    source = ProtocolDetectionSource.POWER_SIGNATURE,
                    detail = "标准 5V 供电"
                )
            }

            DeviceProfileId.AUTO -> when {
                powerWatts >= 45f -> DetectedProtocol(
                    kind = ProtocolKind.PPS,
                    label = "高功率私有 / PPS 闪充",
                    source = ProtocolDetectionSource.POWER_SIGNATURE,
                    detail = "可在校准页选择具体机型提升识别精度"
                )
                powerWatts >= 20f -> DetectedProtocol(
                    kind = ProtocolKind.PPS,
                    label = "USB PPS 快充",
                    source = ProtocolDetectionSource.POWER_SIGNATURE,
                    detail = "中高速调压快充区间"
                )
                powerWatts >= 11f -> DetectedProtocol(
                    kind = ProtocolKind.PD,
                    label = "USB PD 快充",
                    source = ProtocolDetectionSource.POWER_SIGNATURE,
                    detail = "9V 通用 PD 区间"
                )
                else -> DetectedProtocol(
                    kind = ProtocolKind.STANDARD,
                    label = "USB 标准充电",
                    source = ProtocolDetectionSource.POWER_SIGNATURE,
                    detail = "基础 5V 供电"
                )
            }
        }
    }
}
