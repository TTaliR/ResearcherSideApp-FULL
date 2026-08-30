const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..");
const read = (...parts) => fs.readFileSync(path.join(root, ...parts), "utf8");
const fxml = read("com", "example", "demo", "view", "MappingsTab.fxml");
const controller = read("com", "example", "demo", "controller", "MappingsTabController.java");
const css = read("com", "example", "demo", "view", "dashboard.css");

assert.match(fxml, /fx:id="mappingEditorStatusLabel" text="All changes saved"/);
assert.match(controller, /field\.textProperty\(\)\.addListener/);
assert.match(controller, /setMappingEditorDirty\(true\)/);
assert.match(controller, /dirty \? "Unsaved changes" : "All changes saved"/);
assert.match(controller, /private List<TextField> ruleInputFields\(\)/);
assert.match(controller, /setMappingEditorDirty\(false\);\s+String savedUseCase/);
assert.match(controller, /setMappingEditorDirty\(false\);\s+refreshMappingsPreservingEditor/);
assert.match(css, /\.mapping-editor-status-saved/);
assert.match(css, /\.mapping-editor-status-unsaved/);
