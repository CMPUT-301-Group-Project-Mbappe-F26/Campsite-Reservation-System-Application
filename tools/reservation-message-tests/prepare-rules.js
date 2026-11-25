import { mkdir, readFile, writeFile } from "node:fs/promises";

// Only local test fixtures; no production reservation or account permissions are deployed.
const helpers = `
function isAdmin() {
  return request.auth != null
    && get(/databases/$(database)/documents/users/$(request.auth.uid)).data.get('isAdmin', false);
}
function offeredIds(lottery) { return lottery.get('campsiteIds', [lottery.campsiteId]); }
function isVerifiedBooking(booking) {
  let lottery = get(/databases/$(database)/documents/lotteries/$(booking.lotteryId)).data;
  return booking.campsiteId in offeredIds(lottery) && (
    booking in lottery.booked || {
      'id': booking.id, 'entrantId': booking.entrantId, 'campsiteId': booking.campsiteId,
      'lotteryId': booking.lotteryId, 'campsiteName': booking.campsiteName,
      'checkInDate': booking.checkInDate, 'checkOutDate': booking.checkOutDate, 'status': 'Confirmed'
    } in lottery.booked
  );
}
match /users/{uid} { allow read: if request.auth != null; }
match /campsites/{id} { allow read: if request.auth != null; }
match /reservations/{id} {
  allow read: if request.auth != null && (resource.data.entrantId == request.auth.uid
    || get(/databases/$(database)/documents/campsites/$(resource.data.campsiteId)).data.ownerId == request.auth.uid
    || isAdmin());
}
`;
await mkdir(new URL(".cache/", import.meta.url), { recursive: true });
const fragment = await readFile(new URL("../../docs/reservation-messages.rules", import.meta.url), "utf8");
await writeFile(new URL(".cache/test.rules", import.meta.url),
  `rules_version = '2'; service cloud.firestore { match /databases/{database}/documents { ${helpers}\n${fragment}\n} }`);
