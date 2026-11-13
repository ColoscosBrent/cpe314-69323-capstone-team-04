# Response to the Instructor's Reviews

This page lists each point from the instructor's reviews, what we changed, and where. The first review is about the proposal and the first version of the app; the later ones are about the finished app and its design document.

## First review: to-do list

| # | Instructor's point | What we changed | Where | Member |
|---|---|---|---|---|
| 1 | Add a DB-backed test for `insertRental` and the rental list, with a rental overlap case | New class `DatabaseRentalTest` with 3 tests: a vehicle is free again after its rental is returned; an overlapping rental for a vehicle that is out is not saved (the renter waits instead); a returned vehicle can be rented again. `DatabaseTest.rentalIsSavedAndFoundByRenter` already covered saving and listing. | `src/test/java/fleetrent/DatabaseRentalTest.java` | Gabasa, Dexter |
| 1 | (waiting list around overlap) | Two more tests: waiting renters are served in the order they joined; a waiting list belongs to one vehicle only. | `RentalServiceTest.java` | Lagaya, John Carlo |
| 2 | Handle `IllegalArgumentException` in the Add Vehicle handler | The handler now catches it and shows a red message. The form also stops blank makes and a daily rate of 0 or less with red messages before the handler runs. | `VehicleTab.java`, method `addVehicle` | Labra, Simone |
| 3 | Add a second GUI control that makes the Queue and Stack visible | The Rentals tab has **Start rental** (if the vehicle is out, the renter joins the `WaitingList` and the screen shows how many are waiting) and **Undo last check-out** (the `CheckoutHistory` stack). Done in Milestone 2. | `RentalTab.java`, `RentalService.java` | Lagaya, John Carlo; Labra, Simone |
| 4 | Spread the commits | Brent had 63 of 121 commits at the first review. On November 6 it was 65 of 133 (49%). The fixes in this list are committed by the other four members; Brent only merges and opens the final pull request. | Git history | All |
| 5 | Re-export the PDFs with 1-inch margins; keep one `design-document.md` | The Design Document PDF is exported with 1-inch margins on all four sides. The folder `Contributions/` (copies of the sections) is deleted, so `docs/design-document.md` is the only copy. | `docs/`, `Contributions/` | Rosal, Wendil; Coloscos, Brent |

## Bugs and risks the review found

| Finding | Status |
|---|---|
| `IllegalArgumentException` escapes the Add Vehicle handler | Fixed (see item 2). |
| A duplicate plate number shows a raw driver message | Add Vehicle checks the plate first and shows "A vehicle with that plate number already exists." A database `UNIQUE` error is turned into "That value already exists." by `ErrorMessages`. |
| The extra-mile fee is not visible in the UI | A hint under the fleet list gives the free miles and the fee per extra mile for each type. The final cost with extra miles is shown when a rental is returned. |
| Rentals that overlap the same vehicle are not prevented | A new rental is refused while the vehicle is out (the renter waits). The CSV import saves past history as it is and does not check overlap. This limit is listed in `docs/known-issues.md` (item 17). |

## What else the review asked to change

| Point | Status |
|---|---|
| Only one GUI handler | Every tab now has wired controls: Vehicles (Add, Reload from NHTSA, Find photos, Choose photo), Rentals (Start, Return, Undo), Import (Add demo vehicles, Create practice CSV, Choose CSV, Import), Dashboard (question, dates, Show). |
| Fewest tests (16) | 452 JUnit 5 tests in 49 classes, all passing (16 at the first review, 129 after its fixes). They include edge cases, booking races between windows and a click-through of the screens. |
| Proposal risks have no plan or instructor reply | Section 6 of `docs/proposal.md` now has a plan for each risk and a column for the instructor's reply. The replies still have to be asked. |

## Owner and renter requests

The instructor said the app still had a lot to do and must be finalized by Tuesday. The owner and renter requests are listed here with where each one is done.

| Request | What we did | Where |
|---|---|---|
| Remove button for a broken vehicle | Remove on every vehicle card. The vehicle disappears from the fleet and from renters. Its reservations are cancelled. A vehicle that is rented out cannot be removed. Removed vehicles can be restored. | `VehicleTab.java`, `FleetService.java` |
| Make and model bug | The clicked make and model are remembered, so Add Vehicle works after the lists reload. | `VehicleTab.java`, `Validators.java`, `ValidatedField.java` |
| Owner sign up, log in, forgot password, sign out | Done with a security question. No owner code: any shop owner can sign up. Every sign-up asks for last, first and middle name and a phone number with a country; a renter also gives email and address. The login says which of the username or password is wrong. | `AuthScreen.java`, `AuthService.java` |
| Edit a vehicle | Edit button: photo, fees (daily rate), plate, mileage, and so on. | `VehicleTab.java`, `FleetService.java` |
| Separate renter screen with login | Cars, My bookings, My account. No owner information. | `RenterView.java` and the renter tabs |
| Cars with rent, extra fees, picture, make, model, rented cars | Cards with price per day, free miles, extra-mile fee, picture and status. | `RenterBrowseTab.java` |
| Availability for a span of dates | Date pickers; each card says free, rented or reserved for those days. | `BookingService.java`, `Database.java` |
| Rent, Reserve, Undo | Buttons in the car details; My bookings can cancel a reservation. | `RenterBrowseTab.java`, `BookingService.java` |
| Same style, in sync, no bugs | One style sheet; screens refresh every 3 seconds; two windows cannot double-book; window checked from 860 x 560 up to 2560 x 1400 and maximized; 452 tests. | `fleetrent.css`, `SyncPoller.java`, `Database.java` |
| ERD and design document | Sections 3.3 to 3.6 (accounts, booking rules, the two screens and a list of every class) and the ERD with all eleven tables. | `docs/design-document.md` |

## Second review: the finished app

The instructor checked the finished app and listed what needed fixing. This table says what we did.

| Point | What we did | Where |
|---|---|---|
| Remove week and milestone wording, week-numbered PDFs, the `Contributions` folder | The design document has no week or milestone wording. There is one current PDF, `Team04_Project_Design_Document.pdf`. The old PDFs and the `Contributions` folder are deleted, and the proposal PDF has no week number. | `docs/` |
| Sections 8 and 9 were empty | Section 8 (Testing Summary) and Section 9 (Individual Contributions) are written. | `docs/design-document.md` |
| Preliminary and final ERD looked the same | Figure 1 is named an early draft (names and keys only), its Mermaid types now match the schema, and the final ERD has eleven tables with types, keys and crow's foot marks. The repeated "1 to M" sentence is gone from Section 4.1. | `docs/design-document.md` |
| Figure numbers jump, table headings break the outline | Figures are numbered 1 to 8 in order and every table caption is bold text, not a heading. | `docs/design-document.md` |
| Sections 5, 6 and 7 were dense paragraphs | They are split into short subsections with tables. | `docs/design-document.md` |
| Section 5 should mention the Wikipedia photo search | Section 5.3 and Tables 12, 14 and 15 describe it. | `docs/design-document.md` |
| Add a unique plate rule | A unique index in the database, a `DuplicatePlateException`, and `DuplicatePlateTest`. | `schema.sql`, `Database.java`, `DuplicatePlateException.java` |
| Use one shared executor | `UiHelper.runInBackground` hands the work to `Background`, one pool of 4 daemon threads, stopped in `App.stop`. A test checks that many jobs use only the pool threads. | `UiHelper.java`, `App.java`, `DuplicatePlateTest.java` |
| Catching `Exception` in `ImportTab` | It catches `IOException` and `SQLException`. | `ImportTab.java` |
| Javadoc and long lines | Every class and public method has a Javadoc comment, and no line in `src/main` or `src/test` is longer than 100 characters. | `src/main/java/fleetrent/` |
| Layered packages with controllers; no `SQLException` in the tabs | Done. The code is in the packages `model`, `dto`, `dao`, `service`, `controller`, `ui` and `app`. The tabs call controllers; no screen imports `Database`, `java.sql`, an API client or a thread. | `src/main/java/fleetrent/controller/`, Section 3 of the design document |
| Cancel for the 50,000-row import | Not done. It is listed in `docs/known-issues.md` (item 30). | |

## Third review: the extra changes

The instructor sent a short list of extra changes. This table says what we did.

| Point | What we did | Where |
|---|---|---|
| Add a Payment or Charge table (rental, amount, date, late fee) | New table `Payment`. When a rental is returned, the charge and the late fee are saved in the same transaction as the return. The Rentals tab shows what was paid. The late fee is 10 percent of the daily rate for each day after the end date; this rule is our own and can be changed in `RentalMath.LATE_FEE_RATE`. | `schema.sql`, `Database.saveReturn`, `RentalService.returnRental`, `Payment.java` |
| Add a Maintenance table (vehicle, date, type, cost) | New table `Maintenance` and a Maintenance window in the Vehicles tab. A PMS record moves the vehicle's last PMS date forward. | `schema.sql`, `Database.insertMaintenance`, `VehicleTab.java`, `Maintenance.java` |
| About 94 numbers written in the code | Sizes, gaps and delays in the screens, the limits in the services (name, username and password lengths, time-outs, HTTP 200, file columns) and the late fee are now named constants. The spacing values are in one class, `Layout`. The coordinates that draw a car in `CarGraphic` and the rows of the demo vehicles stay as numbers because they are the picture and the sample data themselves. | `ui/Layout.java`, the `static final` fields of each class |
| The waiting list and the checkout history are lost on restart | New tables `WaitingEntry` and `CheckoutLog`. The Queue and the Stack are rebuilt from them. A test closes and opens the database and checks that the line and the undo are still there. | `Database.java`, `RentalService.java`, `DatabasePersistenceTest` |
| Show the signed-in staff name in the header; only the manager may delete vehicles | The header shows the name and the role. Remove and Restore (and recording maintenance) are refused by `VehicleController` for any account that is not the owner. The owner is the manager. We have no separate clerk role, so every non-owner account counts as a clerk in this rule. | `Chrome.java`, `Session.java`, `VehicleController.java` |
| Tests: a clerk cannot delete a vehicle; a wrong password is refused | `OwnerOnlyTest` (10 tests) uses a renter account for the "clerk" and also tests a signed-out window, a wrong password and an unknown user. | `OwnerOnlyTest.java` |
| Missing tests for Rental, Vehicle, Renter, WaitingList, CheckoutHistory and the vehicle subclasses | New `RentalTest`, `RenterTest`, `VehicleSubclassTest`, `WaitingListCheckoutHistoryTest` and `PaymentAndMaintenanceTest`. | `src/test/java/fleetrent/model/` |
| `MainTest` has 1 test; the database tests have 5 failure cases; add a corrupt file and a locked file test | `MainTest` has 4 tests. `DatabaseFileFailureTest` has 4 tests: a file that is not a database, a damaged copy of a database, a folder that does not exist, and a locked file. | `MainTest.java`, `DatabaseFileFailureTest.java` |
