package com.heysig;

import org.junit.Test;

public class SharedRegressionTest {
    @Test public void noiseThresholdLearning() {
        com.heysig.noise.NoiseThresholdLearningCheckKt.main();
    }
    @Test public void environmentSwitching() {
        com.heysig.noise.EnvironmentSwitchingCheckKt.main();
    }
    @Test public void environmentRecordings() {
        com.heysig.noise.EnvironmentRecordingCheckKt.main();
    }
    @Test public void environmentQuietVerification() {
        com.heysig.noise.EnvironmentSignalCheckKt.main();
    }
    @Test public void environmentSceneHead() {
        com.heysig.noise.EnvironmentSceneHeadCheckKt.main();
    }
    @Test public void sleepRoutine() {
        com.heysig.sleep.SleepRoutineCheckKt.main();
    }
    @Test public void companionSafety() {
        com.heysig.companion.CompanionSafetyCheckKt.main();
    }
    @Test public void manualControlsAndVoice() {
        com.heysig.control.ManualControlCheckKt.main();
    }
    @Test public void relativeNoiseMeasurement() {
        com.heysig.noise.NoiseMeasurementCheckKt.main();
    }
    @Test public void noiseAutomation() {
        com.heysig.noise.NoiseAutomationCheckKt.main();
    }
    @Test public void sixLevelObservation() {
        com.heysig.noise.NoiseObserveCheckKt.main();
    }
}
