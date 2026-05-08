"""
train_and_export.py — CogniShield neural friction classifier

Trains a lightweight LSTM → Dense model on synthetic biometric data,
then converts it to a TFLite flatbuffer optimised for Samsung NPU (NNAPI).

Model spec (must match CogniClassifier.kt constants exactly):
  Input:  float32[1, 20, 5]   — 20 BioFrames × 5 features
  Output: float32[1, 3]       — softmax(FLOW, RECOVERY, REDLINING)

Usage:
  python scripts/train_and_export.py

Output:
  app/src/main/assets/cogni_classifier.tflite

Requirements (install with pip):
  pip install tensorflow==2.16.1 numpy scikit-learn matplotlib
"""

import os
import numpy as np
import tensorflow as tf
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report

# ─────────────────────────────────────────────────────────────────────────────
# Configuration — must stay in sync with CogniClassifier.kt
# ─────────────────────────────────────────────────────────────────────────────
WINDOW_SIZE = 20  # frames per inference window
NUM_FEATURES = 5  # [eda, 1-hrv, ppg, imuCadence, gazeScore]
NUM_CLASSES = 3  # 0=FLOW, 1=RECOVERY, 2=REDLINING
EPOCHS = 40
BATCH_SIZE = 64
LEARNING_RATE = 1e-3
OUTPUT_DIR = "app/src/main/assets"
MODEL_NAME = "cogni_classifier.tflite"

# Class label mapping (must match CogniClassifier.kt index order)
LABELS = {0: "FLOW", 1: "RECOVERY", 2: "REDLINING"}

# ─────────────────────────────────────────────────────────────────────────────
# Step 1 — Synthetic dataset generation
#
# Replace this section with real data from Galaxy Watch recordings once
# hardware is available. The synthetic data captures known physiological
# signatures for each cognitive state.
#
# Feature layout per frame: [eda, 1-hrv, ppg, imuCadence, gazeScore]
# All features normalised to [0, 1].
# ─────────────────────────────────────────────────────────────────────────────


def generate_synthetic_dataset(n_samples_per_class: int = 2000):
    """
    Generates a labelled dataset of sliding windows with realistic
    physiological noise added to each class's mean signature.

    FLOW signature:       low EDA, high HRV (so low 1-hrv), moderate PPG, calm IMU, calm gaze
    RECOVERY signature:   moderate all signals — transitional state
    REDLINING signature:  high EDA, low HRV (high 1-hrv), erratic PPG, high IMU, high gaze
    """
    rng = np.random.default_rng(seed=42)

    # Mean signal per class: [eda, 1-hrv, ppg, imuCadence, gazeScore]
    class_means = {
        0: [0.15, 0.20, 0.50, 0.10, 0.10],  # FLOW      — calm
        1: [0.45, 0.50, 0.50, 0.35, 0.40],  # RECOVERY  — mid
        2: [0.80, 0.80, 0.65, 0.70, 0.75],  # REDLINING — overloaded
    }
    class_stds = {
        0: [0.08, 0.10, 0.12, 0.06, 0.07],
        1: [0.10, 0.12, 0.12, 0.10, 0.10],
        2: [0.08, 0.08, 0.12, 0.10, 0.08],
    }

    X_all, y_all = [], []

    for label, mean in class_means.items():
        std = class_stds[label]
        for _ in range(n_samples_per_class):
            # Generate a WINDOW_SIZE × NUM_FEATURES window with temporal continuity
            # Each feature evolves as a random walk around the class mean
            window = np.zeros((WINDOW_SIZE, NUM_FEATURES))
            current = np.array(mean, dtype=np.float32)

            for t in range(WINDOW_SIZE):
                noise = rng.normal(0, std, NUM_FEATURES).astype(np.float32)
                current = np.clip(current + noise * 0.3, 0.0, 1.0)
                window[t] = current

            X_all.append(window)
            y_all.append(label)

    X = np.array(X_all, dtype=np.float32)  # shape: [N, 20, 5]
    y = np.array(y_all, dtype=np.int32)

    print(f"Dataset: {X.shape[0]} windows — {n_samples_per_class} per class")
    return X, y


# ─────────────────────────────────────────────────────────────────────────────
# Step 2 — Model architecture
#
# Design constraints for Samsung NPU (NNAPI):
#   - Prefer Conv1D or Dense layers over LSTM for NPU compatibility
#   - NNAPI supports LSTM but with variable operator support per SoC
#   - We use Conv1D + GlobalAvgPool as the primary path for NPU speed,
#     with an LSTM option commented out for reference
# ─────────────────────────────────────────────────────────────────────────────


def build_model():
    """
    Lightweight Conv1D classifier optimised for Samsung NPU via NNAPI.

    Architecture:
      Input(20, 5)
        → Conv1D(32, kernel=3, relu)     — local temporal patterns
        → Conv1D(64, kernel=3, relu)     — higher-order patterns
        → GlobalAveragePooling1D         — NPU-friendly alternative to LSTM
        → Dense(32, relu)
        → Dropout(0.3)
        → Dense(3, softmax)              — FLOW / RECOVERY / REDLINING

    Parameter count: ~7,500 — tiny enough for <5ms NPU inference.
    """
    inputs = tf.keras.Input(shape=(WINDOW_SIZE, NUM_FEATURES), name="bio_window")

    x = tf.keras.layers.Conv1D(
        32, kernel_size=3, padding="causal", activation="relu", name="conv1"
    )(inputs)
    x = tf.keras.layers.Conv1D(
        64, kernel_size=3, padding="causal", activation="relu", name="conv2"
    )(x)
    x = tf.keras.layers.GlobalAveragePooling1D(name="pool")(x)
    x = tf.keras.layers.Dense(32, activation="relu", name="dense1")(x)
    x = tf.keras.layers.Dropout(0.3, name="dropout")(x)
    outputs = tf.keras.layers.Dense(
        NUM_CLASSES, activation="softmax", name="classifier"
    )(x)

    model = tf.keras.Model(inputs, outputs, name="CogniClassifier")
    return model


# ─────────────────────────────────────────────────────────────────────────────
# Step 3 — Training
# ─────────────────────────────────────────────────────────────────────────────


def train(model, X_train, y_train, X_val, y_val):
    model.compile(
        optimizer=tf.keras.optimizers.Adam(LEARNING_RATE),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )

    callbacks = [
        tf.keras.callbacks.EarlyStopping(
            monitor="val_loss", patience=5, restore_best_weights=True
        ),
        tf.keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss", factor=0.5, patience=3, min_lr=1e-5
        ),
    ]

    history = model.fit(
        X_train,
        y_train,
        validation_data=(X_val, y_val),
        epochs=EPOCHS,
        batch_size=BATCH_SIZE,
        callbacks=callbacks,
        verbose=1,
    )
    return history


# ─────────────────────────────────────────────────────────────────────────────
# Step 4 — TFLite conversion with NNAPI/NPU optimisation
# ─────────────────────────────────────────────────────────────────────────────


def convert_to_tflite(model, X_train):
    import tempfile

    # ── Keras 3 / TF 2.16 fix ────────────────────────────────────────────────
    # TFLiteConverter.from_keras_model() crashes with Keras 3 because the
    # converter cannot parse the new graph format.  The reliable workaround is:
    #   1. Export to a TF SavedModel on disk (uses the TF-native format)
    #   2. Load the SavedModel back via from_saved_model()
    # This bypasses the Keras 3 graph entirely and avoids the LLVM error.
    # ─────────────────────────────────────────────────────────────────────────
    with tempfile.TemporaryDirectory() as tmp_dir:
        # Step A: save as TF SavedModel (not Keras .keras format)
        model.export(tmp_dir)  # tf.keras.Model.export() → SavedModel

        # Step B: convert from the SavedModel path
        converter = tf.lite.TFLiteConverter.from_saved_model(tmp_dir)
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]

        tflite_model = converter.convert()

    return tflite_model


# ─────────────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────────────


def main():
    print("=" * 60)
    print("CogniShield — Neural Friction Classifier Training")
    print("=" * 60)
    print(f"TensorFlow version: {tf.__version__}")
    print(f"Input shape: [batch, {WINDOW_SIZE}, {NUM_FEATURES}]")
    print(f"Output shape: [batch, {NUM_CLASSES}]  ({list(LABELS.values())})")
    print()

    # 1. Dataset
    print("Step 1/4 — Generating synthetic dataset...")
    X, y = generate_synthetic_dataset(n_samples_per_class=2000)
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.15, random_state=42, stratify=y
    )
    X_train, X_val, y_train, y_val = train_test_split(
        X_train, y_train, test_size=0.15, random_state=42, stratify=y_train
    )
    print(
        f"  Train: {X_train.shape[0]}  Val: {X_val.shape[0]}  Test: {X_test.shape[0]}"
    )

    # 2. Build
    print("\nStep 2/4 — Building model...")
    model = build_model()
    model.summary()

    # 3. Train
    print("\nStep 3/4 — Training...")
    train(model, X_train, y_train, X_val, y_val)

    # Evaluate on held-out test set
    print("\n--- Test set evaluation ---")
    y_pred = np.argmax(model.predict(X_test, verbose=0), axis=1)
    print(classification_report(y_test, y_pred, target_names=list(LABELS.values())))

    # 4. Convert and save
    print("Step 4/4 — Converting to TFLite...")
    tflite_model = convert_to_tflite(model, X_train)

    os.makedirs(OUTPUT_DIR, exist_ok=True)
    output_path = os.path.join(OUTPUT_DIR, MODEL_NAME)
    with open(output_path, "wb") as f:
        f.write(tflite_model)

    size_kb = len(tflite_model) / 1024
    print(f"\n✅ Model saved to: {output_path}")
    print(f"   Size: {size_kb:.1f} KB")
    print(f"   Input:  float32[1, {WINDOW_SIZE}, {NUM_FEATURES}]")
    print(f"   Output: float32[1, {NUM_CLASSES}]  ({list(LABELS.values())})")
    print()
    print("Next step: open Android Studio and run the app on a Galaxy device.")


if __name__ == "__main__":
    main()
