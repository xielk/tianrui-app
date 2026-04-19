# Tianrui App Repository and Android APK CI Design

## Goal

Create a new GitHub private repository named `tianrui-app`, add an `ios/` directory, and set up GitHub Actions so that when files under `android/**` change, CI automatically builds a release APK and publishes an artifact named `app-<versionCode>.apk`.

## Scope

In scope:

- Initialize git in current project directory and connect to GitHub remote `tianrui-app`.
- Create and track `ios/` directory.
- Add a workflow file under `.github/workflows/`.
- Trigger workflow on `push` to `main` when `android/**` changes, plus manual trigger.
- Build Android release APK and upload artifact with dynamic file name.

Out of scope:

- iOS project scaffolding or Xcode configuration.
- Publishing APK to GitHub Releases or app stores.
- Gradle signing redesign beyond existing project behavior.

## Existing Context

- Project path: `/Users/aaron/webdata/html/app`
- Current top-level directories: `android/`, `resource/`
- Current directory is not a git repository yet.
- Android project uses Kotlin DSL build file at `android/app/build.gradle.kts`.

## Architecture

### Git and Repository Setup

- Initialize local git repository in project root.
- Ensure branch is `main`.
- Create GitHub private repository `tianrui-app` using GitHub CLI.
- Add remote `origin` and push initial commits.

### iOS Directory

- Create `ios/` and include `ios/.gitkeep` so directory is tracked.

### CI Workflow

Workflow file: `.github/workflows/android-apk.yml`

Main jobs:

1. Checkout source.
2. Set up JDK 17.
3. Ensure `gradlew` is executable.
4. Build release APK via Gradle (`:app:assembleRelease`).
5. Parse `versionCode` from `android/app/build.gradle.kts`.
6. Locate generated release APK and rename to `app-<versionCode>.apk`.
7. Upload renamed APK as GitHub Actions artifact.

## Trigger and Data Flow

### Trigger Conditions

- `push` on branch `main` with path filter `android/**`
- `workflow_dispatch` for manual execution

### Data Flow

1. Workflow starts on qualifying event.
2. Build step produces `android/app/build/outputs/apk/release/app-release.apk` (expected default output path).
3. Parse `versionCode` in `defaultConfig` from `android/app/build.gradle.kts`.
4. Rename APK to `app-${versionCode}.apk`.
5. Upload artifact with same name.

Naming rule is dynamic and preserves the current `app-versioncode.apk` pattern intent by replacing `versioncode` with actual numeric `versionCode`.

## Error Handling

- If `versionCode` cannot be parsed, fail workflow with clear message.
- If release APK file is not found, fail workflow and print expected path.
- If Gradle build fails, surface full build logs from CI output.

## Testing and Verification

### Local Verification

- Run `./gradlew :app:assembleRelease` from `android/` to validate baseline build.

### CI Verification

- Commit changing a file under `android/**` and push to `main`; verify workflow auto-runs.
- Confirm artifact is uploaded and name is `app-<versionCode>.apk`.
- Commit changing only non-`android/**` files; verify workflow does not auto-run.

## Security and Operational Notes

- Repository visibility is private.
- Workflow uses standard GitHub-hosted runner and no custom secrets are required for artifact upload.
- If project later requires signed release builds, keystore secrets can be added separately.

## Acceptance Criteria

- GitHub repo `tianrui-app` exists and is private.
- `ios/` directory exists in repository history.
- `.github/workflows/android-apk.yml` exists and passes syntax checks.
- Pushes that modify `android/**` trigger CI and produce artifact `app-<versionCode>.apk`.
- Pushes with no `android/**` changes do not trigger this workflow.
