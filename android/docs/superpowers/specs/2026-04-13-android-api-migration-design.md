# Android API Migration Design (Full Parity)

Date: 2026-04-13
Owner: OpenCode
Scope: `./app/android` only (no UniApp code changes)

## 1. Goal

Migrate UniApp `utils/api.js` API capabilities into Android code with behavior parity, focusing on:

- Full endpoint coverage required by current app flows.
- Login request/response/storage behavior aligned with `pages/index/login.vue` + `stores/user.js`.
- No test/mock login path in Android implementation.
- Keep current F2 control routing strategy (BLE preferred, fallback to 4G when BLE unavailable, reconnect BLE in background).

## 2. Non-Goals

- No changes under UniApp source.
- No backend contract changes.
- No UI redesign for Android screens in this phase.

## 3. Constraints and Alignment Rules

- Android must keep existing request signing semantics equivalent to UniApp:
  - headers: `UUID`, `X-Timestamp`, `X-Sign`, `Content-Type`.
  - sign params include timestamp + query params + `body` JSON for POST/PUT.
- Login payload alignment:
  - send `phone` + `code` + device fields (`cid`, `build_model`, `os_version`, `device_name`, `app_version`).
  - if local UUID exists, prefer request body key `UUID` over `phone` (same precedence as UniApp).
- Login persistence alignment:
  - persist `uuid`, `userInfo`, `lastDeviceKey` (if provided), and agreement flag behavior equivalent to Android UX intent.
- Current user identity source must not rely on in-memory-only state.

## 4. Recommended Architecture (Chosen)

Use a layered migration in `app/android`:

1. **Network Foundation Layer**
   - Keep `AuthInterceptor` + `SignatureHelper` as single source for signing/header parity.
   - Introduce a UUID storage provider (SharedPreferences-backed) used by interceptor and auth flows.
   - Keep one Retrofit instance; split services by domain.

2. **API Contract Layer**
   - Expand `AppApiService`/domain-specific services to cover `utils/api.js` endpoints needed for app parity.
   - Add request/response DTOs with `@SerialName` mapped exactly to backend fields.

3. **Repository Layer**
   - Create cohesive repositories (`AuthRepository`, `MineRepository`, `ControlRepository`, `ShareRepository`, etc.).
   - Map `ApiResponse<T>` to domain `AppResult<T>` with consistent error mapping.

4. **Session/Storage Layer**
   - Add `SessionStore` abstraction for `uuid`, `userInfo`, `lastDeviceKey`, agreement acceptance.
   - Auth flow writes session through one entry point (no duplicated ad-hoc writes in UI).

5. **Feature Integration Layer**
   - `AuthViewModel` and `F2ViewModel` consume repositories/use-cases only.
   - F2 command path remains policy-driven: BLE first, fallback 4G, no one-way 4G-only lock.

## 5. Endpoint Migration Plan

Migrate `utils/api.js` groups in this order:

1. **Auth**
   - `/api/sms/send`
   - `/api/sms/verify`
   - `/api/login-or-register`
   - `/api/login-with-password`
   - `/api/token2mobile`
   - `/api/set-password`

2. **User/Mine**
   - `/mine/profile`, `/mine/logout`
   - device list/detail/settings: `/mine/user-devices`, `/mine/device-info`, `/mine/settings`, `/mine/set-default-device`
   - bind/unbind/share owner related endpoints from `api.js`

3. **Control/Vehicle**
   - `/api/control`, `/api/setting`
   - lock/find/mute/auto-sense/sos/gps flows used by F2.

4. **Support APIs**
   - location, update check, BLE logs, CID upsert, BLE update check/report.

Each endpoint must map request keys and response keys exactly (snake_case and uppercase `UUID` preserved where required).

## 6. Login Parity Specification (Critical)

### Request parity

- `send code`: same phone format validation (`^1[3-9]\d{9}$`).
- `submit login`: require agreement + non-empty phone/code.
- login body fields:
  - always include `code` and device info fields.
  - include `UUID` if local UUID exists; otherwise include `phone`.

### Response and storage parity

- On success:
  - store `uuid`.
  - call profile endpoint and store full `userInfo`.
  - store `lastDeviceKey` when backend returns default device.
- Remove Android test-code shortcuts and in-memory-only auth assumptions.

### Error parity

- Keep user-facing messages equivalent to UniApp intent:
  - empty fields, agreement not accepted, invalid phone, network failure, business error.

## 7. F2 Availability and "No Channel" Fix Direction

- Keep channel selection policy unchanged in behavior target:
  - F2: BLE preferred; fallback 4G if BLE unavailable and network available.
- Fix false `NO_CHANNEL` by separating:
  - **transport availability** (BLE state, network reachability), and
  - **auth/session readiness** (valid uuid + signed request capability).
- Ensure 4G availability is not hardcoded; drive from actual connectivity/session state.
- Add structured logs for command routing decisions and API request prerequisites.

## 8. Logging and Observability

Add detailed logs (without sensitive payload leakage) for:

- auth: request shape decisions (`UUID` vs `phone`), login success/failure path, storage writes.
- f2 control: selected channel, BLE/network state snapshot, API result code/message.
- BLE lifecycle: connect attempts, active MAC, retry count, terminal reason.

Log format should keep current `AppLogger` style and tags so triage remains searchable.

## 9. Risks and Mitigations

- **Risk:** field mismatch (`UUID` vs `uuid`) causes auth failures.
  - **Mitigation:** explicit DTOs and request builders with parity checks.
- **Risk:** duplicated storage writes in UI and domain layers diverge.
  - **Mitigation:** central `SessionStore`; UI only reacts to state.
- **Risk:** API parity scope too wide for one pass.
  - **Mitigation:** migrate by endpoint groups with compile-safe repository boundaries.

## 10. Acceptance Criteria

- Android side covers all required `utils/api.js` endpoints used by current app flows.
- Login sends/stores data aligned with `login.vue` + `stores/user.js` behavior.
- No test/mock login path remains.
- F2 lock/unlock no longer fails with false `暂无可用通道` when 4G is actually available.
- All changes are limited to `./app/android`.
