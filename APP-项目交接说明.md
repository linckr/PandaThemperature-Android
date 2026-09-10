# PandaThemperature-Android 项目交接说明

更新时间：2026-09-11（第三轮，Asia/Shanghai）  
交接对象：后续继续推进本项目的 AI / 开发者

## 0. 一句话结论

本仓库已完成三轮 App 侧工作。

- **第一轮（2026-09-08）**：实现「启动自动连接最近设备」与「电池电压展示」，留下三个未闭环问题（硬件协议未验证、Release 构建缺失、`source/` 未纳入版本控制）。
- **第二轮（2026-09-10）**：新增 BLE 原始帧诊断能力（联调抓手）、补齐 Release 签名配置（过程中修复了一个真实依赖缺陷）、`source/` 完整纳入 Git。
- **第三轮（2026-09-11）**：**P0 真机验证完成**；按用户要求**移除 NFC 墨水屏挂件图传模块**；**补上首页电压预留位**；Release 改为可用调试密钥签名安装。

**本轮最重要的结论是 P0 验证结果与既有假设不符**：真实设备（固件 v2）的实时数据帧是 **6 字节、不含电压字段**，历史记录是 **V2（12 字节/条、不含电压）**。也就是说，**这台设备的固件根本不上报电压**——首页电压显示为 `-- V` 是硬件事实，不是 App 解析失败。

## 1. 项目、仓库和当前工作区

- App 项目：`PandaThemperature-Android`
- 原始仓库：<https://github.com/yodfz/PandaThemperature-Android>
- 当前 fork：<https://github.com/linckr/PandaThemperature-Android>
- 本地仓库：`C:\Users\linckr\Documents\Codex\2026-09-08\referenced-chatgpt-conversation-this-is-an\PandaThemperature-Android`
- Android Gradle 工程：本地仓库下的 `source` 目录
- 硬件/固件项目：<https://github.com/yodfz/NRF52xxx-FieldTemp>

Git 当前状态（第三轮结束后）：

- 分支：`main`
- 提交历史：
  - `e36f97c` 移除 NFC 墨水屏挂件模块，补首页电压预留位，P0 真机验证完成（**本轮**）
  - `23623da` 整理诊断模块常量声明位置，对齐最终构建产物哈希（第二轮）
  - `b099cb7` 更新交接说明并重新打包 src.zip（第二轮）
  - `5fc3338` 补齐交付闭环：BLE 联调诊断、Release 签名入口与源码入库（第二轮）
  - `1fa71a2` 增加自动连接与电池电压展示（第一轮）
  - `4f34230` ADD README.MD（上游）
  - `8ad8501` init（上游）
- `origin`: `https://github.com/linckr/PandaThemperature-Android.git`
- `upstream`: <https://github.com/yodfz/PandaThemperature-Android.git>

**R4 已解决**：`source/` 完整源码已纳入 Git。`git show HEAD` 可看到 `source/app/...` 的逐文件差异。

当前推荐入口：

~~~text
C:\Users\linckr\Documents\Codex\2026-09-08\referenced-chatgpt-conversation-this-is-an\PandaThemperature-Android\source
~~~

不要提交机器专属文件：`source\local.properties`、`source\.gradle`、`source\.kotlin`、`source\app\build`、`source\keystore.properties`、任何 `*.jks` / `*.keystore`。这些规则已写入 `source\.gitignore`，压缩源码时也不要把它们放入 `src.zip`。

## 2. 需求来源

### 2.1 第一轮需求

**启动自动连接**：打开 App 后自动连接「设备管理」中添加时间最新的设备；用户主动断开后仍可手动重新呼出设备选择界面。

**电池电压显示**（不修改固件的第一阶段，先让 App 具备兼容和展示能力）：

1. 首页顶部设备名后显示最新电压，例如 `PandaTemp_1234 (v2) 2.9V`。
2. 首页「设备配置」弹窗中，在固件版本和存储记录之间显示电压快照。
3. 历史数据每行在温度、湿度下方显示该条记录对应的电压。
4. 设置页「设备管理」列表中，在固件版本下方、添加时间上方显示该设备最新电压。

### 2.2 第三轮需求

1. **完成 P0 真机验证**（用户在电脑上连接了安卓设备，可通过 adb 操作）。
2. **在 App 里移除独立的 NFC 墨水屏挂件图传模块**。
3. **首页设备名称右侧补电压预留位**（用户观察到该位置为空）。
4. **签名问题暂不处理，用 Android 默认调试密钥自动签名**即可。

## 3. 已完成的功能

### 3.1 启动自动连接（第一轮）

主要代码位置：

- `source\app\src\main\java\com\example\pandatemperature\MainActivity.kt`
- `source\app\src\main\java\com\example\pandatemperature\ui\viewmodel\MainViewModel.kt`
- `source\app\src\main\java\com\example\pandatemperature\data\database\dao\DeviceDao.kt`

实现逻辑：

- 蓝牙权限和蓝牙开关准备完成后，执行一次启动自动连接流程。
- 从 `devices` 表按 `createTime` 查询最近添加的设备（`DeviceDao.getLatestDevice()`）。
- 有保存设备时按 MAC 地址直接连接，不主动弹出设备选择框。
- 没有保存设备，或自动连接失败时，保留原来的手动选择入口。
- 用户主动断开时设置本次运行的手动断开状态，避免 App 启动流程重复自动连接（`startupAutoConnectStarted` 一次性守卫）。
- 手动进入设备选择、选择其他设备、断开后重新连接的原有流程保留。

关键方法：

- `MainActivity.BluetoothReadyAction.STARTUP_AUTO_CONNECT`
- `MainViewModel.autoConnectLatestSavedDevice()`

**第三轮真机实测**：安装后首次启动可自动连接 `PandaTemp_7086` 并完成历史同步（1778/1751 条），行为符合预期。

### 3.2 电压字段和协议解析（第一轮）

新增/扩展的数据字段：

- `RealtimeData.batteryVoltage: Float?`
- `TemperatureRecord.batteryVoltage: Float?`
- `Device.latestBatteryVoltage: Float?`

实时数据（`RealtimeDataParser.kt`）：

- 基础帧 6 字节继续兼容：温度(2) + 湿度(2) + 气压(2)。
- 帧长 ≥ 8 时读取 offset 6 的小端 `uint16`，单位为 mV，再转换为 V。
- 因此未来固件可在原 6 字节末尾追加 2 字节电压，而旧固件仍能正常解析。
- 协议常量已具名化：`FRAME_SIZE_BASE`(6)、`BATTERY_VOLTAGE_OFFSET`(6)、`BATTERY_VOLTAGE_SIZE`(2)、`FRAME_SIZE_WITH_VOLTAGE`(8)。诊断与解析共用这一处定义。

历史数据（`HistoryDataParser.kt`）：

- `HistoryRecordFormat.V1`：8 字节，无气压、无电压。
- `HistoryRecordFormat.V2`：12 字节，含气压、无电压。
- `HistoryRecordFormat.V3`：14 字节，含气压和电压。
- V3 中电压为记录偏移 `VOLTAGE_OFFSET_IN_V3`(12) 的小端 `uint16` 毫伏值。
- 历史解析器通过设备 Profile/固件版本选择 V1/V2/V3（`DeviceProfileFactory.createThermometerProfile`），**不按数据包长度模糊猜测**。

**第三轮真机确认**：本机设备固件 v2 → Profile 选择 V2 → 12 字节/条，与实测一致。**这套「按 Profile 而非按长度」的判定方式是本项目硬约束，不要改成猜测式实现。**

### 3.3 Room 数据库迁移和持久化（第一轮）

- Room 数据库版本从 9 升至 10。
- `temperature_records` 增加可空 `batteryVoltage`。
- `devices` 增加可空 `latestBatteryVoltage`。
- 增加完整的 `MIGRATION_9_10`，不使用破坏性迁移。
- 补注册 `MIGRATION_8_9`，避免旧版本升级时掉入 destructive fallback。
- 实时数据到达时更新当前设备最新电压（`updateBatteryVoltage` 带空值守卫：null 不会覆盖已存电压）。
- 本机定时写入历史记录时保存当前电压。
- 从设备同步历史记录时保存该条记录中解析出的电压。
- 旧数据库、旧历史记录没有电压时使用 `null`，UI 显示 `--`。

### 3.4 四处电压 UI（第一轮，第三轮有调整）

- 首页状态栏：`StatusBar.kt`，连接状态下显示电压（**第三轮改为常驻预留位，见 3.10**）。
- 设备配置弹窗：`ConfigModal.kt`，打开弹窗时显示当前电压快照；无数据为 `--`。
- 历史记录：`HistoryCard.kt`，每条记录显示自己的 `batteryVoltage`；旧记录为 `-- V`。
- 设备管理：`DeviceManagementScreen.kt`，显示设备表中的 `latestBatteryVoltage`；无数据为 `电池电压: --`。

没有根据 CR2032 电压推算剩余百分比，也没有把电压伪装成电量百分比。

### 3.5 BLE 原始帧最小诊断（第二轮新增）

**这是第二轮最重要的新增能力，直接服务于 P0 硬件协议确认。**

此前 App 没有任何原始帧输出，联调必须依赖外部 BLE 抓包工具。现在连上设备后，App 日志会直接打印协议事实。

代码位置：

- `source\app\src\main\java\com\example\pandatemperature\data\bluetooth\BleFrameDiagnostics.kt`（新模块）
- 接入点：`MainViewModel` 的实时订阅回调、实时单次读取、历史包消费三处

输出示例（**均为第三轮真机实测**）：

~~~text
D/MainViewModel: [INFO] BLE诊断 实时数据: 帧长=6B（不含电压字段，长度未达 8B），Hex=97 09 5F 13 81 27
D/MainViewModel: [INFO] BLE诊断 历史数据: 帧长=12B，格式=V2（单条 12B），完整记录=1 条，尾部余 0 B，Hex=...
D/MainViewModel: [INFO] BLE诊断 历史数据: 帧长=60B，格式=V2（单条 12B），完整记录=5 条，尾部余 0 B，Hex=...
~~~

去重策略（刻意保守，避免刷屏又不错过关键转折）：

- 同一通道首次出现时报告一次。
- 之后**仅当帧长发生变化时**再次报告——帧长变化正是固件升级的关键信号。
- 断开连接时 `reset()`，下一次连接重新报告基线。

**关键约束：不改变任何协议判定行为。** 历史格式仍由 `DeviceProfile`/固件版本显式选择，绝不按帧长猜测；诊断只读帧长与字节内容，不参与解析。

### 3.6 历史格式自述接口（第二轮新增）

R2 的风险是「固件已变成 14 字节历史记录，却仍被识别为 V2，导致电压字段不被读取」。为让这一风险在联调时可被直接观测：

- 新增 `HistoryFormatAware` 接口（定义在 `HistoryDataParser.kt` 内），暴露 `historyFormatName` 与 `historyRecordSize`。
- 由 `HistoryDataParser` 实现；`MainViewModel` 通过接口读取，不做向下强转。

日志中会同时出现「固件实际帧长」与「App 选中的格式」，两者不一致时一眼可见。

### 3.7 Release 签名配置（第二轮新增，第三轮调整）

`source\app\build.gradle.kts` 的 `signingConfigs.release`：

- 签名材料从仓库根目录的 `keystore.properties` 读取。
- 该文件已在 `.gitignore` 中排除，签名文件与口令都不入库、不进源码压缩包。
- 提供 `source\keystore.properties.example` 模板，内含字段说明与 `keytool` 生成命令。
- **第三轮变更**：`keystore.properties` 缺失时，`signingConfig` 回退为 `signingConfigs.debug`（Android 默认调试密钥），使 `assembleRelease` 产出**可直接安装**的 `app-release.apk`。

~~~kotlin
signingConfig = signingConfigs.findByName("release")
    ?: signingConfigs.getByName("debug")
~~~

> **警告：调试密钥签名的 release APK 仅供本地联调，不可分发。** 一旦补齐正式 `keystore.properties`，产物签名会改变，届时需先卸载设备上的旧包再安装。

### 3.8 移除 NFC 墨水屏挂件图传模块（第三轮新增）

用户决定不再在 App 内保留这个独立的第二硬件模块。删除范围：

| 类型 | 内容 |
|---|---|
| 整目录删除 | `data/nfc/`（11 个文件）：`Crc16Ccitt`、`EInkEepromImageSender`、`EInkFtmImageSender`、`EInkImageEncoder`、`EInkNfcImageSender`、`EInkNfcProtocol`、`EInkTriColorQuantizer`、`Iso15693`、`NfcPresenceDetector`、`St25dvEInkLayout`、`St25dvFtm` |
| 屏幕删除 | `ui/screen/EInkPendantScreen.kt`、`ui/screen/EInkImageEditor.kt`（后者删除前已是死代码） |
| `MainActivity.kt` | 移除墨水屏选图 launcher、`selectedEInkBitmap`、`loadBitmapFromUri` 及相关参数 |
| `MainScreen.kt` | 移除 `homeMode` 与 `when(homeMode)` 分支、`nfcTagInRange`/`eInkSendState` 收集、发送确认弹窗、「打开墨水屏」调试按钮、`toSimpleEInkPreview`、底部导航栏的墨水屏全屏判断 |
| `MainViewModel.kt` | 移除 `HomeMode` 枚举、`setHomeMode`、NFC 在场检测、`EInkSendState`、`sendEInkPreviewOverNfc`、`cancelEInkSend` 及三处 `setHomeMode(HomeMode.DEFAULT)` 调用 |
| `DeviceSelectionDialog.kt` | 移除 `onSelectEInkPendant` 参数、`SavedDeviceList` 同名参数与「墨水屏挂件」入口按钮 |
| `DeviceTypes.kt` | 移除 `EINK_PENDANT` 常量 |
| `AndroidManifest.xml` | 移除 `android.permission.NFC` 与 `android.hardware.nfc` 特性声明 |

**未受影响**：温湿度计 BLE 主流程、协议解析、Room 数据库、气压/WAPS 逻辑、自动连接。

**产物级复核（比源码 grep 更强）**：对 `app-debug.apk` 全部 15 个 dex 扫描，`墨水屏`、`eink_pendant`、`EInk`、`NfcPresence`、`ST25DV` 字符串**均未出现**，也不存在任何自定义 NFC/EInk 类。模块已从交付产物中彻底移除，而非仅不再被调用。

**协议资料已保留**：两份历史设计文档加上了废弃标注但没有删除——

- `source\docs\20260311-墨水屏挂件NFC图片发送-plan.md`
- `source\docs\20260313-NFC图片发送-EEPROM方案-plan.md`

这两份文档记录了 STM32 + ST25DV16K + 三色墨水屏的 NFC 传输协议（块顺序、确认机制、超时、RF/I2C 竞态）。**若该硬件将来重新接入 App，请以这两份文档为协议依据，不要从零推导。**

### 3.9 首页电压预留位（第三轮新增）

**问题**：`StatusBar.kt` 的电压原本是「有数据才渲染」（`isConnected && batteryVoltage != null`），无数据时整块消失。视觉上就没有预留位——这正是用户观察到的现象。

**改法**：改为连接后常驻显示。有电压显示实际值，无电压显示占位符 `-- V`，与 `HistoryCard` 的既有占位约定保持一致，避免数据到达与否导致布局跳动。

~~~kotlin
// 电压预留位：连接后常驻显示，避免有无电压数据时布局跳动。
if (isConnected) {
    Text(
        text = batteryVoltage?.let { String.format("%.1fV", it) } ?: "-- V",
        ...
    )
}
~~~

**真机效果**：状态栏显示 `PandaTemp_7086 (v2) -- V`，三段（设备名 / 固件版本 / 电压位）齐全。

> 注意：本机固件不上报电压，因此该位置会长期显示 `-- V`。这是预期行为，不是缺陷。若换成上报电压的固件，同一位置会直接显示数值。

### 3.10 单元测试、构建和打包

单元测试（第三轮后，共 **25 项全部通过**）：

| 测试类 | 用例数 | 说明 |
|---|---|---|
| `data/bluetooth/BleFrameDiagnosticsTest.kt` | 10 | 第二轮新增 |
| `data/device/parser/HistoryDataParserTest.kt` | 6 | 第二轮新增 3 项格式自述与布局校验 |
| `data/device/parser/RealtimeDataParserTest.kt` | 2 | 实时 6/8 字节 |
| `utils/WeatherChartEventsTest.kt` | 6 | 既有 |
| `ExampleUnitTest.kt` | 1 | 既有 |

墨水屏模块原本没有单元测试，删除不影响测试基线。

已验证命令（**必须显式指定 JDK 17，见第 5 节**）：

~~~powershell
cd "C:\Users\linckr\Documents\Codex\2026-09-08\referenced-chatgpt-conversation-this-is-an\PandaThemperature-Android\source"
$env:JAVA_HOME="C:\Users\linckr\.workbuddy\binaries\jdk\jdk-17.0.20.1+1"
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
~~~

第三轮验证结果：**BUILD SUCCESSFUL**（97 个任务）；单元测试 25 项全部通过，0 失败 0 错误。

当前产物（第三轮实测）：

| 产物 | 路径 | 大小 | SHA-256 |
|---|---|---|---|
| Debug APK | `source\app\build\outputs\apk\debug\app-debug.apk` | 22,332,795 B | `F6DC2EE268F63FB0302D0236D133354C45C7A9321E9EF12E220BD68E3DD89AD4` |
| Release APK | `source\app\build\outputs\apk\release\app-release.apk` | 15,658,729 B | `64F12A68583CE19A42A59FDEFF1C9F4C8BFD3060E7279F2C9EC977B97D5C6D9B` |
| 源码压缩包 | `src.zip` | 9,941,936 B | `1F49726C8E61171B61A8164051A2C75EB85B4289B46A5256557BFC1250CB8768` |

`src.zip` 共 213 项（上一轮 224 项：减去删除的 13 个墨水屏文件，加上本轮 2 份新文档），已确认不含构建产物、密钥与 `local.properties`。

签名状态已用 `apksigner verify --print-certs` 实测：

- `app-debug.apk`：`C=US, O=Android, CN=Android Debug`
- `app-release.apk`：`C=US, O=Android, CN=Android Debug`（回退生效，可直接安装）

注意：APK 的 SHA-256 每次都不同（构建时间戳等），上表是本轮实测值，仅供核对，不要当成稳定指纹。

## 4. 已修复/规避的问题

### 4.1 启动时权限回调误弹设备选择框（第一轮）

自动连接复用了原有蓝牙权限和蓝牙开关流程，但区分了「启动自动连接」和「用户手动选择」两种回调来源（`pendingBluetoothReadyAction`），避免权限准备完成后无条件弹设备选择框。

### 4.2 自动连接失败阻塞手动入口（第一轮）

自动连接失败只记录错误并保留手动选择入口，不让用户停留在不可操作的连接遮罩中。

### 4.3 历史格式误判风险（第一轮）

历史记录格式通过 `DeviceProfile` 固定选择，而不是简单按接收包长度猜测，保证旧 V1/V2 固件兼容，并为 V3 电压格式留出明确入口。

### 4.4 电池特征与实时数据冲突（第一轮）

没有启用旧的独立 `BATTERY_CHAR` 读取/通知路径。电压设计为随温湿度实时数据一起上报，避免同时读取两个互相冲突的协议来源。

### 4.5 Release 变体此前根本无法构建（第二轮修复）

**这是第二轮通过首次执行 `assembleRelease` 才暴露出来的真实缺陷。** 仓库此前从未构建过 release，因此一直被隐藏。

- 现象：`:app:lintVitalRelease` 失败，报 235 条错误。
- 排查：235 条全部同源，都是 `InvalidFragmentVersionForActivityResult`，集中在 `MainActivity.kt` 的 `registerForActivityResult` 调用点。
- 根因（用 `:app:dependencyInsight --dependency androidx.fragment --configuration releaseRuntimeClasspath` 追溯）：

~~~text
com.google.android.gms:play-services-location:21.1.0
  └─ com.google.android.gms:play-services-base:18.1.0
       └─ androidx.fragment:fragment:1.0.0
~~~

本项目没有 appcompat/fragment 依赖，没有任何东西把 `fragment:1.0.0` 顶上去，它就停在 2018 年的 1.0.0，低于 `registerForActivityResult` 要求的 1.3.0。**这不是 lint 误报，是真实的依赖卫生问题。**

- 修复：在 `app\build.gradle.kts` 的 `dependencies` 中加入版本约束，只提升下限、不新增直接依赖：

~~~kotlin
constraints {
    implementation("androidx.fragment:fragment:1.8.9") {
        because("play-services-base 传递依赖 fragment:1.0.0，低于 ActivityResult API 要求的 1.3.0")
    }
}
~~~

- 修复后解析结果确认为 `androidx.fragment:fragment:1.0.0 -> 1.8.9`，`lintVitalRelease` 通过。

### 4.6 模块级构建产物会被误提交（第二轮修复）

`source\.gitignore` 原本只有 `/build`，只忽略根目录构建产物，`source\app\build` 会被 Git 跟踪。已补上 `build/`、`**/build/`、`.kotlin`、`keystore.properties`、`*.jks`、`*.keystore`。

### 4.7 批量裁剪导入时误删仍在使用的导入（第三轮）

第三轮删除墨水屏代码后，用一个脚本按「导入的简单名在文件中只出现一次」的规则自动清理失效导入。该规则的正则用了 `(?<![\w.])name(?![\w])`，负向后顾把 `.` 也排除了，导致 **`.scale(...)` 这类以点号开头的调用没被计入**，于是 `androidx.compose.ui.draw.scale` 被误删，构建报 `Unresolved reference 'scale'`。

修复：恢复该导入，并改用 `\.?name\s*[.(]` 重新核对全部被移除的导入（确认只有 `scale` 一条是误删，其余确实未被使用）。

**教训**：批量静态改写后必须靠编译器复核，不要只信脚本的自检输出。

## 5. 环境与工具链（重要，新踩的坑）

### 5.1 本机没有独立 JDK，Android Studio 自带的 JDK 25 无法构建

第二轮排查发现的阻塞点：

- 本机 PATH 中没有 `java`，唯一可用运行时是 Android Studio 自带的 JBR，版本为 **JDK 25.0.2**。
- **Gradle 8.13 不支持 JDK 25**。直接用它跑 Gradle 会失败，而错误信息只打印一行 `25.0.2`，没有任何其它上下文，极易被误判为项目配置错误：

~~~text
FAILURE: Build failed with an exception.

* What went wrong:
25.0.2
~~~

- 解决方式：下载 Temurin **JDK 17.0.20.1+1**（Gradle 8.13 兼容版本）到
  `C:\Users\linckr\.workbuddy\binaries\jdk\jdk-17.0.20.1+1`，通过 `JAVA_HOME` 显式指定。

**后续任何构建都必须显式指定 JDK 17，不要用 Android Studio 的 JBR（JDK 25）。**

如果该 JDK 目录被清理，重新获取的命令是：

~~~powershell
# 下载到指定目录后解压，保持在同一路径即可
$dir = "C:\Users\linckr\.workbuddy\binaries\jdk"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
curl.exe -L -o "$dir\jdk17.zip" "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse"
Expand-Archive -Path "$dir\jdk17.zip" -DestinationPath $dir -Force
~~~

注意该地址下载较慢（国内网络约 10 分钟 / 190 MB），curl 若设了 `-m` 超时会被截断，建议去掉超时或使用 `-C -` 续传。

### 5.2 真机调试环境（第三轮建立）

- 测试机：小米 `22081212C`（Android 12 / SDK 31），adb 序列号 `59761013`。
- `adb` 路径：`C:\Users\linckr\AppData\Local\Android\Sdk\platform-tools`（PATH 里可能没有，需要显式加）。
- **MIUI 的「USB 安装」限制**：默认状态下 `adb install` 会报
  `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`。
  解决：手机 → 设置 → 更多设置 → 开发者选项 → 打开「USB 安装」（部分机型还需「USB调试(安全设置)」，可能要登录小米账号）。也可以用 `adb shell settings put global adb_install_need_confirm 0` 辅助，但**开关本体必须在手机上点**。
  兜底方案：`adb push` APK 到 `/sdcard/Download/`，在手机上从文件管理器手动安装。
- 抓 App 日志：App 自己的日志走 `Log.d("MainViewModel", ...)`，因此
  `adb logcat -s MainViewModel BleManager` 即可只看到业务日志，不受系统日志干扰。
- 用 `adb shell input tap <x> <y>` 驱动 UI 时，坐标要按物理分辨率算（本机 `1220x2712`，`adb shell wm size` 可查）。截图与屏幕同尺寸，直接按比例换算即可。

### 5.3 其它工具链事实

- Gradle 8.13（wrapper 自带，`~/.gradle/wrapper/dists` 已有缓存）
- AGP 8.13.2，Kotlin 2.0.21，KSP 2.0.21-1.0.27
- compileSdk 36 / targetSdk 36 / minSdk 26，Java 11 目标
- Android SDK：`C:\Users\linckr\AppData\Local\Android\Sdk`（由 `source\local.properties` 指向，该文件不入库）
- Build-Tools 35.0.0 与 36.0.0 均已安装
- `source\local.properties` 内容为 `sdk.dir=C:/Users/linckr/AppData/Local/Android/Sdk`，属机器专属文件，**不要提交**

## 6. 当前未完成事项和下一步优先级

### P0：硬件协议确认（第三轮已大幅推进，仍未完全闭环）

第三轮**已完成**的部分：

- [x] 用真机抓取实时/历史原始帧，确认实际长度与解析格式。
- [x] 确认固件版本号与 Profile 映射一致（v2 → V2 → 12 字节/条）。

第三轮**实测结论**：

| 项目 | 实测值 |
|---|---|
| 设备 | `PandaTemp_7086`，固件 **v2** |
| 实时帧长 | **6 字节**，无电压字段（`97 09 5F 13 81 27`） |
| 实时解析 | 24.55°C / 49.59% / 1011.3hPa，正确 |
| 历史记录 | **V2，12 字节/条**，无电压 |
| 历史帧长 | 4B / 12B / 60B（BLE 分包），60B = 5 条整 |
| 历史条数 | 1751 条，接收 1778 条 |

**仍未闭环**：

1. **8 字节实时帧 / 14 字节 V3 历史记录仍未在真实设备上验证过。** 本轮设备是 v2 固件，走不到这两条路径。若固件侧计划支持电压，需要开发版固件配合再做一次真机验证。
2. **和硬件项目确定正式协议**：明确实时包和历史记录的确切长度、字节序、单位，以及固件版本号映射。**特别要确认固件侧是否真的打算上报电压**——如果不上报，App 侧的电压能力就永远是预留状态。
3. **旧固件兼容回归**：本轮设备即旧固件（6 字节 / V2），温度、湿度、气压、历史同步均正常，电压显示 `-- V`。这部分**已验证通过**。

**不要把 UI 显示 `--` 直接判定为 App 解析失败**，先看诊断日志确认帧长。

### P1：App 交付完善

1. 真实 Android 设备上验证启动流程：自动连接成功（**已验证**）；自动连接失败、主动断开后不自动重连、重新手动选择设备——**仍需补验**。
2. 验证 Room 旧版本升级，尤其是有历史数据的数据库，不丢设备、历史记录和配置。**需要真机或仪器化测试环境，本轮未执行。**
3. 验证设备删除后重新启动不会继续连接已删除设备（逻辑上 `getLatestDevice()` 直接查库，已删除设备不会命中；仍需真机确认）。
4. **产出正式签名的 release APK**：当前 release 用调试密钥签名（可直接安装但不可分发）。正式分发需生成 keystore，走第 3.7 节的 `keystore.properties` 流程。
5. 若固件侧确定最终协议，回来更新 `source\docs\` 下三份 plan 文档的完成记录。

### P2：仓库交付整理

1. 源码交付方式已定：**`source/` 完整源码纳入 Git，`src.zip` 作为发布附件/备份**，两者并存。
2. 若继续上传 fork，提交并推送后记得更新本文件的提交历史。
3. 可选：考虑给 `source/UI/panda-pro-x1-dashboard`（Vite + React 的网页端参考工程）单独说明用途，避免后续 AI 误改。

## 7. 遗留风险

### R1：硬件仅部分闭环（第三轮已更新）

本轮已确认设备固件 v2 的实际帧格式。但**「固件未来是否会上报电压」仍是未知数**。当前 App 侧的电压能力（实时 8 字节 / 历史 V3 14 字节）在本机设备上走不到，属于「已实现但未被真实设备触发」的状态。不要把「单元测试通过」当作「电压协议已联调」。

### R2：固件版本和 V3 历史长度必须匹配（已有观测手段）

历史解析通过设备 Profile 选择格式。若固件已经变成 14 字节历史记录，却仍被识别为 V2，电压字段不会被读取。日志会同时打印「实际帧长」与「App 选中格式」，可直接判断是否错配。

### R3：真实 BLE 回归证据覆盖有限

第三轮已做真机回归：自动连接、实时数据、历史同步、气压、固件版本显示均正常。**但仍未覆盖**：Room 旧版本升级、连接失败重试、长时间运行稳定性、多设备切换。**不要把「一次连接成功」描述成「全面联调完成」。**

### R4：已解决

`source/` 已在 `5fc3338` 中完整入库，`git show HEAD` 可看到逐文件差异。保留本条以说明历史背景。

### R5：电池电量百分比没有实现

需求只要求电压显示。CR2032 的负载、电压曲线和功耗未知，不能根据当前电压直接给出可靠百分比；后续若要增加百分比，需要用户确认估算模型和提示文案。

### R6：Release 密钥尚未生成，且密钥丢失不可逆

当前 release 用调试密钥签名，只适合本地联调。若要产出正式 release APK，需先生成 keystore 并由用户自行保管 `.jks` 与口令。**一旦丢失，将无法再升级同名应用**（`applicationId` 为 `com.example.pandatemperature`）。生成后务必确认 `keystore.properties` 与 `.jks` 都不入 Git、不进 `src.zip`。

**切换正式签名时注意**：设备上已安装的调试密钥版本必须先卸载，否则会因签名不一致而安装失败。

### R7：Release 变体的 lint 门禁现在真正生效了

修复 `fragment:1.0.0` 之后，`lintVitalRelease` 开始正常拦截。这意味着**后续如果引入新的 Error 级 lint 问题，release 构建会直接失败**。这是期望行为，不要在遇到时用 `checkReleaseBuilds = false` 或删除 lint 检查绕过；应像 4.5 节那样定位根因。

### R8：`applicationId` 与包名仍是示例值

`com.example.pandatemperature` 未做正式化处理。若未来要上架或长期分发，需要一并决定是否更改（更改会破坏升级路径）。

### R9：前台服务在「屏幕关闭时启动 App」场景会抛异常（第三轮发现）

第三轮用 `adb` 在**屏幕关闭**状态下拉起 App 时，日志出现：

~~~text
E/MainViewModel: 连接状态监听错误
android.app.ForegroundServiceStartNotAllowedException: startForegroundService() not allowed
  due to mAllowStartForeground false: service com.example.pandatemperature/.service.BleConnectionForegroundService
  at ... MainViewModel$1$1.emit(MainViewModel.kt:365)
~~~

原因：Android 12+ 限制应用在**非前台**状态下启动前台服务。屏幕关闭时 App 不算「可见前台」，因此 BLE 常驻前台服务启动被拒。

- 该异常已被 `try/catch` 捕获并记录，**不会导致崩溃**，但连接保活通知会缺失。
- **未确认真实使用路径是否受影响**：正常使用是用户点亮屏幕后点击图标启动，此时 App 在前台，不应该触发该限制。本轮只在 adb 冷启动 + 屏熄场景复现。
- 后续若收到「连接保活通知偶发不出现」的反馈，优先怀疑此项，可考虑把前台服务启动时机后移到首帧可见之后，或改由 `Lifecycle` 感知前台后再启动。

### R10：墨水屏模块已删除，恢复需依赖 Git 历史

NFC 墨水屏图传模块的代码已从当前分支删除。协议资料（两份历史设计文档）保留，但**实现代码只存在于 Git 历史中**（`e36f97c` 的父提交之前）。

若将来要恢复该功能：

~~~powershell
git show 23623da:source/app/src/main/java/com/example/pandatemperature/data/nfc/EInkEepromImageSender.kt
~~~

或者从该提交整目录检出。**不要重新从零实现 NFC 协议。**

## 8. 现有功能边界

### 8.1 气压数据来源

App 的气压不是手机系统气压，也不是 App 自己估算出来的；它来自温湿度计通过 BLE 上报的数据。设备侧使用的气压传感器在项目约定中为 SPL06-001，App 侧由实时/历史解析器读取并交给气压趋势、海拔和天气分析逻辑。旧固件或无气压记录时，相关字段允许为 `null`。

主要位置：

- `source\app\src\main\java\com\example\pandatemperature\data\device\parser\RealtimeDataParser.kt`
- `source\app\src\main\java\com\example\pandatemperature\data\device\parser\HistoryDataParser.kt`
- `source\app\src\main\java\com\example\pandatemperature\utils\WapsLogic.kt`

### 8.2 墨水屏挂件（第三轮已移除）

原先「连接界面/遮罩中的『打开墨水屏』按钮」以及设备选择界面的「墨水屏挂件」入口，均属于独立的第二硬件模块：设备端为 STM32 + ST25DV16K + 三色墨水屏，通过 NFC 而非 BLE 通信。

**该模块已于第三轮整体移除**（见 3.8 节）。当前 App **不再包含任何 NFC 代码、NFC 权限或墨水屏界面**。若后续 `grep` 到相关内容，只可能来自 Git 历史，不是当前代码。

历史设计文档（保留作为硬件协议资料）：

- `source\docs\20260311-墨水屏挂件NFC图片发送-plan.md`
- `source\docs\20260313-NFC图片发送-EEPROM方案-plan.md`

自动连接逻辑只针对「设备管理」中保存的温湿度计。

### 8.3 网页端参考工程

`source\UI\panda-pro-x1-dashboard`（Vite + React + TypeScript）与 `source\index.html` 是网页端参考实现，不是 Android App 的运行时代码。`source\CLAUDE.md` 明确说明 `index.html` 不需要修改。不要把它们当成 App 模块改动。

## 9. 推荐后续 AI 的启动顺序

1. 先执行 `git status --short --branch`，确认当前分支与是否有用户新改动。若报 `dubious ownership`，用 `git -c safe.directory='*' ...` 前缀绕过，或让用户执行 `git config --global --add safe.directory <仓库路径>`。
2. 阅读本文件、`source\CLAUDE.md`，以及 `source\docs\` 下三份 plan 文档（20260908 / 20260910 / 20260911）。
3. **先确认 JDK**：设置 `JAVA_HOME` 指向 JDK 17（见第 5 节）。不要用 Android Studio 的 JBR（JDK 25）。
4. 运行现有单元测试与 Debug/Release 构建，确认基线没有漂移。
5. **不要先改固件**；先连真机，从 App 日志的 `BLE诊断` 行确认「实时帧长 / 历史帧长 / 固件版本号」。
6. 若需要改代码，遵循 `source\CLAUDE.md`：先写中文开发计划（`docs\[年月日]-[开发计划]-plan.md`），复用现有模块，避免把逻辑堆进单个文件。
7. 修改后补测试，重新构建，再决定是否重新生成 `src.zip`。
8. 推送前检查：没有 `local.properties`、`keystore.properties`、`*.jks`、构建目录、设备日志和个人环境信息。

## 10. 可复用命令

### 查看仓库状态

~~~powershell
cd "C:\Users\linckr\Documents\Codex\2026-09-08\referenced-chatgpt-conversation-this-is-an\PandaThemperature-Android"
git status --short --branch
git log -5 --oneline --decorate
git remote -v
~~~

### 运行测试和 Debug + Release 构建

~~~powershell
cd "C:\Users\linckr\Documents\Codex\2026-09-08\referenced-chatgpt-conversation-this-is-an\PandaThemperature-Android\source"
$env:JAVA_HOME="C:\Users\linckr\.workbuddy\binaries\jdk\jdk-17.0.20.1+1"
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
~~~

### 只跑单元测试（快速回归）

~~~powershell
cd "C:\Users\linckr\Documents\Codex\2026-09-08\referenced-chatgpt-conversation-this-is-an\PandaThemperature-Android\source"
$env:JAVA_HOME="C:\Users\linckr\.workbuddy\binaries\jdk\jdk-17.0.20.1+1"
.\gradlew.bat :app:testDebugUnitTest --tests "*BleFrameDiagnosticsTest" --tests "*HistoryDataParserTest"
~~~

### 安装到真机并抓业务日志

~~~powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
& $adb install -r -t -g "C:\...\source\app\build\outputs\apk\debug\app-debug.apk"
& $adb logcat -c
& $adb shell am start -n com.example.pandatemperature/.MainActivity
# 只看 App 自己的日志，不受系统日志干扰
& $adb logcat -s MainViewModel BleManager
~~~

排查 P0 协议事实时，在日志里搜 `BLE诊断` 即可看到实时/历史帧长、十六进制与所选格式。

### 追溯传递依赖（排查 lint 或依赖冲突）

~~~powershell
cd "C:\Users\linckr\Documents\Codex\2026-09-08\referenced-chatgpt-conversation-this-is-an\PandaThemperature-Android\source"
$env:JAVA_HOME="C:\Users\linckr\.workbuddy\binaries\jdk\jdk-17.0.20.1+1"
.\gradlew.bat :app:dependencyInsight --dependency androidx.fragment --configuration releaseRuntimeClasspath
~~~

### 校验 APK 签名状态

~~~powershell
$env:JAVA_HOME="C:\Users\linckr\.workbuddy\binaries\jdk\jdk-17.0.20.1+1"
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0\apksigner.bat" verify --print-certs `
  "C:\Users\linckr\Documents\Codex\2026-09-08\referenced-chatgpt-conversation-this-is-an\PandaThemperature-Android\source\app\build\outputs\apk\release\app-release.apk"
~~~

### 重新生成并检查源码压缩包

本机 **没有 `zip` 命令**，用已备好的 Python 脚本生成：

~~~powershell
cd "C:\Users\linckr\WorkBuddy\2026-09-10-22-38-19"
python build_src_zip.py `
  "C:\Users\linckr\Documents\Codex\2026-09-08\referenced-chatgpt-conversation-this-is-an\PandaThemperature-Android\source" `
  "C:\Users\linckr\Documents\Codex\2026-09-08\referenced-chatgpt-conversation-this-is-an\PandaThemperature-Android\src.zip"
~~~

脚本会排除构建产物与签名材料，并打印跳过项。若脚本丢失，用 Python `zipfile` 重建即可，排除规则与 `source\.gitignore` 保持一致。

检查压缩包内容（应不输出任何条目）：

~~~powershell
cd "C:\Users\linckr\Documents\Codex\2026-09-08\referenced-chatgpt-conversation-this-is-an\PandaThemperature-Android"
tar -tf .\src.zip | Select-String "local\.properties|/build/|\.gradle/|\.kotlin/|\.jks|keystore\.properties$"
~~~

## 11. 当前交接判定

| 项目 | 状态 |
|---|---|
| 启动自动连接最近设备 | 已实现；真机验证通过 |
| 主动断开后抑制本次自动重连 | 已实现；**未做真机补验** |
| 实时电压解析预留 | 已实现，兼容 6/8 字节；真机设备为 6 字节（无电压） |
| 历史电压解析预留 | 已实现，兼容 V1/V2/V3；真机设备为 V2（无电压） |
| Room 字段与迁移 | 已实现，版本 9→10；**旧库升级回归未做** |
| 四处电压 UI | 已实现；首页电压位第三轮改为常驻预留位 |
| BLE 原始帧诊断 | 已实现，已在真机产出有效协议事实 |
| 历史格式自述接口 | 已实现 |
| Release 签名 | 第三轮改为可回退调试密钥，产出可安装 APK |
| 单元测试 | 25 项全部通过 |
| Debug APK | 已生成并已在真机安装运行 |
| Release APK | 已生成（调试密钥签名，可安装、不可分发） |
| Release lint 门禁 | 第二轮修复 fragment 传递依赖后真正生效 |
| 真机协议验证（固件 v2） | **第三轮完成**：实时 6B 无电压、历史 V2 12B 无电压 |
| 真机协议验证（8B 实时 / V3 历史） | **未验证**，本机设备走不到该路径 |
| 真实手机/硬件联调（自动连接、实时、历史同步） | 第三轮已做，通过 |
| Room 旧版本升级回归 | 待办，需真机或仪器化测试 |
| 完整源码纳入 Git | 已完成 |
| NFC 墨水屏挂件模块 | **第三轮已整体移除**（含权限与界面），协议资料以文档形式保留 |
| 网页端参考工程 | 既有内容，不是 App 运行时模块 |
