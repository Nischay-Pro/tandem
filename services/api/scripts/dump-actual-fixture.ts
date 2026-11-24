// Downloads a budget straight from an Actual server and records what Tandem's reader returns for it.
import {
  create,
  fromBinary,
  SyncRequestSchema,
  SyncResponseSchema,
  toBinary,
} from "../../../node_modules/.pnpm/@actual-app+crdt@3.1.4/node_modules/@actual-app/crdt/dist/index.js";
import { ActualBudgetSnapshotReader } from "../src/actual/budget-snapshot-reader.js";
import * as Actual from "@actual-app/api";
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const url = process.env.ACTUAL_URL ?? "http://localhost:5006";
const password = process.env.ACTUAL_PASSWORD ?? "demo-password";
const budgetName = process.env.BUDGET_NAME ?? "Demo household";
const out = process.argv[2] ?? "fixture";
const dataDir = fs.mkdtempSync("/tmp/actual-fixture-");
fs.mkdirSync(out, { recursive: true });

const post = async (route: string, body: unknown, token?: string) => {
  const response = await fetch(`${url}${route}`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      ...(token ? { "x-actual-token": token } : {}),
    },
    body: JSON.stringify(body),
  });
  return response.json();
};
const login = await post("/account/login", {
  loginMethod: "password",
  password,
});
const token = login.data.token as string;
const files = await (
  await fetch(`${url}/sync/list-user-files`, {
    headers: { "x-actual-token": token },
  })
).json();
const file = files.data.find(
  (f: { name: string; deleted: number }) => f.name === budgetName && !f.deleted,
);
const zip = await fetch(`${url}/sync/download-user-file`, {
  headers: { "x-actual-token": token, "x-actual-file-id": file.fileId },
});
fs.writeFileSync(
  path.join(out, "budget.zip"),
  Buffer.from(await zip.arrayBuffer()),
);
fs.writeFileSync(
  path.join(out, "file.json"),
  JSON.stringify(file, null, 2) + "\n",
);
const sync = await fetch(`${url}/sync/sync`, {
  method: "POST",
  headers: {
    "content-type": "application/actual-sync",
    "x-actual-token": token,
  },
  body: toBinary(
    SyncRequestSchema,
    create(SyncRequestSchema, {
      fileId: file.fileId,
      groupId: file.groupId,
      since: "1970-01-01T00:00:00.000Z-0000-0000000000000000",
    }),
  ),
});
const syncBytes = new Uint8Array(await sync.arrayBuffer());
fs.writeFileSync(path.join(out, "sync-response.bin"), syncBytes);
console.log(
  `${fromBinary(SyncResponseSchema, syncBytes).messages.length} server messages`,
);

await Actual.init({ serverURL: url, password, dataDir });
await Actual.downloadBudget(file.groupId);
const reader = new ActualBudgetSnapshotReader({
  runBudget: (_id, operation) => operation(),
});
const result = await reader.readFullWithSettings(file.groupId);
const today = new Date().toISOString().slice(0, 10);
fs.writeFileSync(
  path.join(out, "snapshot.json"),
  JSON.stringify(
    { today, displaySettings: result.displaySettings, records: result.records },
    null,
    2,
  ) + "\n",
);
await Actual.shutdown();
console.log(`${result.records.length} records, file ${file.fileId}`);
