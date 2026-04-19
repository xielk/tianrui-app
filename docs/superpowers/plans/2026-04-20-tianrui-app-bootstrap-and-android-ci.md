# Tianrui App Bootstrap and Android CI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create private GitHub repo `tianrui-app`, add tracked `ios/` directory, and enable GitHub Actions to auto-build and upload `app-<versionCode>.apk` on `android/**` changes.

**Architecture:** Keep Android project layout unchanged, add one CI workflow at repository root, and use shell parsing in workflow to read `versionCode` from `android/app/build.gradle.kts`. CI builds `:app:assembleRelease`, renames output APK to include version code, and uploads artifact.

**Tech Stack:** Git, GitHub CLI (`gh`), GitHub Actions, Gradle (Android), Bash

---

## File Structure

- Create: `.github/workflows/android-apk.yml` (Android CI workflow)
- Create: `ios/.gitkeep` (track `ios/` directory)
- Create: `.gitignore` (root ignore rules for macOS/system files)
- Modify: `android/app/build.gradle.kts` (no code changes expected; used as CI metadata source for `versionCode`)
- Docs reference: `docs/superpowers/specs/2026-04-20-tianrui-app-repo-ios-android-action-design.md`

### Task 1: Prepare Repository Structure and Ignore Rules

**Files:**
- Create: `.gitignore`
- Create: `ios/.gitkeep`

- [ ] **Step 1: Write root `.gitignore`**

```gitignore
.DS_Store
Thumbs.db
*.swp
*.swo
```

- [ ] **Step 2: Add tracked iOS directory marker**

```text
# file: ios/.gitkeep
```

- [ ] **Step 3: Verify new files exist**

Run: `ls -la && ls -la ios`
Expected: root shows `.gitignore`; `ios/` exists and contains `.gitkeep`

- [ ] **Step 4: Stage and commit structure changes**

```bash
git add .gitignore ios/.gitkeep
git commit -m "chore: add ios directory scaffold and root ignores"
```

### Task 2: Add Android APK GitHub Actions Workflow

**Files:**
- Create: `.github/workflows/android-apk.yml`

- [ ] **Step 1: Write failing validation command first (workflow file not present yet)**

Run: `test -f .github/workflows/android-apk.yml`
Expected: non-zero exit code (file missing)

- [ ] **Step 2: Add workflow implementation**

```yaml
name: Android APK Build

on:
  push:
    branches: ["main"]
    paths:
      - "android/**"
  workflow_dispatch:

jobs:
  build-apk:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Make Gradle wrapper executable
        run: chmod +x gradlew
        working-directory: android

      - name: Build release APK
        run: ./gradlew :app:assembleRelease
        working-directory: android

      - name: Resolve versionCode
        id: version
        run: |
          VERSION_CODE=$(grep -E '^[[:space:]]*versionCode[[:space:]]*=' android/app/build.gradle.kts | head -n 1 | sed -E 's/.*=[[:space:]]*([0-9]+).*/\1/')
          if [ -z "$VERSION_CODE" ]; then
            echo "Failed to parse versionCode from android/app/build.gradle.kts"
            exit 1
          fi
          echo "value=$VERSION_CODE" >> "$GITHUB_OUTPUT"

      - name: Rename APK with versionCode
        run: |
          SRC="android/app/build/outputs/apk/release/app-release.apk"
          DST="android/app/build/outputs/apk/release/app-${{ steps.version.outputs.value }}.apk"
          if [ ! -f "$SRC" ]; then
            echo "APK not found at $SRC"
            exit 1
          fi
          mv "$SRC" "$DST"

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: app-${{ steps.version.outputs.value }}.apk
          path: android/app/build/outputs/apk/release/app-${{ steps.version.outputs.value }}.apk
```

- [ ] **Step 3: Validate workflow file presence and key triggers**

Run: `test -f .github/workflows/android-apk.yml && grep -n "android/\*\*" .github/workflows/android-apk.yml`
Expected: exit code 0 and a line containing `android/**`

- [ ] **Step 4: Stage and commit workflow**

```bash
git add .github/workflows/android-apk.yml
git commit -m "ci: build and upload versioned apk on android changes"
```

### Task 3: Initialize/Configure GitHub Remote and Push

**Files:**
- Modify: `.git/config` (via git commands)

- [ ] **Step 1: Verify local git status before remote operations**

Run: `git status --short`
Expected: clean working tree

- [ ] **Step 2: Create private GitHub repository and set origin**

```bash
gh repo create tianrui-app --private --source . --remote origin
```

- [ ] **Step 3: Push main branch to GitHub**

Run: `git push -u origin main`
Expected: branch `main` tracks `origin/main` and push succeeds

- [ ] **Step 4: Verify repository and remote wiring**

Run: `gh repo view --web=false && git remote -v`
Expected: repository `tianrui-app` exists and `origin` points to GitHub URL

### Task 4: Verify Build and CI Trigger Behavior

**Files:**
- Modify: `android/**` (temporary verification touch commit)
- Modify: non-android file (temporary verification touch commit)

- [ ] **Step 1: Run local release build baseline**

Run: `./gradlew :app:assembleRelease`
Working directory: `android`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Trigger CI with android-path commit**

```bash
touch android/.ci-trigger && git add android/.ci-trigger && git commit -m "chore: trigger android apk workflow" && git push
```

Expected: GitHub Action `Android APK Build` starts automatically

- [ ] **Step 3: Verify artifact naming in Actions run**

Run: `gh run list --limit 5`
Expected: latest run for `Android APK Build` is successful

Run: `gh run view --log`
Expected: upload step references `app-<versionCode>.apk` artifact name

- [ ] **Step 4: Verify non-android change does not auto-trigger workflow**

```bash
touch README.md && git add README.md && git commit -m "docs: trigger non-android push" && git push
```

Expected: no new `Android APK Build` run for this push

- [ ] **Step 5: Commit cleanup decision**

```bash
git add -A
git commit -m "chore: keep or clean verification marker files"
```

Expected: repository ends in intentional state (either keep verification files or remove them explicitly)
