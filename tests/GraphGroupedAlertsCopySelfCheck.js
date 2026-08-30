const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const graph = fs.readFileSync(
  path.join(__dirname, "..", "com", "example", "demo", "view", "templates", "feedbackGraph.html"),
  "utf8"
);

assert.match(graph, /\$\{d\.count\} grouped alerts/);
assert.match(graph, /show grouped alerts/);
assert.match(graph, /\$\{d\.points\.length\} grouped alerts/);
assert.doesNotMatch(graph, /aggregated alerts|aggregated dots/);
