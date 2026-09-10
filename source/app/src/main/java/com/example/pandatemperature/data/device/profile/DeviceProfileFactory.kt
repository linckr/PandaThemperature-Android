package com.example.pandatemperature.data.device.profile

import com.example.pandatemperature.data.bluetooth.BleConstants
import com.example.pandatemperature.data.device.model.DeviceTypes
import com.example.pandatemperature.data.device.model.HistoryRecord
import com.example.pandatemperature.data.device.model.SensorData
import com.example.pandatemperature.data.device.profile.thermometer.ThermometerProfile
import com.example.pandatemperature.data.device.profile.thermometer.ThermometerV1Profile
import com.example.pandatemperature.data.device.profile.thermometer.ThermometerV2Profile
import com.example.pandatemperature.data.device.profile.thermometer.ThermometerV3Profile

/**
 * 设备配置工厂
 * 根据设备类型、固件版本、可用服务创建对应的设备配置
 */
object DeviceProfileFactory {
    
    /**
     * 创建设备配置（通用方法）
     * @param deviceType 设备类型（字符串常量，参见 DeviceTypes）
     * @param firmwareVersion 固件版本号，0 或 null 表示老固件
     * @param availableServiceUuids 设备支持的服务 UUID 集合
     * @return 对应的设备配置
     */
    fun <T : SensorData, H : HistoryRecord> createProfile(
        deviceType: String,
        firmwareVersion: Int?,
        availableServiceUuids: Set<String> = emptySet()
    ): DeviceProfile<T, H>? {
        @Suppress("UNCHECKED_CAST")
        return when (deviceType) {
            DeviceTypes.THERMOMETER -> createThermometerProfile(firmwareVersion, availableServiceUuids) as? DeviceProfile<T, H>
            DeviceTypes.UNKNOWN -> createThermometerProfile(firmwareVersion, availableServiceUuids) as? DeviceProfile<T, H>
            else -> null
        }
    }
    
    /**
     * 创建温度计配置（专用方法）
     * @param firmwareVersion 固件版本号
     * @param availableServiceUuids 可用服务 UUID
     * @return 温度计配置
     */
    fun createThermometerProfile(
        firmwareVersion: Int?,
        availableServiceUuids: Set<String> = emptySet()
    ): ThermometerProfile {
        // 判断是否为新固件：
        // 1. 有固件版本号且 > 0
        // 2. 支持实时数据服务
        val isNewFirmware = (firmwareVersion != null && firmwareVersion > 0) ||
                availableServiceUuids.contains(BleConstants.REALTIME_DATA_SERVICE)
        
        // 旧固件状态字段沿用两位整数表示 v1.2（12）；将其归一化为主版本后，
        // 既能按需求识别 v3+，也不会把现有 v1.x/v2.x 设备误判成 v3。
        val majorVersion = firmwareVersion?.let { if (it >= 10) it / 10 else it }

        return when {
            majorVersion != null && majorVersion >= 3 ->
                ThermometerV3Profile(firmwareVersion ?: 3)
            isNewFirmware ->
                ThermometerV2Profile(firmwareVersion ?: 0)
            else ->
                ThermometerV1Profile()
        }
    }
    
    /**
     * 根据固件版本快速判断是否为新固件
     * 用于在读取设备状态后快速判断
     */
    fun isNewFirmware(firmwareVersion: Int?): Boolean {
        return firmwareVersion != null && firmwareVersion > 0
    }
}
