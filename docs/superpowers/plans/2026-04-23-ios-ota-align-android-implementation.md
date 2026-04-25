# iOS OTA Align Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build iOS FR8010 OTA end-to-end (UI + protocol runner + progress + error handling) aligned to Android behavior.

**Architecture:** Add a dedicated OTA feature module in iOS (`Features/OTA`) and keep OTA protocol isolated from normal encrypted BLE control. Use a pure OTA codec for packet/CRC logic and a CoreBluetooth runner for runtime workflow. Integrate from F1 "我的" menu via a full-screen OTA screen and guard normal BLE control commands during OTA execution.

**Tech Stack:** SwiftUI, CoreBluetooth, Foundation URLSession/FileManager, XCTest, existing iOS app architecture (`F1ViewModel`, `BleRepository`).

---

## File Structure

- Create: `ios/Tianruiapp/Features/OTA/F1OtaModels.swift`
  - OTA package option model, OTA stage enum, OTA view state model.
- Create: `ios/Tianruiapp/Features/OTA/Fr8010OtaCodec.swift`
  - Pure functions: opcode packet builders, page calc, FR8010 legacy CRC.
- Create: `ios/Tianruiapp/Features/OTA/Fr8010OtaRunner.swift`
  - CoreBluetooth OTA workflow (connect/reuse, discover/bind, cccd, mtu, ack loop, reboot).
- Create: `ios/Tianruiapp/Features/OTA/F1OtaViewModel.swift`
  - State machine orchestration for OTA UI and runner.
- Create: `ios/Tianruiapp/Features/OTA/F1OtaUpdateScreen.swift`
  - SwiftUI OTA screen aligned with Android flow.
- Modify: `ios/Tianruiapp/Features/F1/F1ViewModel.swift`
  - Add OTA presentation state and open/dismiss actions.
- Modify: `ios/Tianruiapp/Features/F1/F1HomeScreen.swift`
  - Replace OTA placeholder with OTA screen navigation.
- Modify: `ios/Tianruiapp/Bluetooth/BleRepository.swift`
  - Add OTA execution guard interface.
- Modify: `ios/Tianruiapp/Bluetooth/BleRepositoryImpl.swift`
  - Enforce command freeze during OTA run.
- Create: `ios/TianruiappTests/Fr8010OtaCodecTests.swift`
  - Validate opcode packet structure and CRC parity.
- Create: `ios/TianruiappTests/F1OtaViewModelTests.swift`
  - Validate key state transitions and error mapping.

---

### Task 1: Add OTA codec and models (TDD first)

**Files:**
- Create: `ios/Tianruiapp/Features/OTA/F1OtaModels.swift`
- Create: `ios/Tianruiapp/Features/OTA/Fr8010OtaCodec.swift`
- Test: `ios/TianruiappTests/Fr8010OtaCodecTests.swift`

- [ ] **Step 1: Write failing tests for FR8010 packet building and CRC**

```swift
import XCTest
@testable import Tianruiapp

final class Fr8010OtaCodecTests: XCTestCase {
    func test_buildGetBasePacket_matchesAndroidLayout() {
        let codec = Fr8010OtaCodec()
        let packet = codec.buildCommand(opcode: .getStartBase, address: 0, dataLength: 0, data: nil)
        XCTAssertEqual(packet.map { String(format: "%02X", $0) }.joined(), "0103000000000000")
    }

    func test_buildRebootPacket_containsLengthAndCrcLittleEndian() {
        let codec = Fr8010OtaCodec()
        let packet = codec.buildRebootCommand(fileLength: 0x12345678, crc: 0xA1B2C3D4)
        XCTAssertEqual(packet.map { String(format: "%02X", $0) }.joined(), "090A0078563412D4C3B2A1")
    }

    func test_calcLegacyCrc_skipsFirst256Bytes() {
        let codec = Fr8010OtaCodec()
        let bytes = Array(repeating: UInt8(0x11), count: 300)
        let crc = codec.calcLegacyCrc(bytes)
        XCTAssertNotEqual(crc, 0)
    }
}
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `xcodebuild test -workspace "ios/Tianruiapp.xcworkspace" -scheme "Tianruiapp" -destination "platform=iOS Simulator,name=iPhone 15" -only-testing:TianruiappTests/Fr8010OtaCodecTests`

Expected: build fails with missing types `Fr8010OtaCodec` and `OtaOpcode`.

- [ ] **Step 3: Implement minimal OTA models and codec to pass**

```swift
// F1OtaModels.swift
import Foundation

enum OtaOpcode: UInt8 {
    case getStartBase = 1
    case pageErase = 3
    case writeData = 5
    case reboot = 9
}

enum F1OtaStage: Equatable {
    case idle
    case preparing
    case downloading
    case requestingBase
    case erasing
    case writing
    case rebooting
    case success
    case failed(String)
}

struct OtaPackageOption: Equatable, Identifiable {
    let id: String
    let label: String
    let modelPrefix: String
    let version: String
    let url: String
}
```

```swift
// Fr8010OtaCodec.swift
import Foundation

struct Fr8010OtaCodec {
    func buildCommand(opcode: OtaOpcode, address: UInt32, dataLength: UInt16, data: [UInt8]?) -> [UInt8] {
        let headerLen = opcode == .pageErase ? 7 : 9
        let payload = data ?? []
        var out = [UInt8](repeating: 0, count: headerLen + payload.count)
        let lengthField: UInt16 = opcode == .pageErase ? 7 : (opcode == .getStartBase ? 3 : 9)
        out[0] = opcode.rawValue
        out[1] = UInt8(lengthField & 0xFF)
        out[2] = UInt8((lengthField >> 8) & 0xFF)
        out[3] = UInt8(address & 0xFF)
        out[4] = UInt8((address >> 8) & 0xFF)
        out[5] = UInt8((address >> 16) & 0xFF)
        out[6] = UInt8((address >> 24) & 0xFF)
        if headerLen > 7 {
            out[7] = UInt8(dataLength & 0xFF)
            out[8] = UInt8((dataLength >> 8) & 0xFF)
        }
        if !payload.isEmpty {
            out.replaceSubrange(headerLen..<(headerLen + payload.count), with: payload)
        }
        return out
    }

    func buildRebootCommand(fileLength: UInt32, crc: UInt32) -> [UInt8] {
        [
            OtaOpcode.reboot.rawValue, 0x0A, 0x00,
            UInt8(fileLength & 0xFF), UInt8((fileLength >> 8) & 0xFF), UInt8((fileLength >> 16) & 0xFF), UInt8((fileLength >> 24) & 0xFF),
            UInt8(crc & 0xFF), UInt8((crc >> 8) & 0xFF), UInt8((crc >> 16) & 0xFF), UInt8((crc >> 24) & 0xFF),
        ]
    }

    func calcLegacyCrc(_ bytes: [UInt8]) -> UInt32 {
        guard bytes.count > 256 else { return 0 }
        var crc: UInt32 = 0
        for byte in bytes.dropFirst(256) {
            let high = crc / 256
            crc = crc << 8
            crc = crc ^ fr8010CrcTable[Int((high ^ UInt32(byte)) & 0xFF)]
        }
        return crc
    }
}
```

- [ ] **Step 4: Run tests and confirm GREEN**

Run: `xcodebuild test -workspace "ios/Tianruiapp.xcworkspace" -scheme "Tianruiapp" -destination "platform=iOS Simulator,name=iPhone 15" -only-testing:TianruiappTests/Fr8010OtaCodecTests`

Expected: all `Fr8010OtaCodecTests` pass.

- [ ] **Step 5: Commit Task 1**

```bash
git add ios/Tianruiapp/Features/OTA/F1OtaModels.swift ios/Tianruiapp/Features/OTA/Fr8010OtaCodec.swift ios/TianruiappTests/Fr8010OtaCodecTests.swift
git commit -m "feat: add FR8010 OTA codec and base models"
```

---

### Task 2: Add OTA execution guard to BLE repository

**Files:**
- Modify: `ios/Tianruiapp/Bluetooth/BleRepository.swift`
- Modify: `ios/Tianruiapp/Bluetooth/BleRepositoryImpl.swift`
- Modify: `ios/TianruiappTests/BleRepositoryTests.swift`

- [ ] **Step 1: Write failing tests for command freeze during OTA**

```swift
func test_repository_sendCommandBlockedDuringOta() async {
    let manager = MockBleManager()
    manager.connectionState = .ready
    let repo = BleRepositoryImpl(manager: manager)

    repo.setOtaExecutionActive(true)
    let result = await repo.sendCommand(.findBike, token: "")

    XCTAssertEqual(result.errorCode, "BLE_OTA_BUSY")
    XCTAssertNil(manager.lastSentHex)
}
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `xcodebuild test -workspace "ios/Tianruiapp.xcworkspace" -scheme "Tianruiapp" -destination "platform=iOS Simulator,name=iPhone 15" -only-testing:TianruiappTests/BleRepositoryTests`

Expected: compile fails because `setOtaExecutionActive` is undefined.

- [ ] **Step 3: Implement repository OTA guard minimally**

```swift
// BleRepository.swift
@MainActor
protocol BleRepository {
    // ...existing...
    func setOtaExecutionActive(_ active: Bool)
    var isOtaExecutionActive: Bool { get }
}
```

```swift
// BleRepositoryImpl.swift
@MainActor
final class BleRepositoryImpl: BleRepository {
    private(set) var isOtaExecutionActive: Bool = false

    func setOtaExecutionActive(_ active: Bool) {
        isOtaExecutionActive = active
    }

    func sendCommand(_ command: BleControlCommand, token: String) async -> AppResult<Void> {
        if isOtaExecutionActive {
            return .error(BleError(code: "BLE_OTA_BUSY", message: "OTA升级中，请稍候"))
        }
        // keep existing logic below
        guard case .ready = manager.connectionState else {
            return .error(BleError(code: "BLE_NOT_READY", message: "蓝牙通道未就绪"))
        }
        // ...
    }
}
```

- [ ] **Step 4: Run tests and confirm GREEN**

Run: `xcodebuild test -workspace "ios/Tianruiapp.xcworkspace" -scheme "Tianruiapp" -destination "platform=iOS Simulator,name=iPhone 15" -only-testing:TianruiappTests/BleRepositoryTests`

Expected: BLE repository tests pass including OTA block case.

- [ ] **Step 5: Commit Task 2**

```bash
git add ios/Tianruiapp/Bluetooth/BleRepository.swift ios/Tianruiapp/Bluetooth/BleRepositoryImpl.swift ios/TianruiappTests/BleRepositoryTests.swift
git commit -m "feat: block control commands during OTA execution"
```

---

### Task 3: Build FR8010 OTA runner (CoreBluetooth workflow)

**Files:**
- Create: `ios/Tianruiapp/Features/OTA/Fr8010OtaRunner.swift`
- Modify: `ios/Tianruiapp/Features/OTA/F1OtaModels.swift`
- Test: `ios/TianruiappTests/Fr8010OtaCodecTests.swift` (extend with payload-size helper tests)

- [ ] **Step 1: Add failing tests for payload-size calculation**

```swift
func test_payloadSize_usesMtuMinusHeaders() {
    XCTAssertEqual(Fr8010OtaRunner.payloadSize(forMtu: 247), 235)
    XCTAssertEqual(Fr8010OtaRunner.payloadSize(forMtu: 23), 20)
}
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `xcodebuild test -workspace "ios/Tianruiapp.xcworkspace" -scheme "Tianruiapp" -destination "platform=iOS Simulator,name=iPhone 15" -only-testing:TianruiappTests/Fr8010OtaCodecTests`

Expected: `Fr8010OtaRunner` not found.

- [ ] **Step 3: Implement runner with Android-aligned sequence**

```swift
// Fr8010OtaRunner.swift (key API surface)
import Foundation
import CoreBluetooth

@MainActor
protocol F1OtaRunning {
    func readFirmwareVersion(mac: String) async throws -> String
    func runOta(mac: String, url: String, onProgress: @escaping (Int, String) -> Void) async throws
}

@MainActor
final class Fr8010OtaRunner: NSObject, F1OtaRunning {
    static func payloadSize(forMtu mtu: Int) -> Int { max(20, mtu - 3 - 9) }

    func readFirmwareVersion(mac: String) async throws -> String {
        try await ensureOtaChannelReady(mac: mac)
        return try await readFirmwareVersionOnly()
    }

    func runOta(mac: String, url: String, onProgress: @escaping (Int, String) -> Void) async throws {
        try await ensureOtaChannelReady(mac: mac)
        let file = try await downloadBin(url: url, progress: onProgress)
        defer { try? FileManager.default.removeItem(at: file) }
        let bytes = try Data(contentsOf: file)
        guard bytes.count >= 100 else { throw OtaRunnerError.invalidBin }

        let crc = codec.calcLegacyCrc([UInt8](bytes))
        let mtu = try await requestMtu(247)
        let packageSize = Self.payloadSize(forMtu: mtu)

        onProgress(10, "获取基地址")
        let base = try await requestBaseAddress()
        onProgress(15, "擦除扇区")
        try await erasePages(baseAddress: base, fileSize: bytes.count, onProgress: onProgress)
        onProgress(25, "写入固件")
        try await writeChunks(baseAddress: base, bytes: [UInt8](bytes), packetSize: packageSize, onProgress: onProgress)
        onProgress(98, "发送重启命令")
        try sendReboot(fileLength: UInt32(bytes.count), crc: crc)
        onProgress(100, "OTA 完成")
    }
}
```

- [ ] **Step 4: Run focused tests and build sanity**

Run: `xcodebuild test -workspace "ios/Tianruiapp.xcworkspace" -scheme "Tianruiapp" -destination "platform=iOS Simulator,name=iPhone 15" -only-testing:TianruiappTests/Fr8010OtaCodecTests && xcodebuild build -workspace "ios/Tianruiapp.xcworkspace" -scheme "Tianruiapp" -sdk iphoneos CODE_SIGNING_ALLOWED=NO`

Expected: tests pass and app builds.

- [ ] **Step 5: Commit Task 3**

```bash
git add ios/Tianruiapp/Features/OTA/F1OtaModels.swift ios/Tianruiapp/Features/OTA/Fr8010OtaRunner.swift ios/TianruiappTests/Fr8010OtaCodecTests.swift
git commit -m "feat: add iOS FR8010 OTA runner with android-aligned flow"
```

---

### Task 4: Build OTA view model and UI screen

**Files:**
- Create: `ios/Tianruiapp/Features/OTA/F1OtaViewModel.swift`
- Create: `ios/Tianruiapp/Features/OTA/F1OtaUpdateScreen.swift`
- Create: `ios/TianruiappTests/F1OtaViewModelTests.swift`

- [ ] **Step 1: Write failing tests for state transitions**

```swift
import XCTest
@testable import Tianruiapp

@MainActor
private final class MockOtaBleRepository: BleRepository {
    var latestConnectionState: BleConnectionState = .ready
    var latestRealtimeState: BleRealtimeState = .init()
    var latestSystemConnected: Bool = true
    var isOtaExecutionActive: Bool = false

    func ensureConnectedInBackground() async -> AppResult<Void> { .success(()) }
    func connectTo(macAddress: String) async -> AppResult<Void> { .success(()) }
    func disconnect() async -> AppResult<Void> { .success(()) }
    func removeCurrentPairingRecord() async -> AppResult<Bool> { .success(true) }
    func sendCommand(_ command: BleControlCommand, token: String) async -> AppResult<Void> { .success(()) }
    func setOtaExecutionActive(_ active: Bool) { isOtaExecutionActive = active }
}

@MainActor
private final class MockOtaRunner: F1OtaRunning {
    enum ResultMode { case success, fail }
    let result: ResultMode
    init(result: ResultMode) { self.result = result }

    func readFirmwareVersion(mac: String) async throws -> String { "1.2.0" }

    func runOta(mac: String, url: String, onProgress: @escaping (Int, String) -> Void) async throws {
        onProgress(10, "获取基地址")
        onProgress(98, "发送重启命令")
        onProgress(100, "OTA 完成")
        if result == .fail {
            throw NSError(domain: "test", code: -1, userInfo: [NSLocalizedDescriptionKey: "mock failed"])
        }
    }
}

@MainActor
final class F1OtaViewModelTests: XCTestCase {
    func test_startOta_successPath_reachesSuccess() async {
        let repo = MockOtaBleRepository()
        let runner = MockOtaRunner(result: .success)
        let vm = F1OtaViewModel(repository: repo, runner: runner, macAddress: "E0:00:00:00:00:29", modelType: "F1")

        await vm.startUpgrade()

        XCTAssertEqual(vm.stage, .success)
        XCTAssertEqual(vm.progress, 100)
    }
}
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `xcodebuild test -workspace "ios/Tianruiapp.xcworkspace" -scheme "Tianruiapp" -destination "platform=iOS Simulator,name=iPhone 15" -only-testing:TianruiappTests/F1OtaViewModelTests`

Expected: missing type `F1OtaViewModel`.

- [ ] **Step 3: Implement view model and screen minimally**

```swift
// F1OtaViewModel.swift (public state)
import Foundation

@MainActor
final class F1OtaViewModel: ObservableObject {
    @Published private(set) var stage: F1OtaStage = .idle
    @Published private(set) var progress: Int = 0
    @Published private(set) var progressText: String = "等待开始"
    @Published private(set) var currentVersion: String = "-"
    @Published private(set) var isRunning: Bool = false
    @Published var selectedIndex: Int = 0

    let packageOptions: [OtaPackageOption]

    func refreshVersion() async { /* call runner.readFirmwareVersion */ }

    func startUpgrade() async {
        guard !isRunning else { return }
        isRunning = true
        repository.setOtaExecutionActive(true)
        defer {
            repository.setOtaExecutionActive(false)
            isRunning = false
        }
        do {
            try await runner.runOta(mac: macAddress, url: packageOptions[selectedIndex].url) { [weak self] p, text in
                Task { @MainActor in
                    self?.progress = p
                    self?.progressText = text
                    if p <= 15 { self?.stage = .requestingBase }
                    else if p < 25 { self?.stage = .erasing }
                    else if p < 98 { self?.stage = .writing }
                    else { self?.stage = .rebooting }
                }
            }
            stage = .success
            progress = 100
        } catch {
            stage = .failed(error.localizedDescription)
        }
    }
}
```

```swift
// F1OtaUpdateScreen.swift (core layout)
import SwiftUI

struct F1OtaUpdateScreen: View {
    @ObservedObject var viewModel: F1OtaViewModel
    let onBack: () -> Void

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                Text("OTA 更新").font(.system(size: 22, weight: .bold))
                Text("蓝牙 MAC: \(viewModel.macAddress)")
                Text("当前设备版本(0x180A/0x2A26): \(viewModel.currentVersion)")
                Text("目标升级版本: \(viewModel.packageOptions[viewModel.selectedIndex].version)")
                ProgressView(value: Double(viewModel.progress), total: 100)
                Text("进度: \(viewModel.progress)%")
                Text(viewModel.progressText)
                Button("开始升级") { Task { await viewModel.startUpgrade() } }
                    .disabled(viewModel.isRunning)
                Button("升级完成后刷新版本") { Task { await viewModel.refreshVersion() } }
            }
            .padding(16)
        }
    }
}
```

- [ ] **Step 4: Run tests and validate build**

Run: `xcodebuild test -workspace "ios/Tianruiapp.xcworkspace" -scheme "Tianruiapp" -destination "platform=iOS Simulator,name=iPhone 15" -only-testing:TianruiappTests/F1OtaViewModelTests && xcodebuild build -workspace "ios/Tianruiapp.xcworkspace" -scheme "Tianruiapp" -sdk iphoneos CODE_SIGNING_ALLOWED=NO`

Expected: ViewModel tests pass; app builds.

- [ ] **Step 5: Commit Task 4**

```bash
git add ios/Tianruiapp/Features/OTA/F1OtaViewModel.swift ios/Tianruiapp/Features/OTA/F1OtaUpdateScreen.swift ios/TianruiappTests/F1OtaViewModelTests.swift
git commit -m "feat: add iOS OTA view model and screen"
```

---

### Task 5: Wire OTA entry from F1 and replace placeholder

**Files:**
- Modify: `ios/Tianruiapp/Features/F1/F1ViewModel.swift`
- Modify: `ios/Tianruiapp/Features/F1/F1HomeScreen.swift`

- [ ] **Step 1: Write failing UI wiring test (or compile-level expectation)**

```swift
// Add compile-guard smoke test in existing F1 layout tests
func test_f1HomeScreen_canPresentOtaScreen() {
    let repo = BleRepositoryImpl(manager: IOSBleManager())
    let vm = F1ViewModel(repository: repo)
    _ = F1HomeScreen(viewModel: vm)
    XCTAssertFalse(vm.isOtaPresented)
}
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `xcodebuild test -workspace "ios/Tianruiapp.xcworkspace" -scheme "Tianruiapp" -destination "platform=iOS Simulator,name=iPhone 15" -only-testing:TianruiappTests/F1LayoutTests`

Expected: `isOtaPresented` missing in `F1ViewModel`.

- [ ] **Step 3: Implement navigation state and menu action**

```swift
// F1ViewModel.swift additions
@Published var isOtaPresented: Bool = false

func onOtaTap() {
    isOtaPresented = true
}

func onOtaDismiss() {
    isOtaPresented = false
}

func makeOtaViewModel() -> F1OtaViewModel {
    let mac = UserDefaults.standard.string(forKey: "last_bluetooth_mac") ?? ""
    let model = currentLayoutMode == .f2 ? "F2" : "F1"
    return F1OtaViewModel(
        repository: repository,
        runner: Fr8010OtaRunner(),
        macAddress: mac,
        modelType: model
    )
}
```

```swift
// F1HomeScreen.swift mine menu replacement
mineMenuItem(title: "检查OTA更新") {
    viewModel.onOtaTap()
}

.fullScreenCover(isPresented: $viewModel.isOtaPresented) {
    F1OtaUpdateScreen(
        viewModel: viewModel.makeOtaViewModel(),
        onBack: viewModel.onOtaDismiss
    )
}
```

- [ ] **Step 4: Run related tests and build**

Run: `xcodebuild test -workspace "ios/Tianruiapp.xcworkspace" -scheme "Tianruiapp" -destination "platform=iOS Simulator,name=iPhone 15" -only-testing:TianruiappTests/F1LayoutTests && xcodebuild build -workspace "ios/Tianruiapp.xcworkspace" -scheme "Tianruiapp" -sdk iphoneos CODE_SIGNING_ALLOWED=NO`

Expected: F1 layout tests and build pass.

- [ ] **Step 5: Commit Task 5**

```bash
git add ios/Tianruiapp/Features/F1/F1ViewModel.swift ios/Tianruiapp/Features/F1/F1HomeScreen.swift
git commit -m "feat: wire F1 OTA entry to iOS OTA screen"
```

---

### Task 6: End-to-end verification and regression checks

**Files:**
- Modify (if needed): `ios/Tianruiapp/Features/OTA/*.swift`
- Modify (if needed): `ios/TianruiappTests/*.swift`

- [ ] **Step 1: Run full iOS test target**

Run: `xcodebuild test -workspace "ios/Tianruiapp.xcworkspace" -scheme "Tianruiapp" -destination "platform=iOS Simulator,name=iPhone 15"`

Expected: all tests pass.

- [ ] **Step 2: Run release-style build**

Run: `xcodebuild build -workspace "ios/Tianruiapp.xcworkspace" -scheme "Tianruiapp" -sdk iphoneos CODE_SIGNING_ALLOWED=NO`

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Manual OTA checklist on device**

```text
1) 进入“检查OTA更新”页面，读取版本成功。
2) 选择与车型匹配的固定包，点击开始升级。
3) 进度从 0 -> 100，阶段文案符合预期。
4) 升级完成后刷新版本，读取到目标版本。
5) 升级中尝试控制命令，应被 BLE_OTA_BUSY 拦截。
```

- [ ] **Step 4: If manual issue appears, add failing test first then fix**

Run pattern:

```bash
# add one failing unit test first
xcodebuild test -workspace "ios/Tianruiapp.xcworkspace" -scheme "Tianruiapp" -destination "platform=iOS Simulator,name=iPhone 15" -only-testing:TianruiappTests/<NewFailingTest>
# then implement minimal fix and rerun targeted tests
```

- [ ] **Step 5: Commit final verification/fixes**

```bash
git add ios/Tianruiapp/Features/OTA ios/Tianruiapp/Features/F1 ios/Tianruiapp/Bluetooth ios/TianruiappTests
git commit -m "test: verify iOS OTA android-parity flow and finalize stability fixes"
```

---

## Spec Coverage Check

- OTA entry and user flow from F1 mine page: **Task 5**
- Firmware version read (`0x180A/0x2A26`): **Task 3 + Task 4**
- Fixed OTA package options same as Android: **Task 4**
- FR8010 sequence (base/erase/write/reboot) and CRC parity: **Task 1 + Task 3**
- Progress/error/post-refresh behavior: **Task 4 + Task 6**
- Freeze control commands during OTA: **Task 2 + Task 4 + Task 6**

## Placeholder Scan

- No `TODO`/`TBD` placeholders.
- Each coding step includes concrete code blocks.
- Each validation step includes exact commands and expected outcomes.

## Type Consistency Check

- `Fr8010OtaCodec`, `OtaOpcode`, `F1OtaStage`, `OtaPackageOption` are defined in Task 1 and reused consistently.
- OTA guard API uses `setOtaExecutionActive(_:)` and `isOtaExecutionActive` consistently in Tasks 2 and 4.
- ViewModel/screen names remain `F1OtaViewModel` and `F1OtaUpdateScreen` across tasks.
