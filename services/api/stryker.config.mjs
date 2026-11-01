export default {
  plugins: [
    "@stryker-mutator/vitest-runner",
    "@stryker-mutator/typescript-checker",
  ],
  mutate: [
    "src/actual/actual-session.ts",
    "src/actual/transaction-reconciliation.ts",
    "src/actual/transaction-reconciler.ts",
    "src/actual/transaction-store.ts",
    "src/database/budget-lock.ts",
  ],
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
    fileName: "../../.dev-tmp/stryker/critical-finance-boundaries.html",
  },
  tempDirName: "../../.dev-tmp/stryker/temp",
  thresholds: {
    high: 95,
    low: 90,
    break: 90,
  },
};
