# CogniShield: AI Transparency & Disclosure

## 1. Purpose of AI
CogniShield uses on-device artificial intelligence to monitor and classify the user's cognitive and physiological state. The primary goal is to identify "Redlining" (high stress/cognitive overload) and automatically initiate digital interventions to protect the user's focus and mental well-being.

## 2. Automated Decision-Making
The application employs a Multimodal Fusion Engine that processes data from several sources to make real-time decisions:

- **Stress Classification:** A TensorFlow Lite (TFLite) model running on the device NPU (Neural Processing Unit) analyzes heart rate variability (HRV), PPG, and Electrodermal Activity (EDA) to assign a stress score (0.0 to 1.0).

- **State Detection:** Based on these scores, the AI categorizes the user into one of three states: FLOW, REDLINING, or RECOVERY.

- **Automated Interventions:** When the AI detects a REDLINING state, it independently triggers system-level actions, including:

    - Desaturating the screen (Grayscale mode).

    - Modifying UI scroll physics (Scroll dampening).

    - Suppressing incoming notifications (Neural Voicemail).

### 3. Data Processing & Source Disclosure

The AI processes the following high-frequency biometric and behavioral data:

- Biometrics: Heart rate, micro-sweat changes (EDA), and pulse data.

- Computer Vision: Pupil dilation and gaze patterns via the front-facing camera (MediaPipe).

- Kinematics: Jitter and fidgeting patterns via accelerometer/IMU analysis.

## 4. Privacy & Edge-First Architecture

- **On-Device Processing:** All AI inference and biometric processing occur strictly on the user's physical device. No raw biometric 

**data or camera feeds are transmitted to the cloud or external servers.**

- **Data Minimization:** Only high-level state classifications (e.g., "Stress Peak at 2:00 PM") are stored in a local encrypted database for the user's 7-day history chart.

## 5. Limitations & User Control

- **Non-Medical Device:** The AI classifications provided by CogniShield are for wellness and productivity purposes only. They do not constitute a medical diagnosis or clinical psychological assessment.

- **Intervention Override:** Users maintain ultimate control and can disable Shield Mode or override AI-driven system changes through the application dashboard at any time.

- **Probabilistic Nature:** As with all machine learning models, state detection is probabilistic. "Confidence Scores" are attached to every state emission to indicate the AI's certainty.

## 6. Transparency Statement

CogniShield is committed to the ethical use of AI. By prioritizing local NPU processing over cloud-based analysis, we ensure that the user's most sensitive physiological data remains under their exclusive control while benefiting from automated cognitive protection.