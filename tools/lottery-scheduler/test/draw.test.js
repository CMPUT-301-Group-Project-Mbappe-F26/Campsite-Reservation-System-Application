import test from "node:test";
import assert from "node:assert/strict";
import { albertaDate, drawRegistered, DrawDataError, prepareDraw } from "../src/draw.js";

const lottery = {
  registered: ["alice", "bob", "charlie"], pending: [], booked: [],
  numberOfWinners: 2, registrationEndDate: "2027-04-07", drawCompleted: false,
};
const midnight = new Date("2027-04-08T06:00:00Z");
const reservation = {
  id: "booking", entrantId: "alice", campsiteId: "camp", lotteryId: "draw",
  campsiteName: "Camp", checkInDate: "2027-05-01", checkOutDate: "2027-05-03", status: "Confirmed",
};

test("draw returns everyone when the count covers all entrants", () => {
  assert.deepEqual(drawRegistered(lottery.registered, 3), lottery.registered);
  assert.deepEqual(drawRegistered(lottery.registered, 5), lottery.registered);
});

test("random draw selects only distinct registered users and preserves its input", () => {
  const registered = ["alice", "alice", "bob", "charlie", "dana"];
  const before = [...registered];
  const winners = drawRegistered(registered, 3);
  assert.equal(winners.length, 3);
  assert.equal(new Set(winners).size, 3);
  assert.ok(winners.every((id) => registered.includes(id)));
  assert.deepEqual(registered, before);
});

test("duplicate registrations count once when everyone wins", () => {
  assert.deepEqual(drawRegistered(["alice", "alice", "bob"], 10), ["alice", "bob"]);
});

test("zero count and empty lists produce no winners", () => {
  assert.deepEqual(drawRegistered(lottery.registered, 0), []);
  assert.deepEqual(drawRegistered([], 3), []);
});

test("invalid winner counts and registration IDs are rejected", () => {
  for (const count of [-1, 1.5, NaN, Infinity]) {
    assert.throws(() => drawRegistered([], count), DrawDataError);
  }
  for (const registered of [null, "alice", [""], [1]]) {
    assert.throws(() => drawRegistered(registered, 1), DrawDataError);
  }
});

test("registration is still open throughout the closing minute", () => {
  assert.equal(prepareDraw(lottery, new Date(midnight.getTime() - 1)), null);
});

test("midnight Alberta time prepares winners and completion marker", () => {
  const update = prepareDraw(lottery, midnight);
  assert.equal(update.drawCompleted, true);
  assert.equal(update.pending.length, 2);
  assert.deepEqual(Object.keys(update).sort(), ["drawCompleted", "pending"]);
  assert.equal(lottery.drawCompleted, false);
  assert.deepEqual(lottery.pending, []);
});

test("future registration periods are not drawn", () => {
  assert.equal(prepareDraw({ ...lottery, registrationEndDate: "2027-04-09" }, midnight), null);
});

test("completed draws stay completed after winners book", () => {
  const booked = { ...lottery, drawCompleted: true, pending: [], booked: [reservation] };
  assert.equal(prepareDraw(booked, midnight), null);
  assert.deepEqual(booked.booked, [reservation]);
});

test("empty lottery completes once and cannot draw late registrations", () => {
  const empty = { ...lottery, registered: [] };
  const update = prepareDraw(empty, midnight);
  assert.deepEqual(update, { pending: [], drawCompleted: true });
  assert.equal(prepareDraw({ ...empty, ...update, registered: ["late-user"] }, midnight), null);
});

test("legacy missing marker is supported but existing results are not overwritten", () => {
  const { drawCompleted, ...legacy } = lottery;
  assert.equal(prepareDraw(legacy, midnight).drawCompleted, true);
  assert.throws(() => prepareDraw({ ...legacy, pending: ["alice"] }, midnight), DrawDataError);
  assert.throws(() => prepareDraw({ ...legacy, booked: [reservation] }, midnight), /already has pending users or reservations/);
});

test("invalid dates, participant lists, and marker values are rejected", () => {
  for (const date of [undefined, "", "2027-4-07", "2027-02-29", "2027-04-07extra"]) {
    assert.throws(() => prepareDraw({ ...lottery, registrationEndDate: date }, midnight), DrawDataError);
  }
  assert.throws(() => prepareDraw({ ...lottery, numberOfWinners: 0 }, midnight), DrawDataError);
  assert.throws(() => prepareDraw({ ...lottery, drawCompleted: "false" }, midnight), DrawDataError);
  assert.throws(() => prepareDraw({ ...lottery, pending: null }, midnight), DrawDataError);
  assert.throws(() => prepareDraw({ ...lottery, booked: ["alice"] }, midnight), /booked must contain reservation objects/);
});

test("spring daylight saving deadline follows Alberta local midnight", () => {
  const spring = { ...lottery, registrationEndDate: "2027-03-14" };
  assert.equal(prepareDraw(spring, new Date("2027-03-15T05:59:59Z")), null);
  assert.equal(prepareDraw(spring, new Date("2027-03-15T06:00:00Z")).drawCompleted, true);
});

test("fall daylight saving deadline follows Alberta local midnight", () => {
  const fall = { ...lottery, registrationEndDate: "2027-11-07" };
  assert.equal(prepareDraw(fall, new Date("2027-11-08T06:59:59Z")), null);
  assert.equal(prepareDraw(fall, new Date("2027-11-08T07:00:00Z")).drawCompleted, true);
});

test("runner UTC date does not prematurely close an Alberta registration day", () => {
  assert.equal(albertaDate(new Date("2027-04-08T00:00:00Z")), "2027-04-07");
  assert.equal(albertaDate(midnight), "2027-04-08");
});
