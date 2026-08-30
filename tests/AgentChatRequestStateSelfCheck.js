const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..");
const controller = fs.readFileSync(
  path.join(root, "com", "example", "demo", "controller", "AgentChatTabController.java"),
  "utf8"
);
const fxml = fs.readFileSync(
  path.join(root, "com", "example", "demo", "view", "AgentChatTab.fxml"),
  "utf8"
);
const css = fs.readFileSync(
  path.join(root, "com", "example", "demo", "view", "dashboard.css"),
  "utf8"
);

assert.match(fxml, /fx:id="agentStatusBar"[\s\S]*?<ProgressIndicator/);
assert.match(css, /\.agent-status-bar\s*\{/);
assert.match(controller, /AGENT_ACTIVITY_MESSAGES = List\.of\([\s\S]*?Agent is thinking[\s\S]*?Agent is typing[\s\S]*?Agent is checking context/);
assert.match(controller, /if \(chatRequestInFlight\) \{\s*return;/);
assert.match(controller, /chatInputField\.setDisable\(inFlight \|\| !conversationOpen\)/);
assert.match(controller, /chatSendButton\.setDisable\(inFlight \|\| !conversationOpen\)/);
assert.match(controller, /promptSuggestionsPane\.setDisable\(inFlight\)/);
assert.match(controller, /new Button\("Retry"\)/);
assert.match(controller, /restoreFailedPrompt\(originalMessage\)/);
assert.match(controller, /statusCode == 408 \|\| statusCode == 504/);
assert.match(controller, /statusCode == 429/);
assert.match(controller, /statusCode == 401 \|\| statusCode == 403/);
assert.match(controller, /statusCode >= 500/);
