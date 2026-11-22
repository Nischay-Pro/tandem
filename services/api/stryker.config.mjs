export default {
  plugins: [
    "@stryker-mutator/vitest-runner",
    "@stryker-mutator/typescript-checker",
  ],
  mutate: ["src/store.ts", "src/actual-access.ts", "src/app.ts"],
  testRunner: "vitest",
  vitest: {
    configFile: "vitest.config.ts",
    related: true,
  },
  checkers: ["typescript"],
  tsconfigFile: "tsconfig.json",
  coverageAnalysis: "perTest",
  incremental: true,
  incrementalFile: "../../.dev-tmp/stryker/incremental.json",
  reporters: ["clear-text", "html"],
  htmlReporter: {
    fileName: "../../.dev-tmp/stryker/extension-sync.html",
  },
  tempDirName: "../../.dev-tmp/stryker/temp",
  thresholds: {
    high: 95,
    low: 90,
    break: 90,
  },
};
