const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const html = fs.readFileSync(
  path.join(__dirname, "..", "com", "example", "demo", "view", "templates", "feedbackGraph.html"),
  "utf8"
);
const expression = html.match(/const hasGroupedAlerts = (.+);/)?.[1];
assert.ok(expression, "grouped-alert legend condition is present");

const isVisible = renderedCircleData => {
  const context = { renderedCircleData };
  vm.runInNewContext(`result = ${expression};`, context);
  return context.result;
};

assert.equal(isVisible([{ count: 1 }, { count: 1 }]), false);
assert.equal(isVisible([{ count: 1 }, { count: 2 }]), true);
assert.match(html, /if \(hasGroupedAlerts\)[\s\S]*?\.text\("Grouped alerts"\);/);
