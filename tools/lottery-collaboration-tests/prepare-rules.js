import { mkdir, readFile, writeFile } from "node:fs/promises";
// Isolates the lottery fragment under the same parent helpers as the published rules.
// Non-lottery permissions are fixtures, not changes to the deployed application rules.
const helpers = `
function isAdmin() { return request.auth != null && get(/databases/$(database)/documents/users/$(request.auth.uid)).data.get('isAdmin',false); }
function ownsCampsite(id) { return request.auth != null && get(/databases/$(database)/documents/campsites/$(id)).data.ownerId == request.auth.uid; }
function deletingOwnProfile() { return false; }
function removesOwnLotteryEntries(uid) { return false; }
function ownerClosesRemovedCampsite() { return false; }
function validPeriods(d) { return d.registrationStartDate<=d.registrationEndDate && d.registrationEndDate<d.bookingStartDate
  && d.bookingStartDate<=d.bookingEndDate && d.bookingEndDate<d.stayStartDate && d.stayStartDate<d.stayEndDate; }
function pad(n) { return n<10 ? '0'+string(n) : string(n); }
function albertaToday() { let t=request.time-duration.value(6,'h'); return string(t.year())+'-'+pad(t.month())+'-'+pad(t.day()); }
function isVerifiedBooking(b) { return b in get(/databases/$(database)/documents/lotteries/$(b.lotteryId)).data.booked
  || {'id':b.id,'entrantId':b.entrantId,'campsiteId':b.campsiteId,'lotteryId':b.lotteryId,'campsiteName':b.campsiteName,
    'checkInDate':b.checkInDate,'checkOutDate':b.checkOutDate,'status':'Confirmed'} in get(/databases/$(database)/documents/lotteries/$(b.lotteryId)).data.booked; }
match /users/{id} { allow read: if request.auth != null; }
match /campsites/{id} { allow read: if request.auth != null; }
match /reservations/{id} { allow read: if request.auth != null;
  allow update: if ownsCampsite(resource.data.campsiteId) && resource.data.status=='Confirmed' && request.resource.data.status=='Rejected'
    && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['status']); }
`;
await mkdir(new URL(".cache/", import.meta.url), { recursive: true });
const fragment = await readFile(new URL("../../docs/lottery-collaboration.rules", import.meta.url), "utf8");
await writeFile(new URL(".cache/test.rules", import.meta.url),
  `rules_version = '2'; service cloud.firestore { match /databases/{database}/documents { ${helpers}\n${fragment}\n} }`);
