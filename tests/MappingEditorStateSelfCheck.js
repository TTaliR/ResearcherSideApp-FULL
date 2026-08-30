const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..");
const controller = fs.readFileSync(
  path.join(root, "com", "example", "demo", "controller", "MappingsTabController.java"),
  "utf8"
);
const fxml = fs.readFileSync(
  path.join(root, "com", "example", "demo", "view", "MappingsTab.fxml"),
  "utf8"
);

assert.match(fxml, /<ScrollPane fx:id="mappingsScrollPane"/);
assert.match(controller, /refreshMappingsPreservingEditor\(null, ruleConfig, scrollPosition\)/);
assert.match(controller, /refreshMappingsPreservingEditor\(editingRule, ruleConfig, scrollPosition\)/);
assert.match(controller, /\.filter\(rule -> rule\.mappingId == editingRule\.mappingId\)/);
assert.match(controller, /selectedUser == null \|\| isUserAssignedToRule\(selectedUser, rule\)/);
assert.match(controller, /mappingsScrollPane\.setVvalue\(scrollPosition\)/);

const saveFlow = controller.match(/private void saveRuleBuilderConfig\(\)[\s\S]*?private SensorRuleConfig buildRuleConfigFromForm/)?.[0];
const updateResult = controller.match(/private void handleMappingChangeResult[\s\S]*?private double getMappingsScrollPosition/)?.[0];
assert.ok(saveFlow && updateResult, "save and update flows are present");
assert.doesNotMatch(saveFlow, /clearRuleBuilderForm\(\)/);
assert.doesNotMatch(updateResult, /clearRuleBuilderForm\(\)/);
