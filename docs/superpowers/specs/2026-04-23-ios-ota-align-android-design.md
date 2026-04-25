# iOS OTA Align Android Design

Date: 2026-04-23
Status: Draft for review
Owner: OpenCode

## 1. Goal

Align iOS OTA capability with current Android FR8010 OTA behavior end-to-end, including:

- OTA entry and user flow in F1 "我的" page
- Firmware version read (`0x180A/0x2A26`)
- Fixed OTA package options (same initial URLs as Android)
- FR8010 OTA protocol execution (base address, erase, write, reboot)
- Progress reporting, error handling, and post-upgrade version refresh
- OTA execution-time command-channel freeze to avoid write conflicts

Out of scope for this phase:

- Dynamic OTA package list from backend
- New protocol variants beyond current FR8010 flow
- Redesign of existing BLE control architecture outside OTA boundaries

## 2. Current State and Gap

Android already has complete OTA flow in `OtaUpdateScreen.kt` with `Fr8010OtaRunner` embedded and production-grade logging, while iOS currently exposes a placeholder menu action in `F1HomeScreen` (`"OTA更新功能开发中"`).

Gap summary:

- iOS lacks OTA UI and state machine
- iOS lacks FR8010 OTA protocol runner
- iOS lacks OTA-specific telemetry and failure taxonomy
- iOS does not yet enforce OTA-time control channel freeze

## 3. Selected Approach

Chosen approach: **A (full alignment)**.

Implement iOS OTA as a dedicated feature + dedicated OTA runner, preserving separation between normal BLE control and OTA protocol execution.

Rationale:

- Closest behavior to Android for QA parity
- Minimizes future cross-platform divergence
- Keeps OTA complexity isolated from day-to-day control commands

## 4. Architecture

### 4.1 Components

1. `F1OtaUpdateScreen` (SwiftUI)
2. `F1OtaViewModel` (state + orchestration)
3. `Fr8010OtaRunner` (CoreBluetooth OTA protocol engine)
4. `OtaPackageCatalog` (fixed in-app package options, same as Android for now)
5. Existing BLE repository/manager integration for availability and channel freeze

### 4.2 Boundaries

- `IOSBleManager` remains responsible for normal encrypted control protocol.
- `Fr8010OtaRunner` owns OTA-specific GATT session and OTA commands.
- ViewModel coordinates user actions and state transitions only; no protocol details.

### 4.3 Entry Wiring

- Replace "检查OTA更新" placeholder action in `F1HomeScreen` with navigation to OTA screen.
- Preserve existing navigation style and visual language in iOS app.

## 5. Protocol Alignment (Android -> iOS)

### 5.1 UUIDs

- OTA service: `02f00000-0000-0000-0000-00000000fe00`
- OTA write characteristic: `02f00000-0000-0000-0000-00000000ff01`
- OTA notify characteristic: `02f00000-0000-0000-0000-00000000ff02`
- CCCD: `00002902-0000-1000-8000-00805f9b34fb`
- Device Information Service: `0000180a-0000-1000-8000-00805f9b34fb`
- Firmware Revision String: `00002a26-0000-1000-8000-00805f9b34fb`

### 5.2 OTA Commands and Sequence

Opcode parity with Android:

- `1`: GET_STR_BASE
- `3`: PAGE_ERASE
- `5`: WRITE_DATA
- `9`: REBOOT

Execution order:

1. Ensure connected and characteristics discovered
2. Enable notify + CCCD write success
3. Request MTU (target 247, tolerate fallback)
4. Send `GET_STR_BASE`, wait ACK notify, parse base address
5. Erase sectors by 4KB pages (`PAGE_ERASE` loop)
6. Chunk write firmware (`WRITE_DATA`) where payload size is `mtu - 3 - 9`, minimum 20
7. Send reboot command with file length + legacy CRC

### 5.3 ACK / Timeout Rules

- For each command requiring response, use send-and-wait-ack with timeout.
- Timeout produces actionable error (`ACK_TIMEOUT_<OPCODE>`).
- Any missing/invalid notify response terminates OTA as failed.

### 5.4 CRC Parity

- Use same FR8010 legacy CRC algorithm as Android (`calcFr8010LegacyCrc` behavior).
- CRC range starts from byte index 256.

## 6. UI and State Model

### 6.1 UI Elements

- BLE MAC display
- Current firmware version display
- Local model type display
- Target package option list (fixed URL options)
- Start OTA button + confirmation modal
- Progress percent + progress text
- "Refresh version after upgrade" button

### 6.2 State Machine

`idle -> preparing -> downloading -> requestingBase -> erasing -> writing -> rebooting -> success`

Error path:

- Any stage can transition to `failed(errorMessage)`.

Interaction rules:

- While running, disable repeated start and package switching.
- While running, freeze normal control command dispatch.

## 7. Package Source Strategy (Phase 1)

Use fixed package options in-app, mirroring Android behavior for immediate parity and testing speed.

Planned follow-up (out of this phase):

- switch to backend-driven package catalog after test validation.

## 8. Error Handling

Primary error categories:

- `BLE_UNAVAILABLE`
- `BLE_CONNECT_FAILED`
- `SERVICE_DISCOVERY_FAILED`
- `CHARACTERISTIC_MISSING`
- `CCCD_WRITE_FAILED`
- `DOWNLOAD_FAILED`
- `BIN_INVALID`
- `ACK_TIMEOUT_<OPCODE>`
- `WRITE_FAILED`
- `OTA_ABORTED`

UX behavior:

- Show concise user-visible message
- Keep detailed technical reason in logs
- Allow clean retry after failure

## 9. Logging and Observability

Log tags:

- Protocol/runner: `[OTA-FR8010-iOS]`
- UI layer: `[OTA-FR8010-UI-iOS]`

Must-log checkpoints:

- connection / reuse / disconnect
- service discovery and characteristic binding results
- CCCD write start/result
- MTU request result
- TX opcode + payload preview
- RX notify + ACK mapping
- erase/write progress checkpoints
- reboot command send
- final result and failure reason

## 10. Testing and Acceptance

### 10.1 Automated

- Unit tests for:
  - opcode packet builders
  - CRC calculation parity with Android known vectors
  - progress mapping logic
  - state transitions (success and representative failures)

### 10.2 Manual Acceptance

1. Open OTA page, read current version successfully.
2. Start OTA with supported package, reach 100%.
3. Refresh version and observe expected version change.
4. Simulate interruption/error and verify clear failure + recoverable retry.
5. Confirm no regular control command is sent during OTA run.

## 11. Risks and Mitigations

- **Risk:** iOS peripheral lifecycle differences vs Android callbacks.
  - **Mitigation:** strict timeout boundaries + explicit state cleanup.
- **Risk:** MTU negotiation may not reach 247 on some devices.
  - **Mitigation:** dynamic payload sizing with safe minimum.
- **Risk:** OTA and normal BLE writes collide.
  - **Mitigation:** runner-level lock + repository-level freeze gate.

## 12. Implementation Deliverables

1. New iOS OTA screen and navigation from F1 user center
2. `F1OtaViewModel` with OTA state machine
3. `Fr8010OtaRunner` with Android-parity protocol flow
4. Fixed OTA package catalog aligned to Android options
5. OTA logs and user-facing errors
6. Unit tests for protocol/state/CRC
