const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..");
const read = (...parts) => fs.readFileSync(path.join(root, ...parts), "utf8");
const fxml = read("com", "example", "demo", "view", "MappingsTab.fxml");
const controller = read("com", "example", "demo", "controller", "MappingsTabController.java");

assert.match(fxml, /<TitledPane fx:id="mappingPreviewPane"[^>]*expanded="false"/);
assert.match(fxml, /fx:id="mappingPreviewInputSlider"/);
assert.match(fxml, /fx:id="mappingPreviewWaveform"/);
assert.match(fxml, /Pulse width represents Duration, spacing represents Interval, and height\/opacity represents Intensity/);
assert.match(fxml, /fx:id="mappingPreviewDurationLabel" text="Duration: -"/);
assert.match(fxml, /fx:id="mappingPreviewIntervalLabel" text="Interval: -"/);
assert.doesNotMatch(fxml, /Gap:|mappingPreviewBadge|mappingPreviewRangeLabel/);
assert.match(controller, /field\.textProperty\(\)\.addListener[\s\S]*?updateHapticPreview\(false\)/);
assert.match(controller, /mappingPreviewInputSlider\.valueProperty\(\)\.addListener/);
assert.match(controller, /HapticPreviewCalculator\.calculate/);
assert.match(controller, /updateHapticPreview\(true\)/);
assert.match(controller, /setManaged\(result\.heartRate\(\)\)/);
assert.match(controller, /Math\.min\(result\.pulses\(\), 16\)/);
assert.match(controller, /truncated \? 150 : 24/);
assert.match(controller, /String\.format\(Locale\.ROOT, "%,d", result\.pulses\(\)\)/);
assert.match(controller, /3_600_000/);
