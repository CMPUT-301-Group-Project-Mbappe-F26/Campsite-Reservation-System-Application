import test from "node:test";
import assert from "node:assert/strict";
import { DrawDataError } from "../src/draw.js";
import { drawLotteryDocument, runDueDraws } from "../src/scheduler.js";

const now = new Date("2027-04-08T06:00:00Z");
const lottery = {
  ownerId: "owner", campsiteId: "camp", registrationEndDate: "2027-04-07",
  numberOfWinners: 2, registered: ["alice", "bob", "charlie"],
  pending: [], booked: [], drawCompleted: false, extra: "preserve me",
};

// Minimal in-memory transaction/query adapter; tests never connect to a real project.
function store(entries = { "lotteries/draw": lottery, "campsites/camp": { ownerId: "owner" } }) {
  const records = new Map(Object.entries(structuredClone(entries)));
  let writes = 0;
  let queue = Promise.resolve();
  const snapshot = (path) => ({
    id: path.split("/").at(-1), ref: { path }, exists: records.has(path),
    data: () => structuredClone(records.get(path)),
  });
  const db = {
    records, get writes() { return writes; },
    collection(name) {
      let cutoff, size = 100, after;
      return {
        doc(id) { return { path: `${name}/${id}` }; },
        where(field, operator, value) { assert.equal(field, "registrationEndDate"); assert.equal(operator, "<"); cutoff = value; return this; },
        orderBy(field) { assert.equal(field, "registrationEndDate"); return this; },
        limit(value) { size = value; return this; },
        startAfter(value) { after = value.ref.path; return this; },
        async get() {
          const paths = [...records.keys()].filter((path) => path.startsWith(`${name}/`) && records.get(path).registrationEndDate < cutoff)
            .sort((a, b) => records.get(a).registrationEndDate.localeCompare(records.get(b).registrationEndDate) || a.localeCompare(b));
          const start = after ? paths.indexOf(after) + 1 : 0;
          const docs = paths.slice(start, start + size).map(snapshot);
          return { docs, empty: docs.length === 0, size: docs.length };
        },
      };
    },
    runTransaction(callback) {
      const task = queue.then(async () => {
        let update;
        const result = await callback({
          async get(reference) { assert.equal(update, undefined, "reads must precede writes"); return snapshot(reference.path); },
          update(reference, fields) { update = { reference, fields }; },
        });
        if (update) {
          records.set(update.reference.path, { ...records.get(update.reference.path), ...update.fields });
          writes++;
        }
        return result;
      });
      queue = task.catch(() => {});
      return task;
    },
  };
  return db;
}

test("transaction saves initial winners and preserves unrelated fields", async () => {
  const db = store();
  const result = await drawLotteryDocument(db, { path: "lotteries/draw" }, { now, dryRun: false });
  assert.equal(result.status, "drawn");
  const saved = db.records.get("lotteries/draw");
  assert.equal(saved.drawCompleted, true);
  assert.equal(saved.pending.length, 2);
  assert.deepEqual(saved.registered, lottery.registered);
  assert.deepEqual(saved.booked, []);
  assert.equal(saved.extra, "preserve me");
});

test("repeat calls cannot overwrite a saved initial draw", async () => {
  const db = store();
  const results = await Promise.all([
    drawLotteryDocument(db, { path: "lotteries/draw" }, { now, dryRun: false }),
    drawLotteryDocument(db, { path: "lotteries/draw" }, { now, dryRun: false }),
  ]);
  assert.deepEqual(results.map((result) => result.status), ["drawn", "completed"]);
  assert.equal(db.writes, 1);
});

test("dry run validates and previews without writing", async () => {
  const db = store();
  assert.equal((await drawLotteryDocument(db, { path: "lotteries/draw" }, { now })).status, "previewed");
  assert.deepEqual(db.records.get("lotteries/draw"), lottery);
  assert.equal(db.writes, 0);
});

test("missing campsite and ownership mismatches are rejected without writing", async () => {
  for (const campsite of [undefined, { ownerId: "someone-else" }]) {
    const entries = { "lotteries/draw": lottery };
    if (campsite) entries["campsites/camp"] = campsite;
    const db = store(entries);
    await assert.rejects(drawLotteryDocument(db, { path: "lotteries/draw" }, { now, dryRun: false }), DrawDataError);
    assert.equal(db.writes, 0);
  }
});

test("missing and not-yet-due lotteries do not write", async () => {
  const db = store();
  assert.equal((await drawLotteryDocument(db, { path: "lotteries/missing" }, { now, dryRun: false })).status, "skipped");
  assert.equal((await drawLotteryDocument(db, { path: "lotteries/draw" }, { now: new Date("2027-04-08T05:59:59Z"), dryRun: false })).status, "skipped");
  assert.equal(db.writes, 0);
});

test("runner paginates, skips completed draws, and continues past invalid records", async () => {
  const db = store({
    "campsites/camp": { ownerId: "owner" },
    "lotteries/a": { ...lottery, drawCompleted: true },
    "lotteries/b": { ...lottery, pending: ["old-result"] },
    "lotteries/c": lottery,
    "lotteries/d": { ...lottery, registrationEndDate: "2027-04-08" },
  });
  const errors = [];
  const result = await runDueDraws(db, { now, pageSize: 1, dryRun: false, onError: (id) => errors.push(id) });
  assert.equal(result.scanned, 3);
  assert.equal(result.completed, 1);
  assert.equal(result.invalid, 1);
  assert.equal(result.drawn, 1);
  assert.deepEqual(errors, ["b"]);
  assert.equal(db.records.get("lotteries/d").drawCompleted, false);
  assert.equal(db.writes, 1);
});

test("empty collection produces an empty summary", async () => {
  const result = await runDueDraws(store({}), { now });
  assert.equal(result.scanned, 0);
  assert.equal(result.failed, 0);
});

test("runner reports transient failures while processing remaining records", async () => {
  const db = store({ "lotteries/a": lottery, "lotteries/b": lottery, "campsites/camp": { ownerId: "owner" } });
  const transaction = db.runTransaction;
  let calls = 0;
  db.runTransaction = (...args) => ++calls === 1 ? Promise.reject(new Error("temporarily unavailable")) : transaction(...args);
  const result = await runDueDraws(db, { now, dryRun: false, onError: () => {} });
  assert.equal(result.failed, 1);
  assert.equal(result.drawn, 1);
});
