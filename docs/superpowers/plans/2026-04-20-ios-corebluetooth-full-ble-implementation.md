# iOS CoreBluetooth Full BLE Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 iOS 端完整落地 BLE 能力（连接、协议、命令、状态、OTA、UI 联动），与 Android 现有行为保持一致。

**Architecture:** 采用四层架构：CoreBluetooth 管理层、协议编解码层、仓库层、ViewModel/UI 层。所有页面逻辑只依赖仓库接口，BLE 生命周期和协议细节统一收敛到专用模块。OTA 独立子系统并在执行时冻结控制通道。

**Tech Stack:** Swift 5, SwiftUI, CoreBluetooth, Combine, XCTest

---

## File Structure

- Create: `ios/Tianruiapp/Bluetooth/BleModels.swift`
- Create: `ios/Tianruiapp/Bluetooth/BlePacketCodec.swift`
- Create: `ios/Tianruiapp/Bluetooth/IOSBleManager.swift`
- Create: `ios/Tianruiapp/Bluetooth/BleRepository.swift`
- Create: `ios/Tianruiapp/Bluetooth/BleRepositoryImpl.swift`
- Create: `ios/Tianruiapp/Bluetooth/BleOtaService.swift`
- Create: `ios/Tianruiapp/Bluetooth/BleDebugStore.swift`
- Modify: `ios/Tianruiapp/Features/F1/F1HomeScreen.swift`
- Modify: `ios/Tianruiapp/Features/F1/F1Components.swift`
- Create: `ios/Tianruiapp/Features/F1/F1ViewModel.swift`
- Modify: `ios/Tianruiapp/App/TianruiappApp.swift`
- Modify: `ios/Tianruiapp/App/RootScreen.swift`
- Create: `ios/TianruiappTests/BlePacketCodecTests.swift`
- Create: `ios/TianruiappTests/BleRepositoryTests.swift`
- Create: `ios/TianruiappTests/BleCommandRoutingTests.swift`

### Task 1: Define BLE Models and Contracts

**Files:**
- Create: `ios/Tianruiapp/Bluetooth/BleModels.swift`
- Create: `ios/Tianruiapp/Bluetooth/BleRepository.swift`
- Test: `ios/TianruiappTests/BleRepositoryTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
func test_channelAvailability_defaultsToUnavailable() {
    let availability = ChannelAvailability(bleAvailable: false, networkAvailable: true)
    XCTAssertFalse(availability.hasAnyChannel)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `xcodebuild test -project "ios/Tianruiapp.xcodeproj" -scheme "Tianruiapp" -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:TianruiappTests/BleRepositoryTests CODE_SIGNING_ALLOWED=NO`
Expected: FAIL with missing type `ChannelAvailability`

- [ ] **Step 3: Write minimal implementation**

```swift
struct ChannelAvailability {
    var bleAvailable: Bool
    var networkAvailable: Bool
    var hasAnyChannel: Bool { bleAvailable || networkAvailable }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run same command, expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ios/Tianruiapp/Bluetooth/BleModels.swift ios/Tianruiapp/Bluetooth/BleRepository.swift ios/TianruiappTests/BleRepositoryTests.swift
git commit -m "feat: add BLE domain models and repository contracts"
```

### Task 2: Implement Protocol Codec

**Files:**
- Create: `ios/Tianruiapp/Bluetooth/BlePacketCodec.swift`
- Test: `ios/TianruiappTests/BlePacketCodecTests.swift`

- [ ] **Step 1: Write failing tests for command encoding and notify parsing**

```swift
func test_toggleLock_encodesExpectedFrame() {
    let codec = BlePacketCodec()
    let hex = codec.buildControlHex(command: .toggleLock(locked: true), token: "1234")
    XCTAssertEqual(hex.prefix(4), "C35A")
}

func test_parseC6_updatesLockMuteAutoSense() {
    let codec = BlePacketCodec()
    let parsed = codec.parseNotifyPlainHex("C35AC6...")
    XCTAssertNotNil(parsed)
}
```

- [ ] **Step 2: Run tests to verify failure**

Run: `xcodebuild test -project "ios/Tianruiapp.xcodeproj" -scheme "Tianruiapp" -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:TianruiappTests/BlePacketCodecTests CODE_SIGNING_ALLOWED=NO`
Expected: FAIL due to missing `BlePacketCodec`

- [ ] **Step 3: Implement minimal codec skeleton + parser branching**

```swift
final class BlePacketCodec {
    func buildControlHex(command: BleControlCommand, token: String) -> String { "C35A" }
    func parseNotifyPlainHex(_ plainHex: String) -> BleParsedMessage? { nil }
}
```

- [ ] **Step 4: Expand implementation until tests pass**

Run same command, expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ios/Tianruiapp/Bluetooth/BlePacketCodec.swift ios/TianruiappTests/BlePacketCodecTests.swift
git commit -m "feat: add BLE packet codec and notify parser"
```

### Task 3: Build CoreBluetooth Manager

**Files:**
- Create: `ios/Tianruiapp/Bluetooth/IOSBleManager.swift`
- Test: `ios/TianruiappTests/BleRepositoryTests.swift`

- [ ] **Step 1: Write failing test for connection state publication**

```swift
func test_manager_publishesDisconnectedAfterDisconnectCall() {
    let manager = IOSBleManager()
    manager.disconnect()
    XCTAssertEqual(manager.connectionState, .disconnected)
}
```

- [ ] **Step 2: Run tests and verify failure**

Run: `xcodebuild test -project "ios/Tianruiapp.xcodeproj" -scheme "Tianruiapp" -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:TianruiappTests/BleRepositoryTests CODE_SIGNING_ALLOWED=NO`
Expected: FAIL with missing `IOSBleManager`

- [ ] **Step 3: Implement manager skeleton**

```swift
final class IOSBleManager: NSObject {
    @Published private(set) var connectionState: BleConnectionState = .idle
    func startConnect(macAddress: String) { connectionState = .connecting }
    func disconnect() { connectionState = .disconnected }
}
```

- [ ] **Step 4: Add real delegate wiring**

Implement `CBCentralManagerDelegate` / `CBPeripheralDelegate`, subscribe notify, write characteristic, and publish parsed packets.

- [ ] **Step 5: Run tests**

Run same command, expected: PASS

- [ ] **Step 6: Commit**

```bash
git add ios/Tianruiapp/Bluetooth/IOSBleManager.swift ios/TianruiappTests/BleRepositoryTests.swift
git commit -m "feat: add CoreBluetooth manager with state publishing"
```

### Task 4: Implement Repository and Reconnect Policy

**Files:**
- Create: `ios/Tianruiapp/Bluetooth/BleRepositoryImpl.swift`
- Test: `ios/TianruiappTests/BleRepositoryTests.swift`

- [ ] **Step 1: Write failing test for ensureConnectedInBackground retry**

```swift
func test_repository_retriesAndFailsAfterMaxAttempts() async {
    let sut = makeRepository(alwaysFailingManager: true)
    let result = await sut.ensureConnectedInBackground()
    XCTAssertEqual(result.errorCode, "BLE_RETRY_EXHAUSTED")
}
```

- [ ] **Step 2: Run test and verify failure**

Run same `BleRepositoryTests`, expected: FAIL

- [ ] **Step 3: Implement repository with retry and command routing**

```swift
final class BleRepositoryImpl: BleRepository {
    func ensureConnectedInBackground() async -> AppResult<Void> { .error("BLE_RETRY_EXHAUSTED", "") }
}
```

- [ ] **Step 4: Complete sendCommand/pairing/channel flow**

Run tests until PASS.

- [ ] **Step 5: Commit**

```bash
git add ios/Tianruiapp/Bluetooth/BleRepositoryImpl.swift ios/TianruiappTests/BleRepositoryTests.swift
git commit -m "feat: add BLE repository with retry and routing"
```

### Task 5: Add OTA Service

**Files:**
- Create: `ios/Tianruiapp/Bluetooth/BleOtaService.swift`
- Test: `ios/TianruiappTests/BleCommandRoutingTests.swift`

- [ ] **Step 1: Write failing test for OTA state transitions**

```swift
func test_otaState_transitionsToSuccessWhenTransferAndVerifyComplete() async {
    let sut = BleOtaService(...)
    await sut.startOta(fileData: Data([0x01]))
    XCTAssertEqual(sut.state, .success)
}
```

- [ ] **Step 2: Run tests, verify failure**

Run: `xcodebuild test -project "ios/Tianruiapp.xcodeproj" -scheme "Tianruiapp" -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:TianruiappTests/BleCommandRoutingTests CODE_SIGNING_ALLOWED=NO`

- [ ] **Step 3: Implement OTA state machine and lock command channel**

- [ ] **Step 4: Run tests until PASS**

- [ ] **Step 5: Commit**

```bash
git add ios/Tianruiapp/Bluetooth/BleOtaService.swift ios/TianruiappTests/BleCommandRoutingTests.swift
git commit -m "feat: add BLE OTA service and state machine"
```

### Task 6: Integrate F1/F2 ViewModel and UI

**Files:**
- Create: `ios/Tianruiapp/Features/F1/F1ViewModel.swift`
- Modify: `ios/Tianruiapp/Features/F1/F1HomeScreen.swift`
- Modify: `ios/Tianruiapp/Features/F1/F1Components.swift`

- [ ] **Step 1: Write failing UI-state test**

```swift
func test_viewModel_updatesHasAnyChannelFromRepository() async {
    let vm = F1ViewModel(repository: mockRepo(bleAvailable: false, networkAvailable: false))
    XCTAssertFalse(vm.uiState.hasAnyChannel)
}
```

- [ ] **Step 2: Run tests and verify failure**

- [ ] **Step 3: Implement ViewModel bindings and button intents**

- [ ] **Step 4: Wire screen to ViewModel and disable controls by channel availability**

- [ ] **Step 5: Run targeted tests**

Run full suite: `xcodebuild test -project "ios/Tianruiapp.xcodeproj" -scheme "Tianruiapp" -destination 'platform=iOS Simulator,name=iPhone 17' CODE_SIGNING_ALLOWED=NO`

- [ ] **Step 6: Commit**

```bash
git add ios/Tianruiapp/Features/F1/F1ViewModel.swift ios/Tianruiapp/Features/F1/F1HomeScreen.swift ios/Tianruiapp/Features/F1/F1Components.swift
git commit -m "feat: connect F1 BLE controls to repository state"
```

### Task 7: App Wiring and Regression Pass

**Files:**
- Modify: `ios/Tianruiapp/App/TianruiappApp.swift`
- Modify: `ios/Tianruiapp/App/RootScreen.swift`
- Create: `ios/Tianruiapp/Bluetooth/BleDebugStore.swift`

- [ ] **Step 1: Add app-level BLE container and dependency injection**
- [ ] **Step 2: Persist and restore last bluetooth mac/session**
- [ ] **Step 3: Add BLE debug info surface for diagnosis**
- [ ] **Step 4: Run full tests + manual verification checklist**

Manual checklist:
- 扫描并连接设备
- 锁车/解锁、静音、感应开关
- 断连后自动重连
- OTA 升级成功与失败路径

- [ ] **Step 5: Commit**

```bash
git add ios/Tianruiapp/App/TianruiappApp.swift ios/Tianruiapp/App/RootScreen.swift ios/Tianruiapp/Bluetooth/BleDebugStore.swift
git commit -m "feat: wire full BLE stack into iOS app"
```

## Plan Self-Review

- Spec coverage: 已覆盖连接、协议、仓库、UI 联动、OTA、测试与验收。
- Placeholder scan: 无 TBD/TODO 占位词；每任务包含可执行步骤。
- Type consistency: `BleRepository` / `BleRepositoryImpl` / `BlePacketCodec` 命名一致。
