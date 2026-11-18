# Shared lotteries across campsite owners

Implements the owner collaboration story and preserves winner selection from US 01.04.02.
The coordinator creates the lottery with their own campsites. Other campsites become
offerings only after their current owner accepts an invitation for the displayed terms.

## Owner flow

1. Create a lottery from an owned campsite's details as before.
2. In **Lottery collaboration**, choose **Invite another owner's campsite**, select a
   published campsite, and send the invitation. Pending invitations reserve a slot
   but do not appear to entrants or winners.
3. The invited owner opens that campsite under **My Campsites**. Its details show a
   **Shared lottery invitation**, including registration, booking, stay dates and
   winner count. **Accept invitation → Proceed** adds only that campsite.
   **Decline → Proceed** removes the invitation without adding a campsite.
4. Both owners see the shared lottery from their own campsite details. Contributors
   can withdraw only their own additional campsite. They continue editing their own
   campsite and managing its reservations through the existing owner controls.
5. Only the coordinator edits shared lottery settings. Editing dates
   moves accepted foreign campsites back to pending invitations. They are excluded
   from the offering until their owners review and accept the new terms again.
   Winner-limit and waitlist changes retain campsite consent and notify entrants.

## Rules and interactions

- At most eight accepted plus pending campsites; no duplicate offering or invitation.
- Invitation, acceptance, decline and withdrawal stop once drawn or booked.
- Acceptance rechecks the actual campsite owner, publication, availability, capacity,
  current lottery revision and participant state in a transaction. A stale invitation
  cannot silently accept new terms. An entrant must leave the waitlist before accepting
  an ownership role. Accepted owners cannot enter their shared lottery.
- Acceptance keeps the agreed winner count. Max becomes a fixed count when the pool
  grows. Withdrawal reduces winners only when needed to fit remaining occupancy;
  Max is selected if the remaining occupancy equals the winner count.
- Contributors cannot remove other owners' offerings, edit common dates, change
  ownership grants or delete the entire lottery. The coordinator cannot remove
  accepted foreign campsites through the edit form or delete a shared lottery while
  other owners' campsites remain. They must withdraw first.
- Deleting an owned campsite cannot close another owner's active shared lottery.
  Withdraw the campsite first. The primary coordinator campsite must remain until
  contributors withdraw. Admin moderation retains its existing authority.
- Acceptance, withdrawal and ordinary listing edits notify registered entrants using
  the existing durable lottery notices, in the same transaction as the membership
  change. Notices are subject to the entrant's existing notification preferences.
- Winners choose only accepted campsites using the existing selection screen. Owner
  grants are checked against current ownership before booking and automatic drawing.
  Pending invitations never expand the draw's offering or block the drawing deadline.
- A contributing owner rejecting a reservation supplies `rejectedReservationId` in
  the lottery write. Rules verify that reservation, its campsite owner and the paired
  Rejected status, rather than giving the coordinator authority over all bookings.

## Model and separation of concerns

`Lottery.ownerId` remains the coordinator, and the primary campsite belongs to that
owner. `campsiteIds` contains only accepted offerings. `campsiteOwners` maps accepted
campsite IDs to their accepting owner UIDs; missing legacy entries belong to the
coordinator. `campsiteInvitations` maps pending campsite IDs to the invited owner UID.
`listingRevision` protects consent and edits from concurrent changes.
`collaborationAction` and `collaborationCampsiteId` identify the precise membership
operation for server-side authorization; they do not replace consent checks.

Pure functions in `LotteryCollaborationRules.kt` define invitations, acceptance,
withdrawal, and consent renewal. `LotteryCollaborationService` performs authoritative
transactions and writes notices. `LotteryCollaborationViewModel` coordinates loading
and actions. `LotteryCollaboration.kt` renders state and receives callbacks, allowing
its role restrictions and confirmation dialogs to be tested without Firebase.

Existing single-owner and single-campsite documents need no migration. The scheduler
uses each accepted campsite's owner grant, falling back to the coordinator for legacy
documents. The entrant UI needs no additional screen changes.

## Required Firestore permission update

The currently published single-owner rules reject acceptance by a second owner. Before
using this feature against Firebase, merge [lottery-collaboration.rules](lottery-collaboration.rules)
into the **existing** Firestore rules in Firebase Console:

1. Keep `rules_version`, `service cloud.firestore`, the database documents scope,
   and all existing user, campsite, reservation, messaging and admin rules.
2. Inside `/databases/{database}/documents`, add the fragment's `lc*` helpers.
3. **Replace** the existing `match /lotteries/{lotteryId}` and
   `match /lotteryNotices/{noticeId}` blocks with the fragment's corresponding blocks.
   Do not leave the old blocks alongside them: multiple matching allow rules combine.
4. Keep the existing helpers referenced by the fragment: `validPeriods`,
   `albertaToday`, `ownsCampsite`, `isVerifiedBooking`, `isAdmin`,
   `deletingOwnProfile` and `removesOwnLotteryEntries`. These were included in the
   team's previously published rules from this session.
5. Review and publish the combined rules. The fragment is deliberately **not** a full
   replacement ruleset: publishing it alone would remove unrelated app permissions.

These rules constrain each operation to its actor and target, require fresh atomic
entrant notices for accepted membership changes, and reject forged owner grants,
stale revisions and cross-owner campsite removal. No live rules are deployed by the
code changes in this repository.

## Validation and documentation

Kotlin unit tests cover the collaboration rules in isolation and their interactions
with capacity, editing, waitlist eligibility and winner selection. Compose intent tests
cover owner-specific actions and confirmation. Scheduler tests cover grants across
owners and transferred campsites. The local Firestore suite tests the merge fragment
against isolated parent-helper fixtures; it does not replace or deploy other app rules.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:dokkaGeneratePublicationHtml --offline
cd tools/lottery-scheduler
npm test
cd ../lottery-collaboration-tests
npm ci --ignore-scripts --no-audit --no-fund --cache .cache/npm
$env:FIREBASE_EMULATORS_PATH = Join-Path (Get-Location) '.cache/emulators'
$env:XDG_CONFIG_HOME = Join-Path (Get-Location) '.cache/config'
npm test
```

The security suite uses `demo-shared-lottery` and localhost only. Java 21+ and Node 22+
are required. Dependencies, emulator downloads, generated rule fixtures and logs are
ignored. Test sources and this scoped deployment fragment are durable project files.
Generated Dokka HTML remains at `app/build/dokka/html/index.html`; the owner, service,
model and reservation documentation includes this and the earlier lottery stories.
