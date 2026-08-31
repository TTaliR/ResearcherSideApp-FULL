const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..");
const read = (...parts) => fs.readFileSync(path.join(root, ...parts), "utf8");
const model = read("com", "example", "demo", "model", "RuleCardData.java");
const controller = read("com", "example", "demo", "controller", "MappingsTabController.java");
const factory = read("com", "example", "demo", "factory", "MappingUiFactory.java");
const css = read("com", "example", "demo", "view", "dashboard.css");

assert.match(model, /boolean hasInvalidIntensity\(\)/);
assert.match(model, /minIntensity < 1 \|\| minIntensity > 255/);
assert.match(model, /maxIntensity < 1 \|\| maxIntensity > 255/);
assert.doesNotMatch(model, /minIntensity > maxIntensity/);
assert.match(controller, /Intensity values must be between 1 and 255\./);
assert.match(controller, /rule\.hasInvalidIntensity\(\)/);
assert.match(factory, /rule\.hasInvalidIntensity\(\)/);
assert.match(css, /\.mapping-card-invalid/);
assert.match(css, /\.mapping-invalid-value/);
