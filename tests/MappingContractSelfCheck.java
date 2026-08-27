import com.example.demo.model.SensorRuleConfig;
import com.example.demo.util.FormatUtils;

import java.math.BigDecimal;

public final class MappingContractSelfCheck {
    public static void main(String[] args) {
        SensorRuleConfig rule = new SensorRuleConfig();
        rule.setMinvalue(new BigDecimal("-10.5"));
        rule.setMaxvalue(new BigDecimal("100.25"));
        rule.setMinintensity(1);
        rule.setMaxintensity(255);

        require(rule.getMinvalue().compareTo(new BigDecimal("-10.5")) == 0, "negative decimal minimum changed");
        require(rule.getMaxvalue().compareTo(new BigDecimal("100.25")) == 0, "decimal maximum changed");
        require("(-10.5)".equals(FormatUtils.formatMappingValue(rule.getMinvalue())), "negative display is ambiguous");
        require("100.25".equals(FormatUtils.formatMappingValue(rule.getMaxvalue())), "positive display changed");
        require("U V".equals(FormatUtils.formatUseCaseName("UV")), "UV display name changed");
        require("Heart Rate".equals(FormatUtils.formatUseCaseName("HeartRate")), "HeartRate display name changed");
        expectInvalidIntensity(() -> rule.setMinintensity(0));
        expectInvalidIntensity(() -> rule.setMaxintensity(256));
    }

    private static void expectInvalidIntensity(Runnable action) {
        try {
            action.run();
            throw new AssertionError("invalid intensity was accepted");
        } catch (IllegalArgumentException expected) {
            // Expected contract rejection.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
