const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const html = fs.readFileSync(
  path.join(__dirname, "..", "com", "example", "demo", "view", "templates", "miniFeedbackGraph.html"),
  "utf8"
);
const helper = html.match(/function insertGaps\(points\) \{[\s\S]*?\n  \}/)?.[0];
assert.ok(helper, "insertGaps helper is present");

const point = minutes => ({ parsedTime: new Date(minutes * 60_000), value: minutes });
const run = points => {
  const context = { points };
  vm.runInNewContext(`${helper}; result = insertGaps(points);`, context);
  return context.result;
};

assert.equal(run([point(0), point(1), point(2)]).length, 3);
const withGap = run([point(0), point(1), point(2), point(10)]);
assert.equal(withGap.length, 5);
assert.equal(withGap[3].value, null);
