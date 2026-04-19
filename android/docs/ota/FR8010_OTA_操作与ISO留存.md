# FR8010 OTA 操作与 ISO 留存说明

## 1. 目的

本文档用于说明 Android App 内 FR8010 蓝牙 OTA 的标准操作流程、校验机制、故障定位方法，以及 ISO 过程留存建议。

适用页面与实现：

- `app/src/main/java/xiaochao/com/feature/user/ui/OtaUpdateScreen.kt`

## 2. 当前支持的固件选项

页面内置可选固件：

- F1 v1.2.0 (`TR_H810x_F1_ota_v1.2.0.bin`)
- F1 v1.3.0 (`TR_H810x_F1_ota_v1.3.0.bin`)
- F1 v1.3.1 (`TR_H810x_F1_ota_v1.3.1.bin`)
- F2 v1.3.0 (`TR_H810x_F2_ota_v1.3.0.bin`)

## 3. 标准升级流程（操作员）

1. 打开设备蓝牙，确保 App 有蓝牙权限。
2. 进入 OTA 页面，确认页面右上角蓝牙状态正常。
3. 选择目标固件版本（建议与车型匹配）。
4. 点击 `开始升级`，确认弹窗后执行。
5. 等待进度到 100%，完成后点击“升级完成后刷新版本”确认版本变化。

注意：升级过程中不要退出 App，不要断开蓝牙，不要锁屏省电杀进程。

## 4. 通信与校验关键点

### 4.1 BLE 连接策略

- OTA 页面不强制断开重连。
- 执行 OTA 前会做通道准备：连接检查、服务发现、特征绑定、通知使能。
- 通知描述符（CCCD）写入需成功后再继续发 OTA 指令。
- 写特征写入类型按特征属性自动选择：
  - 支持 `PROPERTY_WRITE_NO_RESPONSE` 时用 `WRITE_TYPE_NO_RESPONSE`
  - 否则回退 `WRITE_TYPE_DEFAULT`

### 4.2 CRC32 算法说明（关键）

本项目最终采用 **FR8010 旧版兼容 CRC 算法**，不是标准 `java.util.zip.CRC32`：

- 跳过前 256 字节后计算
- 初始值为 0
- 使用 FR8010 对应查表算法（与历史 Java/Uni 实现一致）

实现位置：

- `app/src/main/java/xiaochao/com/feature/user/ui/OtaUpdateScreen.kt` 中 `calcFr8010LegacyCrc(...)`

回归测试：

- `app/src/test/java/xiaochao/com/feature/user/ui/OtaCrcAlgorithmTest.kt`

## 5. 日志定位与排障

建议抓取：

```bash
adb logcat -v time | grep -E "OTA-FR8010|OTA-FR8010-UI"
```

关键日志标签：

- `OTA-FR8010`: OTA 底层流程日志
- `OTA-FR8010-UI`: 页面层异常日志

建议重点关注以下阶段：

1. `ensureOtaChannelReady start/done`
2. `discover success write=... notify=... fw=...`
3. `tx GET_STR_BASE ...`
4. `rx notify ...` / `ack GET_STR_BASE ...`
5. `ota file loaded size=... crc=0x...`
6. `reboot command sent ...`

## 6. 常见故障与处理建议

### 6.1 `缺少蓝牙连接权限`

- 仅 Android 12+ 需要 `BLUETOOTH_CONNECT`
- 检查系统权限开关与 App 权限

### 6.2 `NetworkOnMainThreadException`

- 说明下载在主线程执行
- 已修复为 IO 线程下载与读文件

### 6.3 `等待ACK超时`

- 重点检查通知是否开启成功、是否收到 `rx notify`
- 检查写入是否成功与链路稳定性

### 6.4 `写入失败`

- 常见原因：GATT busy、写类型不匹配、链路波动
- 当前已做：等待 CCCD 成功 + 自动写类型回退

### 6.5 `crc32 check fail`

- 常见原因：使用了标准 CRC32 而非 FR8010 兼容算法
- 当前实现已按 FR8010 历史算法对齐

## 7. ISO 留存建议（每次发布/验证）

每次 OTA 回归建议至少留存以下证据：

1. **测试对象信息**：设备型号、SN、固件旧版本、新版本。
2. **升级输入信息**：升级包文件名、URL、文件大小。
3. **过程日志**：完整 `OTA-FR8010` / `OTA-FR8010-UI` 关键片段。
4. **结果证据**：升级前后版本对比截图。
5. **异常记录**：若失败，记录失败阶段（连接/发现/写入/ACK/CRC）。
6. **回归命令结果**：保留关键编译/测试通过记录。

建议归档格式：

- `日期_设备型号_固件版本_结果(成功/失败).zip`

## 8. 变更记录

- 新增 OTA 详细日志，支持定位到命令级别。
- 修复主线程下载问题。
- 修复通知回调兼容与 ACK 竞态。
- 修复写入失败相关流程（通知描述符确认、写类型自动选择）。
- CRC 算法切换为 FR8010 兼容实现。
