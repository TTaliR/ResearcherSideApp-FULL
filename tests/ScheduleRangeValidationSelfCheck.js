const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..");
const read = (...parts) => fs.readFileSync(path.join(root, ...parts), "utf8");
const dashboard = read("com", "example", "demo", "view", "Dashboard.fxml");
const fxml = read("com", "example", "demo", "view", "SchedulesTab.fxml");
const controller = read("com", "example", "demo", "controller", "SchedulesTabController.java");
const api = read("com", "example", "demo", "service", "ApiService.java");

assert.match(dashboard, /fx:id="schedulingTab" text="Schedules"/);
assert.match(fxml, /text="How schedules work"/);
assert.match(fxml, /recalculates the participant's mapping input boundaries/);
assert.match(fxml, /measured result of 100 with a 10% range produces mapping boundaries of 90–110/);
assert.match(fxml, /text="Participant:"/);
assert.match(fxml, /text="Recalculate every"/);
assert.match(fxml, /text="Range around result \(%\):"/);
assert.doesNotMatch(fxml, /Personalization|negative %|Range ±/);
assert.match(controller, /IntegerSpinnerValueFactory\(1, Integer\.MAX_VALUE, 1, 1\)/);
assert.match(controller, /newText\.matches\("\\\\d\*"\)/);
assert.match(controller, /typedValue <= 0/);
assert.match(controller, /Invalid \(must be positive\)/);
assert.match(api, /triggerPercentage <= 0/);
