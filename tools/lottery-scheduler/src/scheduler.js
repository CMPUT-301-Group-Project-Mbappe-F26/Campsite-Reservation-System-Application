import { albertaDate, DrawDataError, prepareDraw } from "./draw.js";

/** Reads current registrations and commits only the initial draw in a transaction. */
export async function drawLotteryDocument(db, reference, { now, dryRun = true }) {
  return db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(reference);
    if (!snapshot.exists) return { status: "skipped" };
    const lottery = snapshot.data();
    if (lottery.drawCompleted === true) return { status: "completed" };
    const update = prepareDraw(lottery, now);
    if (!update) return { status: "skipped" };
    if (typeof lottery.ownerId !== "string" || !lottery.ownerId.trim() ||
        typeof lottery.campsiteId !== "string" || !lottery.campsiteId.trim() ||
        lottery.campsiteId.includes("/")) {
      throw new DrawDataError("Lottery must reference a campsite and its owner.");
    }
    const campsite = await transaction.get(db.collection("campsites").doc(lottery.campsiteId));
    if (!campsite.exists || campsite.data().ownerId !== lottery.ownerId) {
      throw new DrawDataError("Campsite is missing or its owner does not match the lottery.");
    }
    if (!dryRun) transaction.update(reference, update);
    return { status: dryRun ? "previewed" : "drawn", winnerCount: update.pending.length };
  });
}

/**
 * Visits expired lotteries in pages, including legacy documents with no completion marker.
 * Completed documents are skipped before a transaction. Individual invalid records are
 * reported without preventing other valid draws; transient failures mark the run failed.
 */
export async function runDueDraws(db, {
  now = new Date(), dryRun = true, pageSize = 100,
  onError = (id, error) => console.error(JSON.stringify({ lotteryId: id, error: error.message })),
} = {}) {
  if (!Number.isInteger(pageSize) || pageSize < 1) throw new Error("pageSize must be positive.");
  const summary = { scanned: 0, completed: 0, drawn: 0, previewed: 0, skipped: 0, invalid: 0, failed: 0, dryRun };
  const base = db.collection("lotteries")
    .where("registrationEndDate", "<", albertaDate(now))
    .orderBy("registrationEndDate").limit(pageSize);
  let cursor;
  while (true) {
    const page = await (cursor ? base.startAfter(cursor) : base).get();
    if (page.empty) break;
    for (const snapshot of page.docs) {
      summary.scanned++;
      if (snapshot.data().drawCompleted === true) {
        summary.completed++;
        continue;
      }
      try {
        const result = await drawLotteryDocument(db, snapshot.ref, { now, dryRun });
        summary[result.status]++;
      } catch (error) {
        summary[error instanceof DrawDataError ? "invalid" : "failed"]++;
        onError(snapshot.id, error);
      }
    }
    cursor = page.docs.at(-1);
    if (page.size < pageSize) break;
  }
  return summary;
}
