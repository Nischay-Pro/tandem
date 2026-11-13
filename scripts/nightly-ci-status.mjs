import { Buffer } from "node:buffer";
import { resolve } from "node:path";
import process from "node:process";
import { pathToFileURL } from "node:url";

export function nightlyCiDecision(runs) {
  if (!Array.isArray(runs)) {
    throw new TypeError("GitHub Actions runs must be a JSON array");
  }

  const pushRun = runs.find((run) => run?.event === "push");
  if (!pushRun) {
    return { decision: "wait", status: "missing", conclusion: "" };
  }

  const status =
    typeof pushRun.status === "string" ? pushRun.status : "missing";
  const conclusion =
    typeof pushRun.conclusion === "string" ? pushRun.conclusion : "";

  if (status !== "completed") {
    return { decision: "wait", status, conclusion };
  }

  return {
    decision: conclusion === "success" ? "publish" : "skip",
    status,
    conclusion,
  };
}

const invokedPath = process.argv[1];
if (
  invokedPath &&
  import.meta.url === pathToFileURL(resolve(invokedPath)).href
) {
  const input = [];
  try {
    for await (const chunk of process.stdin) input.push(chunk);
    const runs = JSON.parse(Buffer.concat(input).toString("utf8"));
    process.stdout.write(JSON.stringify(nightlyCiDecision(runs)));
  } catch (error) {
    process.stderr.write(`Could not read CI run status: ${error.message}\n`);
    process.exitCode = 1;
  }
}
