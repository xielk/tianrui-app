# iOS CoreBluetooth 全量蓝牙能力设计

## 1. 目标

在现有 iOS SwiftUI 工程中落地完整蓝牙能力，与 Android 侧现有 BLE 行为保持对齐，覆盖：扫描、连接、重连、通知解析、控制指令、配对状态、通道可用性联动、OTA 升级与日志观测。

## 2. 设计选择

采用分层方案（推荐并确认）：

1. CoreBluetooth 管理层（设备连接与特征交互）
2. 协议编解码层（帧构造、加解密、状态解析）
3. 仓库层（业务稳定接口）
4. ViewModel/页面联动层（F1/F2 页面状态与控制）

不采用单体 BluetoothService（可维护性差）和第三方 BLE 框架迁移（协议耦合高、返工风险高）。

## 3. 架构

### 3.1 CoreBluetooth 层

- `IOSBleManager` 负责 `CBCentralManager` 生命周期、扫描、连接、断开、服务发现、通知订阅、写入。
- 状态机：`idle -> scanning -> connecting -> discovering -> ready -> reconnecting -> disconnected`。
- 重连策略：指数退避 + 最大重试上限，超限后回到 `disconnected` 并上抛错误。

### 3.2 协议层

- `BlePacketCodec` 负责：
  - 指令帧构造（锁车/静音/感应/寻车/设置等）
  - Token 请求与校验
  - 加解密（与 Android 协议一致）
  - 通知报文分类解析（C4/C5/C6/C7）
- `BleRealtimeState` 统一存放实时状态：锁车、静音、感应、里程/电量等可用字段。

### 3.3 仓库层

- 定义 `BleRepository`：
  - `connectTo(mac)`
  - `disconnect()`
  - `ensureConnectedInBackground()`
  - `sendCommand(command)`
  - `ensurePaired()` / `removeCurrentPairing()`
  - `channelAvailability` / `realtimeState` 流
- `BleRepositoryImpl` 组合 `IOSBleManager + BlePacketCodec + SessionStore` 对外暴露稳定业务接口。

### 3.4 业务与 UI 层

- F1/F2 ViewModel 仅依赖 `BleRepository`，不直接依赖 CoreBluetooth。
- `hasAnyChannel` 由 `channelAvailability` 驱动，按钮可用态与 Android 对齐。
- 控制按钮行为与 Android 一致：BLE 优先，按设备模式决定是否可回落 API。

### 3.5 OTA 子系统

- 独立 `BleOtaService`，状态机：
  `otaIdle -> validating -> transferring -> verifying -> success/fail`
- OTA 期间冻结控制指令发送，避免写特征并发冲突。

## 4. 数据流

1. App/页面启动读取 `lastBluetoothAddress`，触发后台连接。
2. 连接成功后发现服务/特征并启用 notify。
3. 控制命令通过仓库进入协议层编码，再由 manager 写入。
4. 设备 notify 回包进入协议层解析，更新 `BleRealtimeState`。
5. ViewModel 订阅状态流驱动 UI。
6. 断连后进入重连流程，最终成功恢复或上抛错误并置灰通道。

## 5. 错误处理

- 统一错误域：`BT_UNAVAILABLE`、`NO_MAC`、`BLE_NOT_READY`、`BLE_DISCONNECTED`、`BLE_WRITE_FAIL`、`BLE_TOKEN_TIMEOUT`、`BLE_ENCRYPT_ERROR`、`OTA_*`。
- 用户提示分级：
  - 可恢复错误：toast + 自动重试
  - 不可恢复错误：提示 + 操作入口禁用
- 日志字段统一：phase、mac、service/char、command、plain/encrypted、errorCode。

## 6. 测试策略

- 单元测试：
  - 编解码正确性（命令 hex、token、解密）
  - 状态解析（C4/C5/C6/C7）
  - 重连策略与错误分支
- 集成测试：
  - 仓库与 manager 的状态流联动
  - 指令发送入口可用性
- 人工验收：
  - 扫描连接、断开重连、锁车/解锁、静音/感应、OTA 全流程

## 7. 范围边界

- 本设计包含完整 BLE 功能落地（非仅骨架）。
- 非目标：地图、推送、账号体系重构。

## 8. 验收标准

- iOS 可稳定扫描并连接目标设备。
- F1/F2 主控按钮可通过 BLE 下发命令并看到状态回显。
- 断连后能自动重连且 UI 状态同步正确。
- OTA 能执行到成功/失败闭环，并有可追踪日志。
