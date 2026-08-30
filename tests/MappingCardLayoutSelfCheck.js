const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..");
const read = (...parts) => fs.readFileSync(path.join(root, ...parts), "utf8");
const fxml = read("com", "example", "demo", "view", "MappingsTab.fxml");
const controller = read("com", "example", "demo", "controller", "MappingsTabController.java");
const factory = read("com", "example", "demo", "factory", "MappingUiFactory.java");

assert.match(fxml, /fx:id="ruleSensorTypeDescriptionLabel"[^>]*wrapText="true"[^>]*maxWidth="240\.0"/);
assert.match(controller, /title\.setWrapText\(true\)/);
assert.match(factory, /title\.setWrapText\(true\)/);
assert.match(controller, /actionRow\.getStyleClass\(\)\.add\("mapping-card-actions"\)/);
assert.match(factory, /actionRow\.getStyleClass\(\)\.add\("mapping-card-actions"\)/);
assert.match(controller, /actionRow\.getChildren\(\)\.addAll\(assignButton, selectButton, activationButton\)/);
