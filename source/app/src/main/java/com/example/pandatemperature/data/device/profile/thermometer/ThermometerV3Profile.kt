package com.example.pandatemperature.data.device.profile.thermometer

import com.example.pandatemperature.data.device.parser.DataParser
import com.example.pandatemperature.data.device.parser.HistoryDataParser
import com.example.pandatemperature.data.device.parser.HistoryRecordFormat
import com.example.pandatemperature.data.model.TemperatureRecord

/**
 * 温度计 V3 配置（固件版本 >= 3）。
 *
 * 实时特征仍沿用 V2 的合并格式；历史记录显式使用 14 字节格式，
 * 末尾两个字节为随温湿度数据上报的电池毫伏值。
 */
class ThermometerV3Profile(
    firmwareVersion: Int
) : ThermometerV2Profile(firmwareVersion) {

    override fun getHistoryParser(deviceId: String): DataParser<List<TemperatureRecord>> {
        return HistoryDataParser(deviceId, HistoryRecordFormat.V3)
    }
}
