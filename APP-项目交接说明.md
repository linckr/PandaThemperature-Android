# PandaThemperature-Android 项目交接说明

更新时间：2026-09-10（第二轮，Asia/Shanghai）  
交接对象：后续继续推进本项目的 AI / 开发者

## 0. 一句话结论

本仓库已完成两轮 App 侧工作。第一轮（2026-09-08）实现「启动自动连接最近设备」与「电池电压展示」，但留下三个未闭环问题：硬件协议无法验证、Release 构建缺失、`source/` 未纳入版本控制。第二轮（2026-09-10）已将这三项推进到可交接状态：**新增 BLE 原始帧诊断能力**（联调抓手）、**补齐 Release 签名配置并通过 `assembleRelease` 验证**（过程中修复了一个真实依赖缺陷）、**`source/` 已完整纳入 Git**。

仍未闭环的是**协议本身**：真实设备是否上报实时 8 字节 / 历史 14 字节、固件版本号如何映射，必须连真机抓包确认。**本轮完全没有改固件，也没有做过任何真机联调**。App 侧已就绪，只等硬件侧配合。

## 1. 项目、仓库和当前工作区

- App 项目：`PandaThemperature-Android`
- 原始仓库：<https://github.com/yodfz/PandaThemperature-Android>
- 当前 fork：<https://github.com/linckr/PandaThemperature-Android>
- 本地仓库：`C:\Users\linckr\Documents\Codex\2026-09-08\referenced-chatgpt-conversation-this-is-an\PandaThemperature-Android`
- Android Gradle 工程：本地仓库下的 `source` 目录
- 硬件/固件项目：<https://github.com/yodfz/NRF52xxx-FieldTemp>

Git 当前状态（第二轮结束后）：

- 分支：`main`
- 提交历史：
  - `5fc3338` 补齐交付闭环：BLE 联调诊断、Release 签名入口与源码入库（**本轮**）
  - `1fa71a2` 增加自动连接与电池电压展示（上一轮）
  - `4f34230` ADD README.MD（上游）
  - `8ad8501` init（上游）
- `origin`: `https://github.com/linckr/PandaThemperature-Android.git`
- `upstream`: <https://github.com/yodfz/PandaThemperature-Android.git>

**R4 已解决**：`source/` 完整源码已纳入 Git，`5fc3338` 中新增 224 个文件。`git show HEAD` 现在可以看到 `source/app/...` 的逐文件差异。

当前推荐入口：

~~~text
C:\Users\linckr\Documents\Codex\2026-09-08\referenced-chatgpt-conversation-this-is-an\PandaThemperature-Android\source
~~~

不要提交机器专属文件：`source\local.properties`、`source\.gradle`、`source\.kotlin`、`source\app\build`、`source\keystore.properties`、任何 `*.jks` / `*.keystore`。这些规则已写入 `source\.gitignore`，压缩源码时也不要把它们放入 `src.zip`。

## 2. 用户原始需求

### 2.1 启动自动连接

原行为：打开 App 后先进入连接设备界面，用户需要选择历史设备或扫描新设备。  
目标行为：打开 App 后，自动连接“设备管理”中添加时间最新的设备；用户主动断开后，仍可以手动重新呼出设备选择界面。

### 2.2 电池电压显示

在不修改固件的第一阶段，先让 App 具备兼容和展示能力：

1. 首页顶部设备名后显示最新电压，例如 `PandaTemp_1234 (v2) 2.9V`。
2. 首页“设备配置”弹窗中，在固件版本和存储记录之间显示电压快照。
3. 历史数据每行在温度、湿度下方显示该条记录对应的电压。
4. 设置页“设备管理”列表中，在固件版本下方、添加时间上方显示该设备最新电压。

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
- 用户主动断开时设置本次运行的手动断开状态，避免 App 启动流程重复自动连接（`startupAutoConnectAttempted` 一次性守卫）。
- 手动进入设备选择、选择其他设备、断开后重新连接的原有流程保留。

关键方法：

- `MainActivity.BluetoothReadyAction.STARTUP_AUTO_CONNECT`
- `MainViewModel.autoConnectLatestSavedDevice()`

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

重要协议前提：如果未来固件已经发送 14 字节历史记录，但设备上报的固件版本仍被 App 识别为 V1/V2，App 会继续选择旧格式并忽略电压。因此固件版本映射和历史记录长度必须一起确认。

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

### 3.4 四处电压 UI（第一轮）

- 首页状态栏：`StatusBar.kt`，连接状态下显示一位小数电压。
- 设备配置弹窗：`ConfigModal.kt`，打开弹窗时显示当前电压快照；无数据为 `--`。
- 历史记录：`HistoryCard.kt`，每条记录显示自己的 `batteryVoltage`；旧记录为 `-- V`。
- 设备管理：`DeviceManagementScreen.kt`，显示设备表中的 `latestBatteryVoltage`；无数据为 `电池电压: --`。

没有根据 CR2032 电压推算剩余百分比，也没有把电压伪装成电量百分比。

### 3.5 BLE 原始帧最小诊断（第二轮新增）

**这是本轮最重要的新增能力，直接服务于 P0 硬件协议确认。**

此前 App 没有任何原始帧输出，联调必须依赖外部 BLE 抓包工具。现在连上设备后，App 日志会直接打印协议事实。

代码位置：

- `source\app\src\main\java\com\example\pandatemperature\data\bluetooth\BleFrameDiagnostics.kt`（新模块）
- 接入点：`MainViewModel` 的实时订阅回调、实时单次读取、历史包消费三处

输出内容：

- 实时帧：`BLE诊断 实时数据: 帧长=8B（含电压字段（offset=6）），Hex=...`
- 历史帧：`BLE诊断 历史数据: 帧长=28B，格式=V3（单条 14B），完整记录=2 条，尾部余 0 B，Hex=...`

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

### 3.7 Release 签名配置（第二轮新增）

`source\app\build.gradle.kts` 增加 `signingConfigs.release`：

- 签名材料从仓库根目录的 `keystore.properties` 读取。
- 该文件已在 `.gitignore` 中排除，签名文件与口令都不入库、不进源码压缩包。
- 文件不存在时 `signingConfig` 为 `null`，release 变体退回**未签名构建**，`assembleRelease` 仍可用于编译验证。
- 提供 `source\keystore.properties.example` 模板，内含字段说明与 `keytool` 生成命令。

**本轮只做「可编译验证 + 配置入口」，没有生成任何真实密钥。** 详见第 6 节 R6。

### 3.8 单元测试、构建和打包

单元测试（第二轮后，共 **25 项全部通过**）：

| 测试类 | 用例数 | 说明 |
|---|---|---|
| `data/bluetooth/BleFrameDiagnosticsTest.kt` | 10 | 本轮新增 |
| `data/device/parser/HistoryDataParserTest.kt` | 6 | 本轮新增 3 项格式自述与布局校验 |
| `data/device/parser/RealtimeDataParserTest.kt` | 2 | 实时 6/8 字节 |
| `utils/WeatherChartEventsTest.kt` | 6 | 既有 |
| `ExampleUnitTest.kt` | 1 | 既有 |

上一轮基线为 12 项，本轮新增 13 项。

已验证命令（**必须显式指定 JDK 17，见第 5 节**）：

~~~powershell
cd "C:\Users\linckr\Documents\Codex\2026-09-08\referenced-chatgpt-conversation-this-is-an\PandaThemperature-Android\source"
$env:JAVA_HOME="C:\Users\linckr\.workbuddy\binaries\jdk\jdk-17.0.20.1+1"
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
~~~

第二轮验证结果：**BUILD SUCCESSFUL**（96 个任务，43 执行 / 53 复用，5 分 26 秒）；单元测试 25 项全部通过，0 失败 0 错误。

当前产物：

| 产物 | 路径 | 大小 | SHA-256 |
|---|---|---|---|
| Debug APK | `source\app\build\outputs\apk\debug\app-debug.apk` | 22,438,328 B | `1A8DD6749A304E668DCC5CB38099411D5E419ED20C3A769EC3D4C7F53BC28B9A` |
| Release APK（未签名） | `source\app\build\outputs\apk\release\app-release-unsigned.apk` | 15,716,121 B | `1055D260513F64209EDA36207630B003B23FA98D2115A5ACE6D27FCFC573E74E` |
| 源码压缩包 | `src.zip` | 9,965,840 B | `33226F7F85AD63D980E121DE39A7E1643723FCC92B406392CCF4B89088ACDBB7` |

签名状态已用 `apksigner verify --print-certs` 实测：

- `app-debug.apk`：由 `CN=Android Debug` 调试密钥签名，可正常安装。
- `app-release-unsigned.apk`：`DOES NOT VERIFY`（`Missing META-INF/MANIFEST.MF`），**确实未签名**，符合「无 keystore.properties 时优雅回退」的设计。

注意：APK 的 SHA-256 每次都不同（构建时间戳等），上表是本轮实测值，仅供核对，不要当成稳定指纹。

## 4. 已修复/规避的问题

### 4.1 启动时权限回调误弹设备选择框（第一轮）

自动连接复用了原有蓝牙权限和蓝牙开关流程，但区分了“启动自动连接”和“用户手动选择”两种回调来源（`pendingBluetoothReadyAction`），避免权限准备完成后无条件弹设备选择框。

### 4.2 自动连接失败阻塞手动入口（第一轮）

自动连接失败只记录错误并保留手动选择入口，不让用户停留在不可操作的连接遮罩中。

### 4.3 历史格式误判风险（第一轮）

历史记录格式通过 `DeviceProfile` 固定选择，而不是简单按接收包长度猜测，保证旧 V1/V2 固件兼容，并为 V3 电压格式留出明确入口。

### 4.4 电池特征与实时数据冲突（第一轮）

没有启用旧的独立 `BATTERY_CHAR` 读取/通知路径。电压设计为随温湿度实时数据一起上报，避免同时读取两个互相冲突的协议来源。

### 4.5 Release 变体此前根本无法构建（第二轮修复）

**这是本轮通过首次执行 `assembleRelease` 才暴露出来的真实缺陷。** 仓库此前从未构建过 release，因此一直被隐藏。

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

### 5.2 其它工具链事实

- Gradle 8.13（wrapper 自带，`~/.gradle/wrapper/dists` 已有缓存）
- AGP 8.13.2，Kotlin 2.0.21，KSP 2.0.21-1.0.27
- compileSdk 36 / targetSdk 36 / minSdk 26，Java 11 目标
- Android SDK：`C:\Users\linckr\AppData\Local\Android\Sdk`（由 `source\local.properties` 指向，该文件不入库）
- Build-Tools 35.0.0 与 36.0.0 均已安装
- `source\local.properties` 内容为 `sdk.dir=C:/Users/linckr/AppData/Local/Android/Sdk`，属机器专属文件，**不要提交**

## 6. 当前未完成事项和下一步优先级

### P0：必须先确认（需要真实硬件 + 固件侧配合）

1. **用真机确认固件是否真的上报电压**。App 已预留实时 8 字节、历史 14 字节解析，但没有固件配合时电压必然为空。
   - **抓手已就位**：连上设备后直接看 App 日志中的 `BLE诊断` 行，即可读到实时/历史帧的实际长度与十六进制。
2. **和硬件项目确定正式协议**：明确实时包和历史记录的确切长度、字节序、单位以及固件版本号映射。
   - 重点核对：日志里的 `格式=V1/V2/V3` 与固件实际使用的记录长度是否一致（对应 R2）。
3. **用真实设备回归四处 UI**：新固件连接后检查首页、设备配置、历史记录、设备管理四处是否均显示同一来源的正确电压。
4. **旧固件兼容回归**：验证只有 6 字节实时数据和 8/12 字节历史数据时，温度、湿度、气压、历史同步仍正常，电压显示为 `--`。

**不要把 UI 显示 `--` 直接判定为 App 解析失败**，先看诊断日志确认帧长。

### P1：App 交付完善

1. 真实 Android 设备上验证：首次启动、无历史设备、自动连接成功、自动连接失败、主动断开后不自动重连、重新手动选择设备。
2. 验证 Room 旧版本升级，尤其是有历史数据的数据库，不丢设备、历史记录和配置。**需要真机或仪器化测试环境，本轮未执行。**
3. 验证设备删除后重新启动不会继续连接已删除设备（逻辑上 `getLatestDevice()` 直接查库，已删除设备不会命中；仍需真机确认）。
4. **产出可安装的 release APK**：配置入口已就位并通过编译验证，但**尚未生成密钥**。需要用户提供或授权生成 keystore，走第 3.7 节的 `keystore.properties` 流程。
5. 若固件侧确定最终协议，回来更新 `source\docs\20260908-自动连接与电压展示-plan.md` 与 `source\docs\20260910-联调诊断与交付闭环-plan.md` 的完成记录。

### P2：仓库交付整理

1. 源码交付方式已定：**`source/` 完整源码纳入 Git，`src.zip` 作为发布附件/备份**，两者并存。`5fc3338` 已完成入库。
2. 若继续上传 fork，提交并推送后记得更新本文件的提交历史。
3. 可选：考虑给 `source/UI/panda-pro-x1-dashboard`（Vite + React 的网页端参考工程）单独说明用途，避免后续 AI 误改。

## 7. 遗留风险

### R1：App 代码已完成，但硬件协议尚未闭环

本轮明确“不改固件”，因此真实设备可能暂时不会上报电压。不要把 UI 出现 `--` 误判为 App 解析失败；先看 `BLE诊断` 日志中的原始帧长度与十六进制。

### R2：固件版本和 V3 历史长度必须匹配（已有观测手段）

历史解析通过设备 Profile 选择格式。若固件已经变成 14 字节历史记录，却仍被识别为 V2，电压字段不会被读取。现在日志会同时打印「实际帧长」与「App 选中格式」，可直接判断是否错配。

### R3：本轮仍只有单元测试，没有真实 BLE 回归证据

构建和解析测试通过，不等价于手机连接、通知、历史同步、Room 升级和真实设备 UI 全部通过。**不要把 Debug/Release 构建成功描述成“硬件联调完成”。**

### R4：已解决

`source/` 已在 `5fc3338` 中完整入库，`git show HEAD` 可看到逐文件差异。保留本条以说明历史背景：上一轮这是最容易出错的地方。

### R5：电池电量百分比没有实现

需求只要求电压显示。CR2032 的负载、电压曲线和功耗未知，不能根据当前电压直接给出可靠百分比；后续若要增加百分比，需要用户确认估算模型和提示文案。

### R6：Release 密钥尚未生成，且密钥丢失不可逆

本轮只补齐了签名配置入口，未生成密钥。若要产出正式 release APK，需先生成 keystore 并由用户自行保管 `.jks` 与口令。**一旦丢失，将无法再升级同名应用**（`applicationId` 为 `com.example.pandatemperature`）。生成后务必确认 `keystore.properties` 与 `.jks` 都不入 Git、不进 `src.zip`。

### R7：Release 变体的 lint 门禁现在真正生效了

修复 `fragment:1.0.0` 之后，`lintVitalRelease` 开始正常拦截。这意味着**后续如果引入新的 Error 级 lint 问题，release 构建会直接失败**。这是期望行为，不要在遇到时用 `checkReleaseBuilds = false` 或删除 lint 检查绕过；应像 4.5 节那样定位根因。

### R8：`applicationId` 与包名仍是示例值

`com.example.pandatemperature` 未做正式化处理。若未来要上架或长期分发，需要一并决定是否更改（更改会破坏升级路径）。

## 8. 现有气压和墨水屏功能边界

### 8.1 气压数据来源

App 的气压不是手机系统气压，也不是 App 自己估算出来的；它来自温湿度计通过 BLE 上报的数据。设备侧使用的气压传感器在项目约定中为 SPL06-001，App 侧由实时/历史解析器读取并交给气压趋势、海拔和天气分析逻辑。旧固件或无气压记录时，相关字段允许为 `null`。

主要位置：

- `source\app\src\main\java\com\example\pandatemperature\data\device\parser\RealtimeDataParser.kt`
- `source\app\src\main\java\com\example\pandatemperature\data\device\parser\HistoryDataParser.kt`
- `source\app\src\main\java\com\example\pandatemperature\utils\WapsLogic.kt`

### 8.2 “打开墨水屏”按钮

连接界面/遮罩中的“打开墨水屏”不是本轮自动连接或电压功能。它属于已有的墨水屏挂件逻辑设备入口：

- `EInkPendantScreen`：选择、裁剪和预览图片。
- `NfcPresenceDetector`：检测 NFC 贴片是否在场。
- `EInkEepromImageSender` / `EInkNfcImageSender`：向 ST25DV16K 发送图片的 NFC 路径。
- 设备端对应 STM32 + ST25DV16K + 三色墨水屏，不依赖温湿度计 BLE 连接。
- 设计文档：`source\docs\20260311-墨水屏挂件NFC图片发送-plan.md`、`source\docs\20260313-NFC图片发送-EEPROM方案-plan.md`。

自动连接逻辑只针对“设备管理”中保存的温湿度计，不应把墨水屏挂件当作蓝牙设备自动连接。

### 8.3 网页端参考工程

`source\UI\panda-pro-x1-dashboard`（Vite + React + TypeScript）与 `source\index.html` 是网页端参考实现，不是 Android App 的运行时代码。`source\CLAUDE.md` 明确说明 `index.html` 不需要修改。不要把它们当成 App 模块改动。

## 9. 推荐后续 AI 的启动顺序

1. 先执行 `git status --short --branch`，确认当前分支与是否有用户新改动。若报 `dubious ownership`，用 `git -c safe.directory='*' ...` 前缀绕过，或让用户执行 `git config --global --add safe.directory <仓库路径>`。
2. 阅读本文件、`source\CLAUDE.md`、`source\docs\20260910-联调诊断与交付闭环-plan.md` 与 `source\docs\20260908-自动连接与电压展示-plan.md`。
3. **先确认 JDK**：设置 `JAVA_HOME` 指向 JDK 17（见第 5 节）。不要用 Android Studio 的 JBR（JDK 25）。
4. 运行现有单元测试与 Debug/Release 构建，确认基线没有漂移。
5. **不要先改固件**；先连真机，从 App 日志的 `BLE诊断` 行确认“实时帧长 / 历史帧长 / 固件版本号”。
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
  "C:\Users\linckr\Documents\Codex\2026-09-08\referenced-chatgpt-conversation-this-is-an\PandaThemperature-Android\source\app\build\outputs\apk\release\app-release-unsigned.apk"
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
| 启动自动连接最近设备 | 已实现并通过代码/构建验证 |
| 主动断开后抑制本次自动重连 | 已实现 |
| 实时电压解析预留 | 已实现，兼容 6/8 字节 |
| 历史电压解析预留 | 已实现，兼容 V1/V2/V3（8/12/14 字节） |
| Room 字段与迁移 | 已实现，版本 9→10 |
| 四处电压 UI | 已实现 |
| BLE 原始帧诊断 | 本轮新增，已接入实时/历史两条路径 |
| 历史格式自述接口 | 本轮新增 |
| Release 签名配置入口 | 本轮新增，并通过编译验证 |
| 单元测试 | 25 项全部通过 |
| Debug APK | 已生成 |
| Release APK（未签名） | 已生成；签名密钥待用户提供或授权生成 |
| Release lint 门禁 | 本轮修复 fragment 传递依赖后真正生效 |
| 新固件实际上报电压 | 未确认，当前未改固件 |
| 真实手机/硬件联调 | 待办 |
| Room 旧版本升级回归 | 待办，需真机 |
| 完整源码纳入 Git | 已完成（`5fc3338`，224 个文件） |
| 墨水屏 NFC 图传 | 属于既有独立功能，不是本轮改动 |
| 网页端参考工程 | 既有内容，不是 App 运行时模块 |
