const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..");
const dashboard = fs.readFileSync(path.join(root, "com", "example", "demo", "view", "Dashboard.fxml"), "utf8");
const personalization = fs.readFileSync(path.join(root, "com", "example", "demo", "view", "SchedulesTab.fxml"), "utf8");

assert.match(dashboard, /fx:id="schedulingTab" text="Personalization"/);
assert.match(personalization, /text="Participant Personalization"/);
assert.match(personalization, /Periodically checks the selected feedback measure/);
assert.match(personalization, /configured percentage/);
