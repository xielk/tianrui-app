# Zongshen Splash Screen Logo-Only Animation Design

## Goal

Design a launch animation for the Android app (`宗申智行`) using only the brand logo (no scooter scene, no Canva dependency), with a novel but controlled effect that feels premium and startup-safe.

Approved direction:

1. Logo enters from left.
2. Logo makes a small rebound.
3. Logo performs electric-outline glow reveal.

## Scope

In scope:

- One logo-only splash animation concept for vertical mobile startup screen.
- Motion timing, easing, keyframe, and glow parameter definition.
- Engineering-friendly spec for native implementation (Compose/Lottie/video fallback).

Out of scope:

- Additional scene elements (vehicle, city background, particles).
- Brand redesign or logo shape modification.
- Multi-theme variants.

## Existing Product Context

- App name is `宗申智行`.
- Product tone is smart mobility, BLE control, and safety trust.
- Startup animation should feel technical and reliable, not flashy.

## Visual Direction

- Keep background simple and dark to maximize logo readability.
- Use cyan electric highlights for intelligence and connection feeling.
- Keep center-weighted composition for clean handoff to home screen.

Recommended colors:

- Background: `#071426` to `#0B2A46` subtle gradient.
- Electric edge light: `#7FE9FF`.
- Secondary glow: `#37C8FF`.

## Motion Architecture

Total duration: `1.8s`

### Phase 1: Logo Slide-In (`0.00s - 0.70s`)

- Position: `X -115% -> X 50%`.
- Easing: `ease-out-cubic`.
- Opacity: fixed `100%`.

### Phase 2: Micro Rebound (`0.70s - 0.95s`)

- Position rebound: `50% -> 47% -> 49%`.
- Scale rebound: `1.00 -> 1.02 -> 1.00`.
- Keep movement subtle to avoid cartoon style.

### Phase 3: Electric Outline + Glow (`0.95s - 1.55s`)

- Outline trace starts around the logo contour.
- Solid fill appears after 120ms delay from trace start.
- Glow pulse:
  - Strength `70% -> 100% -> 68%`
  - Radius `16 -> 24 -> 20`

### Phase 4: Hold (`1.55s - 1.80s`)

- Hold stable final frame for 250ms before navigating to the main screen.

## Layer Model

1. Background layer.
2. Logo base (solid).
3. Logo outline (stroke reveal).
4. Logo glow (blurred duplicate).

This keeps implementation simple and deterministic.

## Implementation Mapping

Primary recommendation:

- Native animation in Android Compose with `Animatable` and keyframes.

Optional alternatives:

- Lottie JSON if design team exports vector timeline.
- Pre-rendered MP4/WebM only as fallback when startup constraints allow.

## Quality Gates

- Animation reads clearly in first 800ms.
- No frame drops on mid-tier Android devices.
- Logo edge stays sharp under glow.
- Startup total animation time remains `1.6s - 2.0s`.

## Acceptance Criteria

- Only logo is animated (no extra scene assets).
- Slide-in, rebound, and glow sequence is visible and ordered.
- Final frame remains stable and legible before transition.
- Spec is directly implementable by Android engineering.
