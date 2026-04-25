# iOS SwiftUI Login + F1 Static UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a new Swift + SwiftUI iOS app that statically reproduces the provided Android Login and F1 screenshots with high visual fidelity.

**Architecture:** Use a small feature-first SwiftUI structure with a shared DesignSystem layer. Implement only static rendering (no network/BLE/map SDK), and use local assets and preview data. Keep UI composition modular so future state/logic integration can be added without layout rewrites.

**Tech Stack:** Swift 5.9+, SwiftUI, XCTest, XcodeGen (project generation), xcodebuild

---

## File Structure (planned)

- Create: `ios/project.yml` (XcodeGen project definition)
- Create: `ios/ZongshenApp/Resources/Assets.xcassets/` (image/color assets)
- Create: `ios/ZongshenApp/App/ZongshenApp.swift` (app entry)
- Create: `ios/ZongshenApp/App/RootScreen.swift` (temporary static screen switcher)
- Create: `ios/ZongshenApp/DesignSystem/AppColor.swift`
- Create: `ios/ZongshenApp/DesignSystem/AppSpacing.swift`
- Create: `ios/ZongshenApp/DesignSystem/AppRadius.swift`
- Create: `ios/ZongshenApp/DesignSystem/AppTypography.swift`
- Create: `ios/ZongshenApp/DesignSystem/AppShadow.swift`
- Create: `ios/ZongshenApp/Features/Login/LoginScreen.swift`
- Create: `ios/ZongshenApp/Features/Login/LoginComponents.swift`
- Create: `ios/ZongshenApp/Features/F1/F1HomeScreen.swift`
- Create: `ios/ZongshenApp/Features/F1/F1Components.swift`
- Create: `ios/ZongshenApp/Features/F1/F1MockData.swift`
- Create: `ios/ZongshenAppTests/LoginLayoutTests.swift`
- Create: `ios/ZongshenAppTests/F1LayoutTests.swift`
- Modify: `docs/superpowers/specs/2026-04-20-ios-swiftui-login-f1-static-design.md` (link implementation status)

### Task 1: Bootstrap iOS SwiftUI project

**Files:**
- Create: `ios/project.yml`
- Create: `ios/ZongshenApp/App/ZongshenApp.swift`
- Create: `ios/ZongshenApp/App/RootScreen.swift`

- [ ] **Step 1: Write the failing smoke test target reference**

```swift
import XCTest

final class AppBootstrapTests: XCTestCase {
    func test_canInstantiateRootScreenType() {
        _ = RootScreen()
        XCTAssertTrue(true)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ios && xcodegen generate && xcodebuild test -scheme ZongshenApp -destination 'platform=iOS Simulator,name=iPhone 15'`

Expected: FAIL with compile error similar to `cannot find 'RootScreen' in scope`

- [ ] **Step 3: Add minimal project and app entry implementation**

```yaml
# ios/project.yml
name: ZongshenApp
options:
  minimumXcodeGenVersion: 2.38.0
targets:
  ZongshenApp:
    type: application
    platform: iOS
    deploymentTarget: "16.0"
    sources:
      - ZongshenApp
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: com.xiaochao.app
        INFOPLIST_FILE: ZongshenApp/Info.plist
  ZongshenAppTests:
    type: bundle.unit-test
    platform: iOS
    sources:
      - ZongshenAppTests
    dependencies:
      - target: ZongshenApp
```

```swift
// ios/ZongshenApp/App/ZongshenApp.swift
import SwiftUI

@main
struct ZongshenApp: App {
    var body: some Scene {
        WindowGroup {
            RootScreen()
        }
    }
}
```

```swift
// ios/ZongshenApp/App/RootScreen.swift
import SwiftUI

struct RootScreen: View {
    var body: some View {
        Text("Placeholder")
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ios && xcodegen generate && xcodebuild test -scheme ZongshenApp -destination 'platform=iOS Simulator,name=iPhone 15'`

Expected: PASS for bootstrap test target.

- [ ] **Step 5: Commit**

```bash
git add ios/project.yml ios/ZongshenApp/App/ZongshenApp.swift ios/ZongshenApp/App/RootScreen.swift ios/ZongshenAppTests/AppBootstrapTests.swift
git commit -m "feat(ios): bootstrap SwiftUI app with bundle identifier"
```

### Task 2: Build DesignSystem tokens and static page switcher

**Files:**
- Create: `ios/ZongshenApp/DesignSystem/AppColor.swift`
- Create: `ios/ZongshenApp/DesignSystem/AppSpacing.swift`
- Create: `ios/ZongshenApp/DesignSystem/AppRadius.swift`
- Create: `ios/ZongshenApp/DesignSystem/AppTypography.swift`
- Create: `ios/ZongshenApp/DesignSystem/AppShadow.swift`
- Modify: `ios/ZongshenApp/App/RootScreen.swift`
- Test: `ios/ZongshenAppTests/LoginLayoutTests.swift`

- [ ] **Step 1: Write failing test for key design constants**

```swift
import XCTest
@testable import ZongshenApp

final class LoginLayoutTests: XCTestCase {
    func test_loginPrimaryBlueHex() {
        XCTAssertEqual(AppColor.primaryBlueHex, "#1383F6")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ios && xcodebuild test -scheme ZongshenApp -destination 'platform=iOS Simulator,name=iPhone 15'`

Expected: FAIL with `cannot find 'AppColor' in scope`.

- [ ] **Step 3: Implement token files and root switcher**

```swift
// ios/ZongshenApp/DesignSystem/AppColor.swift
import SwiftUI

enum AppColor {
    static let primaryBlueHex = "#1383F6"
    static let loginBackground = Color(red: 0.95, green: 0.95, blue: 0.95)
    static let f1Background = Color(red: 0.78, green: 0.84, blue: 0.95)
}
```

```swift
// ios/ZongshenApp/App/RootScreen.swift
import SwiftUI

enum StaticScreenType: String, CaseIterable {
    case login = "Login"
    case f1 = "F1"
}

struct RootScreen: View {
    @State private var selected: StaticScreenType = .login

    var body: some View {
        VStack(spacing: 0) {
            Picker("Screen", selection: $selected) {
                ForEach(StaticScreenType.allCases, id: \.self) { type in
                    Text(type.rawValue).tag(type)
                }
            }
            .pickerStyle(.segmented)
            .padding()

            Group {
                if selected == .login {
                    Text("Login placeholder")
                } else {
                    Text("F1 placeholder")
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `cd ios && xcodebuild test -scheme ZongshenApp -destination 'platform=iOS Simulator,name=iPhone 15'`

Expected: PASS for token and bootstrap tests.

- [ ] **Step 5: Commit**

```bash
git add ios/ZongshenApp/DesignSystem ios/ZongshenApp/App/RootScreen.swift ios/ZongshenAppTests/LoginLayoutTests.swift
git commit -m "feat(ios): add design tokens and static screen switcher"
```

### Task 3: Implement Login static screenshot layout

**Files:**
- Create: `ios/ZongshenApp/Features/Login/LoginScreen.swift`
- Create: `ios/ZongshenApp/Features/Login/LoginComponents.swift`
- Modify: `ios/ZongshenApp/Resources/Assets.xcassets/`
- Test: `ios/ZongshenAppTests/LoginLayoutTests.swift`

- [ ] **Step 1: Write failing layout assertions**

```swift
import XCTest
@testable import ZongshenApp

final class LoginLayoutTests: XCTestCase {
    func test_loginStaticMetrics() {
        XCTAssertEqual(LoginLayout.primaryButtonHeight, 50)
        XCTAssertEqual(LoginLayout.horizontalPadding, 30)
        XCTAssertEqual(LoginLayout.buttonCornerRadius, 8)
    }
}
```

- [ ] **Step 2: Run tests to verify fail**

Run: `cd ios && xcodebuild test -scheme ZongshenApp -destination 'platform=iOS Simulator,name=iPhone 15'`

Expected: FAIL with `cannot find 'LoginLayout' in scope`.

- [ ] **Step 3: Implement Login static view and constants**

```swift
// ios/ZongshenApp/Features/Login/LoginScreen.swift
import SwiftUI

enum LoginLayout {
    static let horizontalPadding: CGFloat = 30
    static let primaryButtonHeight: CGFloat = 50
    static let buttonCornerRadius: CGFloat = 8
}

struct LoginScreen: View {
    var body: some View {
        VStack(spacing: 0) {
            Spacer().frame(height: 130)
            LoginLogoBlock()
            Spacer().frame(height: 90)
            LoginButtonsBlock()
            Spacer().frame(height: 36)
            LoginAgreementRow()
            Spacer()
        }
        .padding(.horizontal, LoginLayout.horizontalPadding)
        .background(AppColor.loginBackground.ignoresSafeArea())
    }
}
```

```swift
// ios/ZongshenApp/Features/Login/LoginComponents.swift
import SwiftUI

struct LoginLogoBlock: View {
    var body: some View {
        VStack(spacing: 18) {
            Image("app_logo")
                .resizable()
                .scaledToFit()
                .frame(width: 120, height: 120)
            Text("宗申智行")
                .font(.system(size: 42, weight: .semibold))
                .foregroundStyle(.black)
        }
    }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `cd ios && xcodebuild test -scheme ZongshenApp -destination 'platform=iOS Simulator,name=iPhone 15'`

Expected: PASS for login layout tests.

- [ ] **Step 5: Commit**

```bash
git add ios/ZongshenApp/Features/Login ios/ZongshenApp/Resources/Assets.xcassets ios/ZongshenAppTests/LoginLayoutTests.swift
git commit -m "feat(ios): add static login screen matching Android screenshot"
```

### Task 4: Implement F1 static screenshot layout

**Files:**
- Create: `ios/ZongshenApp/Features/F1/F1HomeScreen.swift`
- Create: `ios/ZongshenApp/Features/F1/F1Components.swift`
- Create: `ios/ZongshenApp/Features/F1/F1MockData.swift`
- Test: `ios/ZongshenAppTests/F1LayoutTests.swift`

- [ ] **Step 1: Write failing metric tests for F1 block layout**

```swift
import XCTest
@testable import ZongshenApp

final class F1LayoutTests: XCTestCase {
    func test_f1MainCardCornerRadius() {
        XCTAssertEqual(F1Layout.mainCardRadius, 34)
    }
}
```

- [ ] **Step 2: Run tests to verify fail**

Run: `cd ios && xcodebuild test -scheme ZongshenApp -destination 'platform=iOS Simulator,name=iPhone 15'`

Expected: FAIL with `cannot find 'F1Layout' in scope`.

- [ ] **Step 3: Implement F1 static screen and components**

```swift
// ios/ZongshenApp/Features/F1/F1HomeScreen.swift
import SwiftUI

enum F1Layout {
    static let mainCardRadius: CGFloat = 34
}

struct F1HomeScreen: View {
    var body: some View {
        VStack(spacing: 16) {
            F1HeaderView(deviceName: "GC0018002", totalMileage: "49KM")
            F1HeroView()
            F1MainControlCardView()
            F1MapCardView()
            Spacer(minLength: 0)
            F1BottomTabView()
        }
        .padding(.horizontal, 16)
        .padding(.top, 12)
        .background(AppColor.f1Background.ignoresSafeArea())
    }
}
```

```swift
// ios/ZongshenApp/Features/F1/F1Components.swift
import SwiftUI

struct F1HeaderView: View {
    let deviceName: String
    let totalMileage: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(deviceName)
                .font(.system(size: 36, weight: .bold))
            Text("历史总里程：\(totalMileage)")
                .font(.system(size: 20, weight: .regular))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `cd ios && xcodebuild test -scheme ZongshenApp -destination 'platform=iOS Simulator,name=iPhone 15'`

Expected: PASS for F1 layout tests.

- [ ] **Step 5: Commit**

```bash
git add ios/ZongshenApp/Features/F1 ios/ZongshenAppTests/F1LayoutTests.swift
git commit -m "feat(ios): add static F1 home screen matching Android screenshot"
```

### Task 5: Import Android assets and wire final static rendering

**Files:**
- Modify: `ios/ZongshenApp/Resources/Assets.xcassets/`
- Modify: `ios/ZongshenApp/Features/Login/LoginComponents.swift`
- Modify: `ios/ZongshenApp/Features/F1/F1Components.swift`

- [ ] **Step 1: Write failing tests for required asset names**

```swift
import XCTest

final class RequiredAssetTests: XCTestCase {
    func test_requiredAssetNamesAreDeclared() {
        let names = RequiredAssets.all
        XCTAssertTrue(names.contains("app_logo"))
        XCTAssertTrue(names.contains("f2_bike"))
        XCTAssertTrue(names.contains("nav_home"))
    }
}
```

- [ ] **Step 2: Run tests to verify fail**

Run: `cd ios && xcodebuild test -scheme ZongshenApp -destination 'platform=iOS Simulator,name=iPhone 15'`

Expected: FAIL with `cannot find 'RequiredAssets' in scope`.

- [ ] **Step 3: Implement asset registry and apply to views**

```swift
// ios/ZongshenApp/DesignSystem/RequiredAssets.swift
enum RequiredAssets {
    static let all: [String] = [
        "app_logo",
        "f2_bike",
        "f2_map_current",
        "nav_home",
        "nav_mine"
    ]
}
```

```swift
// in F1 hero view
Image("f2_bike")
    .resizable()
    .scaledToFit()
    .frame(maxWidth: .infinity)
```

- [ ] **Step 4: Run tests to verify pass**

Run: `cd ios && xcodebuild test -scheme ZongshenApp -destination 'platform=iOS Simulator,name=iPhone 15'`

Expected: PASS for required-asset tests.

- [ ] **Step 5: Commit**

```bash
git add ios/ZongshenApp/Resources/Assets.xcassets ios/ZongshenApp/DesignSystem/RequiredAssets.swift ios/ZongshenApp/Features/Login/LoginComponents.swift ios/ZongshenApp/Features/F1/F1Components.swift ios/ZongshenAppTests/RequiredAssetTests.swift
git commit -m "chore(ios): import Android assets and wire static image rendering"
```

### Task 6: Fidelity tuning and validation pass

**Files:**
- Modify: `ios/ZongshenApp/Features/Login/LoginScreen.swift`
- Modify: `ios/ZongshenApp/Features/F1/F1HomeScreen.swift`
- Modify: `ios/ZongshenApp/Features/F1/F1Components.swift`
- Modify: `docs/superpowers/specs/2026-04-20-ios-swiftui-login-f1-static-design.md`

- [ ] **Step 1: Write failing tests for final layout constants**

```swift
import XCTest
@testable import ZongshenApp

final class FinalMetricsTests: XCTestCase {
    func test_finalConstantsLocked() {
        XCTAssertEqual(LoginLayout.horizontalPadding, 30)
        XCTAssertEqual(F1Layout.mainCardRadius, 34)
    }
}
```

- [ ] **Step 2: Run tests to verify fail (before constant tune)**

Run: `cd ios && xcodebuild test -scheme ZongshenApp -destination 'platform=iOS Simulator,name=iPhone 15'`

Expected: FAIL when constants are not yet tuned to final values.

- [ ] **Step 3: Apply final visual tuning and doc status update**

```swift
// Login constants
enum LoginLayout {
    static let horizontalPadding: CGFloat = 30
    static let primaryButtonHeight: CGFloat = 50
    static let buttonCornerRadius: CGFloat = 8
}

// F1 constants
enum F1Layout {
    static let mainCardRadius: CGFloat = 34
    static let topSectionSpacing: CGFloat = 16
    static let mainCardHorizontalPadding: CGFloat = 16
}
```

```markdown
<!-- append to spec file -->
## Implementation Status

- [x] iOS app scaffolded
- [x] Login static page complete
- [x] F1 static page complete
- [x] Asset mapping complete
```

- [ ] **Step 4: Run all tests to verify pass**

Run: `cd ios && xcodebuild test -scheme ZongshenApp -destination 'platform=iOS Simulator,name=iPhone 15'`

Expected: PASS for all test classes.

- [ ] **Step 5: Commit**

```bash
git add ios/ZongshenApp docs/superpowers/specs/2026-04-20-ios-swiftui-login-f1-static-design.md ios/ZongshenAppTests
git commit -m "refactor(ios): tune static login and F1 UI for screenshot fidelity"
```

## Verification Commands (final)

Run in order:

1. `cd ios && xcodegen generate`
2. `cd ios && xcodebuild test -scheme ZongshenApp -destination 'platform=iOS Simulator,name=iPhone 15'`
3. `cd ios && xcodebuild -scheme ZongshenApp -destination 'platform=iOS Simulator,name=iPhone 15' build`

Expected:

- Project generation succeeds
- Tests all pass
- Simulator build succeeds with no compile errors

## Self-Review Checklist

- Spec coverage: includes iOS scaffold, Login static, F1 static, assets, and fidelity validation.
- Placeholder scan: no TBD/TODO language, every task has concrete file paths and commands.
- Type consistency: `LoginLayout`, `F1Layout`, `RootScreen`, and feature filenames are consistent across tasks.
