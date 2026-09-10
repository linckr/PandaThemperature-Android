package com.example.pandatemperature.data.device.parser

import com.example.pandatemperature.data.model.DeviceStatus
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 设备状态解析器
 * 解析设备状态特征数据
 * 
 * 数据格式（小端序）：
 * - 字节 0-1: interval (uint16) - 采集间隔（秒）
 * - 字节 2-3: recordCount (uint16) - 记录数
 * - 字节 4: statusFlags (uint8) - 状态标志
 *   - bit 0: isConnected
 *   - bit 1: isTimeSynced
 *   - bit 2: isDataClearInProgress
 * - 字节 5: reserved (uint8) - 保留
 * - 字节 6-7: firmwareVersion (uint16) - 固件版本号（可选，老固件可能没有）
 */
class DeviceStatusParser : DataParser<DeviceStatus> {
    
    override val expectedMinLength: Int = 5  // 最小需要 5 字节（interval + recordCount + statusFlags）
    
    override fun canParse(data: ByteArray): Boolean {
        return data.size >= expectedMinLength
    }
    
    override fun parse(data: ByteArray): DeviceStatus? {
        if (!canParse(data)) return null
        
        return try {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            
            // 采集间隔（uint16）
            val interval = buffer.short.toInt() and 0xFFFF
            
            // 记录数（uint16）
            val recordCount = buffer.short.toInt() and 0xFFFF
            
            // 状态标志（uint8）
            val statusFlags = buffer.get().toInt() and 0xFF
            val isConnected = (statusFlags and 0x01) != 0
            val isTimeSynced = (statusFlags and 0x02) != 0
            val isDataClearInProgress = (statusFlags and 0x04) != 0
            
            // 固件版本号（如果数据足够长）
            val firmwareVersion = if (data.size >= 8) {
                buffer.get()  // 跳过 reserved
                buffer.short.toInt() and 0xFFFF
            } else {
                0  // 老固件没有版本号
            }
            
            DeviceStatus(
                interval = interval,
                recordCount = recordCount,
                isConnected = isConnected,
                isTimeSynced = isTimeSynced,
                firmwareVersion = firmwareVersion,
                isDataClearInProgress = isDataClearInProgress
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 格式化状态信息为日志字符串
     */
    fun formatForLog(status: DeviceStatus): String {
        val clearStatusStr = if (status.isDataClearInProgress) "进行中" else "空闲"
        return "间隔:${status.interval}秒, 记录数:${status.recordCount}, 清空:${clearStatusStr}, 固件:v${status.firmwareVersion}"
    }
}
