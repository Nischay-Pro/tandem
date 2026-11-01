export default {
  plugins: [
    "@stryker-mutator/vitest-runner",
    "@stryker-mutator/typescript-checker",
  ],
  mutate: ["src/actual/actual-session.ts"],
  testRunner: "vitest",
  vitest: {
    configFile: "vitest.config.ts",
    related: true,
  },
  checkers: ["typescript"],
  tsconfigFile: "tsconfig.json",
  coverageAnalysis: "perTest",
  reporters: ["clear-text", "html"],
  htmlReporter: {
    fileName: "../../.dev-tmp/stryker/actual-session.html",
  },
  tempDirName: "../../.dev-tmp/stryker/temp",
  thresholds: {
    high: 95,
    low: 90,
    break: 90,
  },
};
