package com.example.pandatemperature.data.model

/**
 * 设备状态数据类
 */
data class DeviceStatus(
    /**
     * 采集间隔（秒）
     */
    val interval: Int,
    
    /**
     * 存储的记录数
     */
    val recordCount: Int,
    
    /**
     * 设备连接状态（从设备状态标志位解析）
     */
    val isConnected: Boolean,
    
    /**
     * 时间同步状态（从设备状态标志位解析）
     */
    val isTimeSynced: Boolean,

    /**
     * 固件版本号
     */
    val firmwareVersion: Int = 0,

    /**
     * 是否正在清空数据（从设备状态标志位解析）
     */
    val isDataClearInProgress: Boolean = false
)
