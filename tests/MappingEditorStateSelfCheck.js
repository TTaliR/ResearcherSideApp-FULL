const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..");
const controller = fs.readFileSync(
  path.join(root, "com", "example", "demo", "controller", "MappingsTabController.java"),
  "utf8"
);
const dashboard = fs.readFileSync(
  path.join(root, "com", "example", "demo", "controller", "DashboardController.java"),
  "utf8"
);
const fxml = fs.readFileSync(
  path.join(root, "com", "example", "demo", "view", "MappingsTab.fxml"),
  "utf8"
);

assert.match(fxml, /<ScrollPane fx:id="mappingsScrollPane"/);
assert.match(fxml, /fx:id="listAllMappingsToggle" text="List All"/);
assert.match(controller, /refreshMappingsAfterCreate\(ruleConfig, savedUseCase, scrollPosition\)/);
assert.match(controller, /refreshMappingsPreservingEditor\(editingRule, ruleConfig, scrollPosition\)/);
assert.match(controller, /\.filter\(rule -> rule\.mappingId == editingRule\.mappingId\)/);
assert.match(controller, /selectedUser == null \|\| isUserAssignedToRule\(selectedUser, rule\)/);
assert.match(controller, /mappingsScrollPane\.setVvalue\(scrollPosition\)/);
assert.match(controller, /new ButtonType\("Show Mapping"/);
assert.match(controller, /new ButtonType\("Stay Here"/);
assert.match(controller, /selectAllUsersCallback\.run\(\)/);
assert.match(controller, /scrollToMapping\(mappingToShow\.mappingId\)/);
assert.match(dashboard, /setSelectAllUsersCallback\(\(\) -> topBarController\.selectAllUsers\(\)\)/);

const saveFlow = controller.match(/private void saveRuleBuilderConfig\(\)[\s\S]*?private SensorRuleConfig buildRuleConfigFromForm/)?.[0];
const updateResult = controller.match(/private void handleMappingChangeResult[\s\S]*?private double getMappingsScrollPosition/)?.[0];
assert.ok(saveFlow && updateResult, "save and update flows are present");
assert.doesNotMatch(saveFlow, /clearRuleBuilderForm\(\)/);
assert.doesNotMatch(updateResult, /clearRuleBuilderForm\(\)/);
