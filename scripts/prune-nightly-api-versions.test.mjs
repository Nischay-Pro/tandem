import { test } from "node:test";
import assert from "node:assert/strict";

import { obsoleteNightlyVersionIds } from "./prune-nightly-api-versions.mjs";

test("keeps the rolling tag and latest fourteen dated nightly versions", () => {
  const versions = Array.from({ length: 16 }, (_, index) => {
    const day = String(16 - index).padStart(2, "0");
    return {
      id: index + 1,
      metadata: {
        container: { tags: [`nightly-202611${day}`, `sha-${index}`] },
      },
    };
  });
  versions[0].metadata.container.tags.push("nightly");

  assert.deepEqual(obsoleteNightlyVersionIds([versions]), ["15", "16"]);
});

test("keeps duplicate digests that carry the rolling tag and ignores unrelated versions", () => {
  const pages = [
    [
      {
        id: 1,
        metadata: {
          container: { tags: ["nightly", "nightly-20260101", "sha-a"] },
        },
      },
      {
        id: 2,
        metadata: { container: { tags: ["nightly-20260101", "sha-b"] } },
      },
    ],
    [{ id: 3, metadata: { container: { tags: ["sha-c"] } } }],
  ];

  assert.deepEqual(obsoleteNightlyVersionIds(pages), []);
});
