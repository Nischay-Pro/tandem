// Writes byte-level fixtures from Actual's own CRDT package, used to check Tandem's Kotlin port.
import {
  create,
  EncryptedDataSchema,
  fromBinary,
  makeClock,
  merkle,
  MessageEnvelopeSchema,
  MessageSchema,
  setClock,
  SyncRequestSchema,
  SyncResponseSchema,
  Timestamp,
  toBinary,
} from "../../node_modules/.pnpm/@actual-app+crdt@3.1.4/node_modules/@actual-app/crdt/dist/index.js";
import { Buffer } from "node:buffer";
import console from "node:console";
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const out = process.argv[2];
const realNow = Date.now;
const at = (ms, fn) => {
  Date.now = () => ms;
  try {
    return fn();
  } finally {
    Date.now = realNow;
  }
};
const hex = (bytes) => Buffer.from(bytes).toString("hex");

const strings = [
  "1970-01-01T00:00:00.000Z-0000-0000000000000000",
  "2026-11-21T09:41:00.123Z-0000-A219E7A71CC18912",
  "2026-11-21T09:41:00.123Z-00FF-A219E7A71CC18912",
  "2026-11-21T09:41:59.999Z-FFFF-0123456789ABCDEF",
  "2024-02-29T23:59:59.000Z-0001-00000000DEADBEEF",
];
const parsing = strings.map((s) => {
  const t = Timestamp.parse(s);
  return {
    input: s,
    millis: t.millis(),
    counter: t.counter(),
    node: t.node(),
    string: t.toString(),
    hash: t.hash(),
  };
});
const invalid = [
  "not-a-timestamp",
  "2026-11-21T09:41:00.123Z-10000-A219E7A71CC18912",
  "2026-11-21T09:41:00.123Z-0000-A219E7A71CC189120",
  "2026-11-21T09:41:00.123Z-0000",
].map((s) => ({ input: s, valid: Timestamp.parse(s) !== null }));

const base = Date.parse("2026-11-21T09:41:00.000Z");
setClock(makeClock(new Timestamp(0, 0, "A219E7A71CC18912")));
const clock = [];
const step = (kind, phys, remote) =>
  at(phys, () => {
    const r =
      kind === "send"
        ? Timestamp.send()
        : Timestamp.recv(Timestamp.parse(remote));
    clock.push({ kind, phys, remote: remote ?? null, result: r.toString() });
  });
step("send", base);
step("send", base);
step("send", base + 5);
step("send", base - 1000);
step("recv", base + 10, "2026-11-21T09:41:00.050Z-0007-0000000000000001");
step("recv", base + 10, "2026-11-21T09:41:00.050Z-0002-0000000000000001");
step("send", base + 100);
step("recv", base + 200, "2026-11-21T09:41:00.150Z-0004-0000000000000002");

const sets = [
  strings.slice(1),
  [
    "2026-11-01T00:00:00.000Z-0000-0000000000000001",
    "2026-11-02T12:30:00.000Z-0003-0000000000000002",
    "2026-11-21T09:41:00.123Z-0000-A219E7A71CC18912",
    "2025-06-15T08:00:00.000Z-0000-0000000000000003",
  ],
  [
    "2026-11-01T00:00:00.000Z-0000-0000000000000001",
    "2026-11-02T12:30:00.000Z-0003-0000000000000002",
    "2026-11-21T09:41:00.123Z-0000-A219E7A71CC18912",
    "2025-06-15T08:00:00.000Z-0000-0000000000000003",
    "2026-11-21T09:45:10.000Z-0000-0000000000000004",
  ],
];
const tries = sets.map((set) => {
  let trie = merkle.emptyTrie();
  for (const s of set) trie = merkle.insert(trie, Timestamp.parse(s));
  return { timestamps: set, trie, pruned: merkle.prune(trie) };
});
const diffs = [
  { a: 0, b: 1, result: merkle.diff(tries[0].trie, tries[1].trie) },
  { a: 1, b: 0, result: merkle.diff(tries[1].trie, tries[0].trie) },
  { a: 0, b: 0, result: merkle.diff(tries[0].trie, tries[0].trie) },
  { a: 1, b: "pruned1", result: merkle.diff(tries[1].trie, tries[1].pruned) },
  { a: 1, b: 2, result: merkle.diff(tries[1].trie, tries[2].trie) },
  { a: 2, b: 1, result: merkle.diff(tries[2].trie, tries[1].trie) },
];
const keyToTimestamp = ["", "1", "2", "0120", "1101211222000110"].map((k) => ({
  key: k,
  millis: merkle.keyToTimestamp(k),
}));

const message = create(MessageSchema, {
  dataset: "transactions",
  row: "tx-1",
  column: "amount",
  value: "N:-5000",
});
const envelope = create(MessageEnvelopeSchema, {
  timestamp: strings[1],
  isEncrypted: false,
  content: toBinary(MessageSchema, message),
});
const encrypted = create(EncryptedDataSchema, {
  iv: new Uint8Array([1, 2, 3]),
  authTag: new Uint8Array([4, 5]),
  data: new Uint8Array([6, 7, 8, 9]),
});
const request = create(SyncRequestSchema, {
  messages: [
    envelope,
    create(MessageEnvelopeSchema, {
      timestamp: strings[2],
      isEncrypted: true,
      content: toBinary(EncryptedDataSchema, encrypted),
    }),
  ],
  fileId: "file-1",
  groupId: "group-1",
  keyId: "key-1",
  since: "2026-11-20T00:00:00.000Z-0000-0000000000000000",
});
const response = create(SyncResponseSchema, {
  messages: [envelope],
  merkle: JSON.stringify(tries[0].pruned),
});
const proto = {
  message: hex(toBinary(MessageSchema, message)),
  messageUnicode: hex(
    toBinary(
      MessageSchema,
      create(MessageSchema, {
        dataset: "payees",
        row: "p-1",
        column: "name",
        value: "S:Café ☕",
      }),
    ),
  ),
  encrypted: hex(toBinary(EncryptedDataSchema, encrypted)),
  envelope: hex(toBinary(MessageEnvelopeSchema, envelope)),
  request: hex(toBinary(SyncRequestSchema, request)),
  response: hex(toBinary(SyncResponseSchema, response)),
  emptyRequest: hex(toBinary(SyncRequestSchema, create(SyncRequestSchema, {}))),
};
const roundTrip = fromBinary(
  SyncResponseSchema,
  toBinary(SyncResponseSchema, response),
);
if (roundTrip.messages.length !== 1) throw new Error("round trip failed");

fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(
  out,
  JSON.stringify(
    { parsing, invalid, clock, tries, diffs, keyToTimestamp, proto },
    null,
    2,
  ) + "\n",
);
console.log(`wrote ${out}`);
