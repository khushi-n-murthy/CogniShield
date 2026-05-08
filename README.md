# CogniShield

CogniShield is an edge-first, AI-driven cognitive protection system for Android. It uses multimodal sensor fusion to detect your mental state in real-time and automatically initiates digital interventions to prevent burnout, protect deep flow, and facilitate recovery.

## Features
Cognitive State Detection
Real-time AI Inference: Uses a custom TFLite model running on the device NPU to generate stress scores (0–1).
Three-State Classification:
- **FLOW:** Optimal performance state.
- **REDLINING:** High cognitive load/stress detected.
- **RECOVERY:** Period of rest or required downtime.

**Low Latency:** Sub-15ms inference speeds with NPU to GPU to CPU fallback logic.

## Multimodal Sensor Fusion
- **Biometrics:** Streams EDA (Electrodermal Activity), HRV, and PPG data from Galaxy Watch (Wear OS) at 250ms cadence.
- **Vision:** Tracks pupil dilation and gaze overwhelm via the front camera using MediaPipe Face Landmarker.
- **Behavioral:** Analyzes IMU (accelerometer) data to detect stress-fidgeting and erratic typing rhythms.

## Shield Mode (Activates on REDLINING)
- **Grayscale Overlay:** Automatically desaturates the screen to remove dopamine-triggering colors.
- **Scroll Dampening:** Injects accessibility gestures to slow scroll velocity in social feeds by 60%.
- **Aura Border:** A soft cyan glow ring appears around the screen edges indicating the shield is active.
- **Neural Voicemail:** Intercepts and suppresses non-critical Slack/Teams notifications, batching them for later.

## Recovery Mode

- **Guided Breathing:** An animated 4-7-8 breathing UI launches automatically when a recovery state is detected.
- **Haptic Nudge:** Gentle vibrational pulses signal the transition to recovery.

## Installation and Setup

**Prerequisites**
- **Android Device:** Android 13 (API 33) or higher.
- **Hardware:** Works best on devices with dedicated NPUs (Snapdragon 8 Gen 2+, Exynos 2200+).


## Critical Permissions

Because CogniShield performs system-level interventions, it requires two sensitive permissions that are often hidden for side-loaded apps:
1. Display over other apps (for the Aura Border and Grayscale).
2. Notification Access (for Neural Voicemail).
    [!IMPORTANT]If the app is missing from permission lists:
    - Long-press the CogniShield icon > Tap App Info (i).
    - Tap the three dots (vertical ellipsis) in the top right corner.
    - Select "Allow restricted settings".
    - Go back to the app and grant the permissions.

## Technical Architecture
CogniShield is built with a strictly Edge-First philosophy.
- **AI:** TensorFlow Lite with NNAPI delegation.
- **UI:** Jetpack Compose with real-time StateFlow collection.
- **Persistence:** Room Database for local-only history (nothing is sent to the cloud).
- **Services:** Background AccessibilityService and Notification ListenerService for system-wide control.

## AI Disclosure

- **Automated Decision-Making:** This app uses probabilistic machine learning models to trigger system changes.
- **Non-Medical:** CogniShield is a productivity tool, not a medical diagnostic device.
- **Data Privacy:** All biometric and camera processing happens locally. No raw sensor data ever leaves the device.

## License
Copyright 2026. All rights reserved.