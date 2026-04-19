# Android Full API Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port UniApp `utils/api.js` API behavior to Android (`./app/android`) with login payload/storage parity and reliable F2 channel fallback.

**Architecture:** Keep a single signed Retrofit client, split API declarations by domain, centralize session persistence in one store, and route feature code through repositories/use-cases. Fix false `NO_CHANNEL` by deriving network availability from real connectivity + session readiness instead of static defaults.

**Tech Stack:** Kotlin, Coroutines/Flow, Retrofit, OkHttp, kotlinx.serialization, Jetpack Compose, JUnit.

---

## File Structure

- `app/android/app/src/main/java/xiaochao/com/data/network/AppApiService.kt`
  - Expand endpoint declarations and DTOs for parity groups.
- `app/android/app/src/main/java/xiaochao/com/data/network/AppAuthApiService.kt`
  - Align login/send-code/verify-code/password/token APIs with exact request keys (`UUID`, snake_case fields).
- `app/android/app/src/main/java/xiaochao/com/data/network/AuthInterceptor.kt`
  - Read UUID from centralized session store (not memory only).
- `app/android/app/src/main/java/xiaochao/com/data/session/SessionStore.kt` (create)
  - Single source of truth for `uuid`, `userInfo`, `lastDeviceKey`, agreement flag.
- `app/android/app/src/main/java/xiaochao/com/data/session/UserInfoCache.kt` (create)
  - Serialize/deserialize persisted user profile JSON.
- `app/android/app/src/main/java/xiaochao/com/data/auth/AuthRepository.kt`
  - Build login payload exactly like UniApp and persist parity fields.
- `app/android/app/src/main/java/xiaochao/com/domain/auth/LoginUseCase.kt`
  - Keep validation, call repository parity flow.
- `app/android/app/src/main/java/xiaochao/com/feature/auth/presentation/AuthViewModel.kt`
  - Remove ad-hoc persistence and mock/test paths.
- `app/android/app/src/main/java/xiaochao/com/data/api/ApiRepository.kt`
  - Add missing API methods required by parity list.
- `app/android/app/src/main/java/xiaochao/com/data/api/ApiRepositoryImpl.kt`
  - Implement new methods and uniform error mapping.
- `app/android/app/src/main/java/xiaochao/com/data/ble/AndroidBleManager.kt`
  - Compute network availability from connectivity callback.
- `app/android/app/src/main/java/xiaochao/com/data/ble/BleRepositoryImpl.kt`
  - Improve reconnect logs + availability transitions.
- `app/android/app/src/main/java/xiaochao/com/feature/f2/presentation/F2ViewModel.kt`
  - Add detailed command precondition/channel logs.
- `app/android/app/src/test/java/xiaochao/com/data/auth/AuthRepositoryParityTest.kt` (create)
  - Verify login request body key precedence and storage writes.
- `app/android/app/src/test/java/xiaochao/com/domain/control/ControlChannelPolicyTest.kt`
  - Extend coverage for F2 fallback and NONE cases.

### Task 1: Build Session Store and UUID Source of Truth

**Files:**
- Create: `app/android/app/src/main/java/xiaochao/com/data/session/SessionStore.kt`
- Create: `app/android/app/src/main/java/xiaochao/com/data/session/UserInfoCache.kt`
- Modify: `app/android/app/src/main/java/xiaochao/com/core/AppConfig.kt`
- Modify: `app/android/app/src/main/java/xiaochao/com/data/network/AuthInterceptor.kt`
- Test: `app/android/app/src/test/java/xiaochao/com/data/auth/AuthRepositoryParityTest.kt`

- [ ] **Step 1: Write failing test for persisted UUID precedence**

```kotlin
@Test
fun `interceptor reads uuid from session store when memory uuid empty`() {
    val session = FakeSessionStore(uuid = "persisted-uuid")
    val interceptor = AuthInterceptor(session)
    val request = Request.Builder().url("https://api.tr.sheyutech.com/app-api/mine/profile").get().build()
    val chain = FakeChain(request)

    interceptor.intercept(chain)

    assertEquals("persisted-uuid", chain.capturedRequest.header("UUID"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*AuthRepositoryParityTest"`
Expected: FAIL because `AuthInterceptor` has no `SessionStore` dependency.

- [ ] **Step 3: Implement `SessionStore` + interceptor integration**

```kotlin
interface SessionStore {
    fun getUuid(): String
    fun setUuid(uuid: String)
    fun getLastDeviceKey(): String
    fun setLastDeviceKey(deviceKey: String)
    fun getUserInfoJson(): String
    fun setUserInfoJson(value: String)
    fun isAgreedToTerms(): Boolean
    fun setAgreedToTerms(agreed: Boolean)
}
```

```kotlin
class AuthInterceptor(
    private val sessionStore: SessionStore = AppSessionStoreProvider.store
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val uuid = sessionStore.getUuid().ifEmpty { xiaochao.com.core.AppConfig.deviceUuid }
        val newRequest = chain.request().newBuilder().header("UUID", uuid).build()
        return chain.proceed(newRequest)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*AuthRepositoryParityTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/android/app/src/main/java/xiaochao/com/data/session/SessionStore.kt app/android/app/src/main/java/xiaochao/com/data/session/UserInfoCache.kt app/android/app/src/main/java/xiaochao/com/data/network/AuthInterceptor.kt app/android/app/src/main/java/xiaochao/com/core/AppConfig.kt app/android/app/src/test/java/xiaochao/com/data/auth/AuthRepositoryParityTest.kt
git commit -m "auth: centralize session storage for UUID and user state"
```

### Task 2: Align Login Payload and Storage with UniApp

**Files:**
- Modify: `app/android/app/src/main/java/xiaochao/com/data/network/AppAuthApiService.kt`
- Modify: `app/android/app/src/main/java/xiaochao/com/data/auth/AuthRepository.kt`
- Modify: `app/android/app/src/main/java/xiaochao/com/domain/auth/LoginUseCase.kt`
- Modify: `app/android/app/src/main/java/xiaochao/com/feature/auth/presentation/AuthViewModel.kt`
- Test: `app/android/app/src/test/java/xiaochao/com/data/auth/AuthRepositoryParityTest.kt`

- [ ] **Step 1: Write failing tests for request body alignment**

```kotlin
@Test
fun `login uses UUID field and omits phone when uuid exists`() = runTest {
    fakeSession.uuid = "existing-uuid"
    repository.loginWithSms("13800138000", "123456")
    assertEquals("existing-uuid", fakeApi.lastLoginRequest.uuid)
    assertNull(fakeApi.lastLoginRequest.phone)
}

@Test
fun `login includes phone when no uuid exists`() = runTest {
    fakeSession.uuid = ""
    repository.loginWithSms("13800138000", "123456")
    assertEquals("13800138000", fakeApi.lastLoginRequest.phone)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*AuthRepositoryParityTest"`
Expected: FAIL due to missing payload/session behavior.

- [ ] **Step 3: Implement payload/device-info/session parity**

```kotlin
@Serializable
data class LoginOrRegisterRequest(
    val phone: String? = null,
    @SerialName("UUID") val uuid: String? = null,
    val code: String,
    val cid: String? = null,
    @SerialName("build_model") val buildModel: String? = null,
    @SerialName("os_version") val osVersion: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
)
```

```kotlin
val existingUuid = sessionStore.getUuid().ifEmpty { null }
val request = LoginOrRegisterRequest(
    phone = if (existingUuid == null) phone else null,
    uuid = existingUuid,
    code = code,
    cid = deviceInfo.cid,
    buildModel = deviceInfo.buildModel,
    osVersion = deviceInfo.osVersion,
    deviceName = deviceInfo.deviceName,
    appVersion = deviceInfo.appVersion,
)
```

```kotlin
if (response.code == 0 && response.data != null) {
    sessionStore.setUuid(response.data.uuid)
    sessionStore.setLastDeviceKey(response.data.defaultDeviceKey.orEmpty())
    sessionStore.setUserInfoJson(json.encodeToString(profile))
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./gradlew testDebugUnitTest --tests "*AuthRepositoryParityTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/android/app/src/main/java/xiaochao/com/data/network/AppAuthApiService.kt app/android/app/src/main/java/xiaochao/com/data/auth/AuthRepository.kt app/android/app/src/main/java/xiaochao/com/domain/auth/LoginUseCase.kt app/android/app/src/main/java/xiaochao/com/feature/auth/presentation/AuthViewModel.kt app/android/app/src/test/java/xiaochao/com/data/auth/AuthRepositoryParityTest.kt
git commit -m "auth: align login payload and storage with uniapp"
```

### Task 3: Expand Android API Surface to Match `utils/api.js`

**Files:**
- Modify: `app/android/app/src/main/java/xiaochao/com/data/network/AppApiService.kt`
- Modify: `app/android/app/src/main/java/xiaochao/com/data/api/ApiRepository.kt`
- Modify: `app/android/app/src/main/java/xiaochao/com/data/api/ApiRepositoryImpl.kt`
- Test: `app/android/app/src/test/java/xiaochao/com/feature/f2/F2ViewModelTest.kt`

- [ ] **Step 1: Write failing tests for missing repository methods**

```kotlin
@Test
fun `repository exposes update cid and ble log operations`() {
    val methods = ApiRepository::class.members.map { it.name }
    assertTrue(methods.contains("upsertCid"))
    assertTrue(methods.contains("createBleLog"))
}
```

- [ ] **Step 2: Run tests to verify fail**

Run: `./gradlew testDebugUnitTest --tests "*F2ViewModelTest"`
Expected: FAIL because methods/endpoints are absent.

- [ ] **Step 3: Add endpoint + repository coverage by groups**

```kotlin
@POST("mine/upsert-cid")
suspend fun upsertCid(@Body body: UpsertCidBody): ApiResponse<Unit>

@POST("mine/ble/logs")
suspend fun createBleLog(@Body body: BleLogBody): ApiResponse<Unit>

@GET("api/ble/check_update")
suspend fun bleCheckUpdate(
    @Query("device_key") deviceKey: String,
    @Query("version") version: String
): ApiResponse<BleUpdateDto>
```

```kotlin
override suspend fun upsertCid(cid: String): AppResult<Unit> =
    safeCall { apiService.upsertCid(UpsertCidBody(cid)) }
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./gradlew testDebugUnitTest --tests "*F2ViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/android/app/src/main/java/xiaochao/com/data/network/AppApiService.kt app/android/app/src/main/java/xiaochao/com/data/api/ApiRepository.kt app/android/app/src/main/java/xiaochao/com/data/api/ApiRepositoryImpl.kt app/android/app/src/test/java/xiaochao/com/feature/f2/F2ViewModelTest.kt
git commit -m "api: add android endpoint parity for mine and ble operations"
```

### Task 4: Fix F2 `NO_CHANNEL` False Negatives and Add Diagnostic Logs

**Files:**
- Modify: `app/android/app/src/main/java/xiaochao/com/data/ble/AndroidBleManager.kt`
- Modify: `app/android/app/src/main/java/xiaochao/com/data/ble/BleRepositoryImpl.kt`
- Modify: `app/android/app/src/main/java/xiaochao/com/domain/control/SendControlCommandUseCase.kt`
- Modify: `app/android/app/src/main/java/xiaochao/com/feature/f2/presentation/F2ViewModel.kt`
- Modify: `app/android/app/src/test/java/xiaochao/com/domain/control/ControlChannelPolicyTest.kt`

- [ ] **Step 1: Write failing tests for F2 fallback selection**

```kotlin
@Test
fun `f2 uses 4g when ble unavailable and network available`() {
    val selected = ControlChannelPolicy().select(
        ChannelAvailability(bleAvailable = false, networkAvailable = true),
        DeviceType.F2
    )
    assertEquals(CommandChannel.CELLULAR_4G, selected)
}
```

- [ ] **Step 2: Run tests to verify fail (if current behavior regressed)**

Run: `./gradlew testDebugUnitTest --tests "*ControlChannelPolicyTest"`
Expected: FAIL if channel input source is stale/hardcoded in integration path.

- [ ] **Step 3: Implement real network availability + richer logs**

```kotlin
fun updateNetworkAvailability(available: Boolean) {
    _availability.value = _availability.value.copy(networkAvailable = available)
    logPhase("network_${'$'}{if (available) "available" else "unavailable"}")
}
```

```kotlin
logger.info(
    LogEntry(
        tag = "command_route",
        message = "Route decision",
        payload = mapOf(
            "selected" to selected.name,
            "bleAvailable" to availability.bleAvailable,
            "networkAvailable" to availability.networkAvailable,
            "uuidPresent" to sessionStore.getUuid().isNotEmpty()
        )
    )
)
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./gradlew testDebugUnitTest --tests "*ControlChannelPolicyTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/android/app/src/main/java/xiaochao/com/data/ble/AndroidBleManager.kt app/android/app/src/main/java/xiaochao/com/data/ble/BleRepositoryImpl.kt app/android/app/src/main/java/xiaochao/com/domain/control/SendControlCommandUseCase.kt app/android/app/src/main/java/xiaochao/com/feature/f2/presentation/F2ViewModel.kt app/android/app/src/test/java/xiaochao/com/domain/control/ControlChannelPolicyTest.kt
git commit -m "f2: fix channel availability source and add routing diagnostics"
```

### Task 5: Remove Mock/Test Login Paths and Wire UI to Real Session

**Files:**
- Modify: `app/android/app/src/main/java/xiaochao/com/feature/auth/ui/LoginScreen.kt`
- Modify: `app/android/app/src/main/java/xiaochao/com/feature/auth/presentation/AuthViewModel.kt`
- Modify: `app/android/app/src/main/java/xiaochao/com/app/navigation/AppNavGraph.kt`
- Test: `app/android/app/src/test/java/xiaochao/com/data/auth/AuthRepositoryParityTest.kt`

- [ ] **Step 1: Write failing test for no mock-code bypass**

```kotlin
@Test
fun `login flow never bypasses repository with hardcoded code`() = runTest {
    viewModel.processIntent(AuthIntent.CodeChanged("123123"))
    viewModel.processIntent(AuthIntent.SubmitClicked)
    assertEquals(1, fakeRepository.loginCalls)
}
```

- [ ] **Step 2: Run test to verify fail (if bypass exists)**

Run: `./gradlew testDebugUnitTest --tests "*AuthRepositoryParityTest"`
Expected: FAIL if hardcoded test bypass remains.

- [ ] **Step 3: Remove bypass and keep only API-backed login**

```kotlin
private fun login() {
    val state = _uiState.value
    if (!state.isAgree) {
        _uiState.update { it.copy(errorString = "请先阅读并同意协议") }
        return
    }
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        val result = loginUseCase(state.phone, state.code)
        _uiState.update {
            if (result.isSuccess) it.copy(isLoading = false, isLoggedIn = true)
            else it.copy(isLoading = false, errorString = result.exceptionOrNull()?.message ?: "登录失败")
        }
    }
}
```

- [ ] **Step 4: Run test to verify pass**

Run: `./gradlew testDebugUnitTest --tests "*AuthRepositoryParityTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/android/app/src/main/java/xiaochao/com/feature/auth/ui/LoginScreen.kt app/android/app/src/main/java/xiaochao/com/feature/auth/presentation/AuthViewModel.kt app/android/app/src/main/java/xiaochao/com/app/navigation/AppNavGraph.kt app/android/app/src/test/java/xiaochao/com/data/auth/AuthRepositoryParityTest.kt
git commit -m "auth: remove mock login path and use persisted session"
```

### Task 6: Final Verification and Documentation Sync

**Files:**
- Modify: `app/android/docs/superpowers/specs/2026-04-13-android-api-migration-design.md`
- Modify: `app/android/docs/superpowers/plans/2026-04-13-android-api-full-parity-plan.md`

- [ ] **Step 1: Run focused unit tests**

Run: `./gradlew testDebugUnitTest --tests "*AuthRepositoryParityTest" --tests "*ControlChannelPolicyTest" --tests "*F2ViewModelTest"`
Expected: PASS for all targeted suites.

- [ ] **Step 2: Run complete Android unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 3: Build debug apk**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Update docs with final endpoint checklist**

```markdown
- [x] Auth endpoints migrated
- [x] Mine endpoints migrated
- [x] Control endpoints migrated
- [x] Support endpoints migrated
```

- [ ] **Step 5: Commit**

```bash
git add app/android/docs/superpowers/specs/2026-04-13-android-api-migration-design.md app/android/docs/superpowers/plans/2026-04-13-android-api-full-parity-plan.md
git commit -m "docs: record android api parity completion checklist"
```

## Self-Review Notes

- Spec coverage: all sections mapped to tasks (session parity, auth parity, endpoint expansion, channel/logging, verification).
- Placeholder scan: no TODO/TBD placeholders remain.
- Type consistency: request uses `UUID` (uppercase serialized key), storage fields use `uuid`, `userInfo`, `lastDeviceKey` consistently.
