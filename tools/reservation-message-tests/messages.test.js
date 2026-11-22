import { readFile } from "node:fs/promises";
import { before, beforeEach, after, test } from "node:test";
import assert from "node:assert/strict";
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { collection, doc, setDoc, getDocs, getDoc, updateDoc, deleteDoc, runTransaction,
  serverTimestamp, Timestamp, setLogLevel } from "firebase/firestore";

setLogLevel("silent");
let environment;
const db = uid => environment.authenticatedContext(uid).firestore();
const ref = (client, id = "first", reservation = "booking") => doc(client, `reservations/${reservation}/messages/${id}`);
const payload = (uid, text = "What time is check-in?") => ({ senderId: uid, text, sentAt: serverTimestamp() });
const booking = { id: "booking", entrantId: "guest", campsiteId: "site", lotteryId: "lottery",
  campsiteName: "Two Jack", checkInDate: "2027-07-02", checkOutDate: "2027-07-04", status: "Confirmed" };
async function seed(entries) {
  await environment.withSecurityRulesDisabled(async context => {
    for (const [path, data] of Object.entries(entries)) await setDoc(doc(context.firestore(), path), data);
  });
}
before(async () => {
  environment = await initializeTestEnvironment({ projectId: "demo-reservation-messages",
    firestore: { host: "127.0.0.1", port: 8087,
      rules: await readFile(new URL(".cache/test.rules", import.meta.url), "utf8") } });
});
after(async () => { await environment?.cleanup(); });
beforeEach(async () => {
  await environment.clearFirestore();
  await seed({
    "users/guest": { isAdmin: false }, "users/owner": { isAdmin: false },
    "users/stranger": { isAdmin: false }, "users/admin": { isAdmin: true },
    "campsites/site": { ownerId: "owner" },
    "reservations/booking": booking,
    "lotteries/lottery": { campsiteId: "site", booked: [booking] }
  });
});

test("entrant can open an empty conversation and send the first message", async () => {
  const guest = db("guest");
  assert.equal((await assertSucceeds(getDocs(collection(guest, "reservations/booking/messages")))).size, 0);
  await assertSucceeds(setDoc(ref(guest), payload("guest")));
  const received = await assertSucceeds(getDoc(ref(db("owner"))));
  assert.equal(received.data().text, "What time is check-in?");
});
test("owner reply appears in the same reservation thread", async () => {
  await assertSucceeds(setDoc(ref(db("guest")), payload("guest")));
  await assertSucceeds(setDoc(ref(db("owner"), "reply"), payload("owner", "Check-in is at 3 pm.")));
  assert.equal((await getDocs(collection(db("guest"), "reservations/booking/messages"))).size, 2);
});
test("a retry with the same ID does not create another message", async () => {
  const guest = db("guest"), target = ref(guest);
  const send = () => runTransaction(guest, async transaction => {
    const existing = await transaction.get(target);
    if (!existing.exists()) transaction.set(target, payload("guest"));
  });
  await assertSucceeds(send());
  await assertSucceeds(send());
  assert.equal((await getDocs(collection(guest, "reservations/booking/messages"))).size, 1);
});
test("outsiders, unrelated owners, and signed-out users cannot read or send", async () => {
  await seed({ "reservations/booking/messages/first": { senderId: "guest", text: "Hello", sentAt: Timestamp.now() } });
  for (const client of [db("stranger"), db("other-owner"), environment.unauthenticatedContext().firestore()]) {
    await assertFails(getDoc(ref(client)));
    await assertFails(setDoc(ref(client, "blocked"), payload("stranger")));
  }
});
test("a reservation is required, and the sender cannot impersonate the owner", async () => {
  await assertFails(setDoc(ref(db("guest"), "first", "missing"), payload("guest")));
  await assertFails(setDoc(ref(db("guest")), payload("owner")));
  await assertFails(setDoc(ref(db("owner")), payload("guest")));
});
test("cancelled and rejected entrants can read history but cannot send new messages", async () => {
  await assertSucceeds(setDoc(ref(db("guest")), payload("guest")));
  for (const status of ["Cancelled", "Rejected"]) {
    await seed({ "reservations/booking": { ...booking, status } });
    await assertSucceeds(getDoc(ref(db("guest"))));
    await assertFails(setDoc(ref(db("guest"), `blocked-${status}`), payload("guest")));
    await assertSucceeds(setDoc(ref(db("owner"), `owner-${status}`), payload("owner")));
  }
});
test("a fabricated reservation without a matching lottery booking cannot send", async () => {
  await seed({ "lotteries/lottery": { campsiteId: "site", booked: [] } });
  await assertFails(setDoc(ref(db("guest")), payload("guest")));
  await assertFails(setDoc(ref(db("owner")), payload("owner")));
});
test("messages reject blanks, oversized text, extra fields, and client timestamps", async () => {
  const guest = db("guest");
  for (const text of ["", " \n\t", "a".repeat(1001)]) await assertFails(setDoc(ref(guest), payload("guest", text)));
  await assertSucceeds(setDoc(ref(guest, "boundary"), payload("guest", "a".repeat(1000))));
  await assertFails(setDoc(ref(guest), { ...payload("guest"), ownerId: "owner" }));
  await assertFails(setDoc(ref(guest), { ...payload("guest"), sentAt: Timestamp.fromMillis(0) }));
});
test("transcripts are immutable and admin read access does not permit sending", async () => {
  await assertSucceeds(setDoc(ref(db("guest")), payload("guest")));
  await assertSucceeds(getDoc(ref(db("admin"))));
  for (const uid of ["guest", "owner", "admin"]) {
    await assertFails(updateDoc(ref(db(uid)), { text: "Changed" }));
    await assertFails(deleteDoc(ref(db(uid))));
  }
  await assertFails(setDoc(ref(db("admin"), "admin-send"), payload("admin")));
});
