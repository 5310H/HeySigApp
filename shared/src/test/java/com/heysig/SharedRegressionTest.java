package com.heysig;

import org.junit.Test;

public class SharedRegressionTest {
    @Test public void manualControlsAndVoice() {
        com.heysig.control.ManualControlCheckKt.main();
    }
    @Test public void relativeNoiseMeasurement() {
        com.heysig.noise.NoiseMeasurementCheckKt.main();
    }
    @Test public void sixLevelObservation() {
        com.heysig.noise.NoiseObserveCheckKt.main();
    }
}
