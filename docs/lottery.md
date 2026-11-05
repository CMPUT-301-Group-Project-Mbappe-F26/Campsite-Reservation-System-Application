# Lottery data model

## KDoc and Dokka

The API reference is written as KDoc on `Lottery`, the lottery UI and its helpers,
`LotteryCreationViewModel`, `LotteryService`, and the date/capacity validators.
Update these comments when changing their behavior. Dokka generates browsable HTML from the source
comments; generated files belong in the ignored build directory, not in Git.

From the project root in PowerShell, run:

```powershell
.\gradlew.bat :app:dokkaGeneratePublicationHtml
```

Open `app/build/dokka/html/index.html`, then navigate to the
`com.example.campsitereservationapp.model` package and `Lottery`.
The `campsite` package contains the creation form, UI helpers, submission ViewModel,
and owner details entry point. The `service` package contains persistence and validation.
Dokka includes private and internal declarations in these two packages so the
lottery helper functions appear without changing their Kotlin visibility.
The first run downloads the Dokka plugin and its dependencies.

The app module uses Dokka 2.2.0 with the v2 Gradle plugin configuration. See the
[official Dokka Gradle guide](https://kotlinlang.org/docs/dokka-gradle.html).

## Overview

`Lottery` represents one owner's lottery for one campsite. Its Kotlin definition is
[`Lottery.kt`](../app/src/main/java/com/example/campsitereservationapp/model/Lottery.kt).
`LotteryService` saves each object as a document at `lotteries/{id}` in Firestore.
User and campsite IDs reference separate documents; their full objects are not embedded.

## Fields

| Field | Kotlin type | Meaning |
| --- | --- | --- |
| `id` | `String` | Firestore document ID, reused when retrying the same submission. |
| `ownerId` | `String` | Owner's Firebase Authentication UID. |
| `campsiteId` | `String` | ID of the campsite offered by the lottery. |
| `numberOfWinners` | `Int` | Number of reservation opportunities; called capacity in the form. |
| `registrationStartDate` | `String` | First date entrants can join the waiting list. |
| `registrationEndDate` | `String` | Last registration date. |
| `bookingStartDate` | `String` | First date selected entrants can make a reservation. |
| `bookingEndDate` | `String` | Last booking date. |
| `stayStartDate` | `String` | First campsite stay date offered. |
| `stayEndDate` | `String` | Exclusive checkout end of the offered stay period. |
| `registered` | `List<String>` | User IDs registered for the waiting list. |
| `registeredGroupSizes` | `Map<String, Int>` | Party size chosen by each registered user, keyed by UID. |
| `waitingListCapacity` | `Int` | Maximum distinct entrants accepted; defaults to 1,000. |
| `pending` | `List<String>` | Selected user IDs awaiting a reservation decision. |
| `booked` | `List<Reservation>` | Reservations made through this lottery. |
| `useMaxCapacity` | `Boolean` | Whether the owner selected the Max checkbox. |
| `drawCompleted` | `Boolean` | Whether the initial draw has been saved, including a draw with no entrants. |

String fields default to empty strings, winner count to zero, lists and maps to empty,
waiting-list capacity to 1,000,
and both `useMaxCapacity` and `drawCompleted` to false. These defaults allow Firestore deserialization and
incomplete drafts. A default object cannot be submitted as a valid lottery.

## Creation rules

- The signed-in user's UID must match both `ownerId` and the stored campsite's owner.
- All six dates are required and must be valid `yyyy-MM-dd` dates.
- Registration and booking can each span a single date or multiple dates.
- Registration must end on an earlier calendar date than booking starts.
- Booking must end on an earlier calendar date than the stay starts.
- The stay end must be strictly after the stay start.
- Offered stay nights must fit within the campsite's current availability dates.
  Checkout can be the day after the last available night. The first and last offered
  nights must be available weekdays; the reservation flow filters closed nights
  between them, so a lottery can span multiple weekends.
- Capacity must be a positive integer. With Max unchecked, it must be strictly less
  than the campsite's current `maxOccupancy`. With Max checked, it must equal that maximum.
- A campsite without a positive maximum occupancy cannot be used to create a lottery.

The form checks dates and capacity before enabling Start Lottery. The service
validates dates again and checks ownership, the latest campsite maximum, and current availability inside
the Firestore transaction. Cancelling the form does not save a lottery.

## Date and participant semantics

Registration and booking windows start at 00:00 and end at 23:59 (11:59 PM) on the
selected Alberta date. From November 1, 2026, Alberta Time is permanently UTC-6;
earlier dates follow historical `America/Edmonton` daylight saving rules. Both draw
implementations explicitly apply the new policy to support older timezone databases.
Stay end is an exclusive checkout date, matching the reservation implementation.
The model stores only dates, without a time or time zone. It does not itself schedule
registration opening/closing or winner draws.

`registered` and `pending` are stored as arrays of Firebase user IDs, and `booked`
as an array of reservations. `registeredGroupSizes` stores the party size captured
by the confirmation dialog without changing the draw input. New lotteries start with
all participant collections empty.

## Joining the waiting list

`LotteryService.joinWaitingList` reads the lottery and campsite in one Firestore
transaction. Before adding the signed-in UID, it checks that the inclusive Alberta
registration period is open, the draw has not run, the waiting list has room, the
entrant is not already registered/pending/booked, and the party size is from 1 through
the campsite's current maximum occupancy. The same transaction updates `registered`
and `registeredGroupSizes`, so concurrent taps cannot add the entrant twice or exceed
the waiting-list limit. Cancelling the C01 confirmation dialog never calls the service.

## Drawing winners

`Lottery.draw(amountOfWinners)` returns randomly selected, distinct IDs from
`registered`. When the requested count is at least the number of distinct entrants,
it returns everyone in their existing order. Zero returns an empty list; negative
counts are rejected. Duplicate IDs cannot win multiple times.

```kotlin
val winners = lottery.draw(lottery.numberOfWinners)
```

The function only returns IDs. It does not modify `registered`, `pending`, or
`booked`, persist anything, or check the registration deadline. Calling it again
performs a new draw. Use the service method below to persist an initial draw safely.

## Loading, drawing, and saving

From a coroutine belonging to the signed-in campsite owner:

```kotlin
val drawnLottery = LotteryService().drawLottery(lotteryId)
val pendingWinners = drawnLottery.pending
```

`drawLottery` reads the lottery and its campsite in a Firestore transaction. It
checks ownership against both documents, checks the Alberta registration deadline,
draws the stored `numberOfWinners`, and updates only `pending` and `drawCompleted`.
The registered list and unrelated document fields remain intact. Concurrent edits
cause Firestore to retry using the latest snapshot; a committed draw cannot be
overwritten by another call through this service.

Repeated calls return the current stored state if `drawCompleted` is true. This
also covers empty lotteries and winners who later leave pending to book. Older
documents missing the marker deserialize as false; if they already contain pending
or booked users, the service refuses to overwrite them and asks for review.

The Android method is callable but has no UI button or timer. It uses the
Android device clock to check the deadline; authoritative automatic timing belongs
in the future backend. Firestore rules must allow the owner's transaction reads
and the update to `pending`/`drawCompleted`. No live Firebase rules are deployed by
this implementation.

Automatic drawing is prepared as an opt-in GitHub Actions workflow, avoiding the
Blaze requirement for scheduled Firebase functions. It uses equivalent Node.js logic
to find expired lotteries and atomically save `pending` and `drawCompleted`.
The workflow is disabled until Google authentication and no-cost Actions settings
are configured; see [automatic draw setup](lottery-scheduler.md).
The agreed registration cutoff is 23:59 (11:59 PM) on `registrationEndDate` in
`America/Edmonton`. The scheduler should draw after that closing minute has elapsed,
beginning at 00:00 the following day. It must convert the agreed local deadline
to an instant or compare Alberta calendar dates since the current model stores date
strings only. The GitHub runner uses Alberta calendar dates. It does not directly
call the Android Kotlin method and has no notification implementation.

## Confirming reservations

`ReservationService.reserve` moves a winner from `pending` to `booked` in a Firestore
transaction. Confirmed reservations cannot exceed `numberOfWinners`, and each night
can only be booked once. A stay can check out on the day another stay checks in.

Enforcement of campsite season/available weekdays at reservation time is in
`reservationError`. Lottery creation constrains offered stay dates in the form and
rechecks current campsite availability in the save transaction using
`lotteryStayAvailabilityError`. Registration and booking windows can precede the
campsite's season. A campsite availability edit also affects subsequent reservation
checks for existing lotteries; saved lotteries are not automatically rewritten.
Deployed Firestore security rules are outside the current implementation.
The data class constructor performs no creation validation or state transitions;
`draw` validates only its requested winner count.
