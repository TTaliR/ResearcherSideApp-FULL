import com.example.demo.model.SensorRuleConfig;
import com.example.demo.util.HapticPreviewCalculator;

import java.math.BigDecimal;

public final class HapticPreviewCalculatorSelfCheck {
    public static void main(String[] args) {
        SensorRuleConfig rule = new SensorRuleConfig();
        rule.setMinvalue(new BigDecimal("0"));
        rule.setMaxvalue(new BigDecimal("100"));
        rule.setMinpulses(2);
        rule.setMaxpulses(6);
        rule.setMinintensity(20);
        rule.setMaxintensity(220);
        rule.setMinduration(100);
        rule.setMaxduration(300);
        rule.setMininterval(40);
        rule.setMaxinterval(80);

        HapticPreviewCalculator.Result midpoint = HapticPreviewCalculator.calculate(new BigDecimal("50"), rule, false);
        require(midpoint.pulses() == 4, "midpoint pulses changed");
        require(midpoint.intensity() == 120, "midpoint intensity changed");
        require(midpoint.duration() == 200, "midpoint duration changed");
        require(midpoint.interval() == 60, "midpoint interval changed");
        require(midpoint.totalDurationMs() == 980, "final silence was included in total length");

        HapticPreviewCalculator.Result heartRate = HapticPreviewCalculator.calculate(new BigDecimal("100"), rule, true);
        require(heartRate.pulses() == 10, "HeartRate pulse contract changed");
        require(heartRate.interval() == 300, "HeartRate interval contract changed");
        require(heartRate.totalDurationMs() == 5700, "HeartRate total length changed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
