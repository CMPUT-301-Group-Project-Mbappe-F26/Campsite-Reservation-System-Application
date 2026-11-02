import { Firestore } from "@google-cloud/firestore";
import { runDueDraws } from "./scheduler.js";

const mode = process.argv[2];
if (!["--apply", "--dry-run"].includes(mode) || process.argv.length !== 3) {
  throw new Error("Specify --dry-run to preview or --apply to persist lottery draws.");
}
const projectId = process.env.GOOGLE_CLOUD_PROJECT;
if (!projectId) throw new Error("GOOGLE_CLOUD_PROJECT must identify the intended Firebase project.");

// Uses short-lived Google credentials from the workflow; no service-account key file is needed.
const db = new Firestore({ projectId });
try {
  const summary = await runDueDraws(db, { dryRun: mode === "--dry-run" });
  console.log(JSON.stringify({ projectId, ...summary }));
  if (summary.failed > 0 || summary.invalid > 0) process.exitCode = 1;
} finally {
  await db.terminate();
}
