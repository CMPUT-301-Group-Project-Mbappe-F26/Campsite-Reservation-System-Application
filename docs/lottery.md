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
| `stayEndDate` | `String` | End of the offered stay period. |
| `registered` | `List<String>` | User IDs registered for the waiting list. |
| `pending` | `List<String>` | Selected user IDs awaiting a reservation decision. |
| `booked` | `List<String>` | User IDs with a confirmed reservation through this lottery. |
| `useMaxCapacity` | `Boolean` | Whether the owner selected the Max checkbox. |

String fields default to empty strings, winner count to zero, lists to empty lists,
and `useMaxCapacity` to false. These defaults allow Firestore deserialization and
incomplete drafts. A default object cannot be submitted as a valid lottery.

## Creation rules

- The signed-in user's UID must match both `ownerId` and the stored campsite's owner.
- All six dates are required and must be valid `yyyy-MM-dd` dates.
- Registration and booking can each span a single date or multiple dates.
- Registration must end on an earlier calendar date than booking starts.
- Booking must end on an earlier calendar date than the stay starts.
- The stay end must be strictly after the stay start.
- Capacity must be a positive integer. With Max unchecked, it must be strictly less
  than the campsite's current `maxOccupancy`. With Max checked, it must equal that maximum.
- A campsite without a positive maximum occupancy cannot be used to create a lottery.

The form checks dates and capacity before enabling Start Lottery. The service
validates dates again and checks ownership and the latest campsite maximum inside
the Firestore transaction. Cancelling the form does not save a lottery.

## Date and participant semantics

The UI displays start dates as 00:00 and end dates as 11:59 on the selected date.
The model stores only dates, without a time or time zone; there is no automatic
opening, closing, or winner draw implemented yet.

`registered`, `pending`, and `booked` are stored as arrays of Firebase user IDs.
New lotteries start with all three arrays empty. Their names describe the intended
participant states; joining, drawing winners, booking, and moving users between
these lists are not implemented by the creation flow.

Existing-reservation conflict checks, enforcement of campsite season/available
weekdays, and deployed Firestore security rules are outside the current creation
implementation. The data class itself performs no validation or state transitions.
