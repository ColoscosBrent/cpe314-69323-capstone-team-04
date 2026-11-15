# Response to the Instructor's Reviews

This page lists each point from the instructor's reviews, what we changed, and where. The first review is about the proposal and the first version of the app; the later ones are about the finished app and its design document.

## First review: to-do list

| # | Instructor's point | What we changed | Where | Member |
|---|---|---|---|---|
| 1 | Add a DB-backed test for `insertRental` and the rental list, with a rental overlap case | New class `DatabaseRentalTest` with 3 tests: a vehicle is free again after its rental is returned; an overlapping rental for a vehicle that is out is not saved (the renter waits instead); a returned vehicle can be rented again. `DatabaseTest.rentalIsSavedAndFoundByRenter` already covered saving and listing. | `src/test/java/fleetrent/DatabaseRentalTest.java` | Gabasa, Dexter |
| 1 | (waiting list around overlap) | Two more tests: waiting renters are served in the order they joined; a waiting list belongs to one vehicle only. | `RentalServiceTest.java` | Lagaya, John Carlo |
| 2 | Handle `IllegalArgumentException` in the Add Vehicle handler | The handler now catches it and shows a red message. The form also stops blank makes and a daily rate of 0 or less with red messages before the handler runs. | `VehicleTab.java`, method `addVehicle` | Labra, Simone |
| 3 | Add a second GUI control that makes the Queue and Stack visible | The Rentals tab has **Start rental** (if the vehicle is out, the renter joins the `WaitingList` and the screen shows how many are waiting) and **Undo last check-out** (the `CheckoutHistory` stack). | `RentalTab.java`, `RentalService.java` | Lagaya, John Carlo; Labra, Simone |
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
| Fewest tests (16) | 509 JUnit 5 tests in 58 classes, all passing (16 at the first review, 129 after its fixes). They include edge cases, booking races between windows and a click-through of the screens. |
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
| Same style, in sync, no bugs | One style sheet; screens refresh every 3 seconds; two windows cannot double-book; window checked from 860 x 620 up to 2560 x 1400 and maximized; 509 tests. | `fleetrent.css`, `SyncPoller.java`, `Database.java` |
| ERD and design document | Sections 3.3 to 3.6 (accounts, booking rules, the two screens and a list of every class) and the ERD with all eleven tables. | `docs/design-document.md` |

## Second review: the finished app

The instructor checked the finished app and listed what needed fixing. This table says what we did.

| Point | What we did | Where |
|---|---|---|
| Remove week and milestone wording, week-numbered PDFs, the `Contributions` folder | The design document has no week or milestone wording. There is one current PDF, `Team04_Project_Design_Document.pdf`. The old PDFs and the `Contributions` folder are deleted, and the proposal PDF has no week number. | `docs/` |
| Sections 8 and 9 were empty | Section 8 (Testing Summary) and Section 9 (Individual Contributions) are written. | `docs/design-document.md` |
| Preliminary and final ERD looked the same | Figure 1 is named an early draft (names and keys only), its Mermaid types now match the schema, and the final ERD has eleven tables with types, keys and crow's foot marks on each line (with a legend). The repeated "1 to M" sentence is gone from Section 4.1. | `docs/design-document.md` |
| Figure numbers jump, table headings break the outline | Figures are numbered 1 to 8 in order and every table caption is bold text, not a heading. | `docs/design-document.md` |
| Sections 5, 6 and 7 were dense paragraphs | They are split into short subsections with tables. | `docs/design-document.md` |
| Section 5 should mention the Wikipedia photo search | Section 5.3 and Tables 12, 14 and 15 describe it. | `docs/design-document.md` |
| Add a unique plate rule | A unique index in the database, a `DuplicatePlateException`, and `DuplicatePlateTest`. | `schema.sql`, `Database.java`, `DuplicatePlateException.java` |
| Use one shared executor | Every background Task goes to `Background`, one pool of 4 daemon threads, stopped in `App.stop`. A test checks that many jobs use only the pool threads. | `Background.java`, `TaskRunner.java`, `App.java`, `DuplicatePlateTest.java` |
| Catching `Exception` in `ImportTab` | The tab catches only `ControllerException`; the controller turns `IOException` and `SQLException` into plain messages. | `ImportTab.java`, `ImportController.java`, `Guard.java` |
| Javadoc and long lines | Every public class, constructor, method and constant has a Javadoc comment (a method that overrides another uses the comment of the method it overrides), and no line in `src/main` or `src/test` is longer than 100 characters. | `src/main/java/fleetrent/` |
| Layered packages with controllers; no `SQLException` in the tabs | Done. The code is in the packages `model`, `dto`, `dao`, `service`, `controller`, `ui` and `app`. The tabs call controllers; no screen imports `Database`, `java.sql`, an API client or a thread. | `src/main/java/fleetrent/controller/`, Section 3 of the design document |
| Cancel for the 50,000-row import | Done in the fourth review: the Import tab has a Cancel import button. | `ImportTab.java`, `ImportController.java`, `RentalCsvImporter.java` |

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

## Fourth review: the finished app, second check

The instructor checked the code again (commit `5ed8d55`) and sent a list with five parts. The tables below follow that list point by point.

### Corrections that were "Partial" or "Not applied"

| # | Point | What we did | Where |
|---|---|---|---|
| 5 | ERD still used the letters 1 and M; this page wrongly said crow's foot | The final ERD (Figure 8) is drawn again with crow's foot marks and a legend: exactly one, zero or one, zero or many. Section 4.1 explains the marks. This page now says the same. | `docs/erd.png`, Section 4.1 |
| 10 | One `Database` class for 11 tables | `BaseDao` and one DAO per table (`VehicleDao`, `RentalDao`, ...). `Database` keeps the connection, the schema and the multi-table transactions, and calls the DAOs. | `dao/` |
| 14 | No Cancel for the 50,000-row import | The Import tab has a Cancel import button. The rows saved so far stay and the history shows the real counts. | `ImportController.cancelImport`, `ImportTab.java`, `RentalCsvImporter.java` |
| 15 | DTOs, a tested mapper and saved JSON answers | `VpicResponse`, `VehicleMakeDto`, `VehicleModelDto`, `WikipediaResponse`, `WikipediaPageDto`, `CommonsFileDto`, and `ApiMapper`. `ApiMapperTest` reads saved answers from `src/test/resources`. | `dto/`, `service/ApiMapper.java` |
| 16 | `VehicleFactory` took plain strings | A new `create` takes a `VehicleModelInfo` and a type; the old one calls it. | `model/VehicleFactory.java` |
| 17 | Controllers with an observable list, selected item, status and busy; the controller runs the Task | Done. Every screen controller extends `ObservableController`: `items()`, `selectedProperty()`, `statusProperty()`, `busyProperty()`. The controller builds and runs the JavaFX `Task` (`runTask`, `TaskRunner`); no tab creates a `Task` or a thread any more. `ObservableStateTest` checks it. | `controller/ObservableController.java`, `controller/TaskRunner.java`, every controller |
| 19 | `processedBy` on every rental, with a test | New column `Rental.processedBy`, written at check-out and at hand-over. `SessionRulesTest` and `DaoClassesTest` check it. | `schema.sql`, `RentalService.java`, `RentalDao.java` |
| 21 | Renter sees no waiting-list position and has no join button | The Cars tab has a Join waiting list button and says the renter's number in the line. My bookings lists the renter's waiting lists with the position in each, and a Leave selected waiting list button. | `RenterBrowseTab.java`, `RenterBookingsTab.java`, `BookingService.java` |
| 22 | Role checks missing for add, edit, rent, return, hand over, cancel; renter id taken from the screen | Every owner action calls `Session.requireOwner`, and `RenterController` reads the renter id from the session. `SessionRulesTest` (10 tests). | `controller/` |
| 27 | Numbers left in `AppWindow` (1320, 800, 28) and `VehicleTab` (460, 260) | They are named in `Layout` (`WINDOW_WIDTH`, `WINDOW_HEIGHT`, ...) and in the classes that use them. The picture coordinates of `CarGraphic` and the demo rows of `SampleData` stay (known issue 34). | `ui/Layout.java` |
| 32 | A public constructor without Javadoc; line length not checked | Every public class, constructor, method and constant has Javadoc. No line is over 100 characters (checked with a script over `src/main` and `src/test`). | `src/` |
| 33 | Four methods of 53 to 59 lines | They are split. No method in `src/main` is longer than 50 lines. | `ui/` |
| 36 | Live NHTSA and Wikipedia calls not tried | The tests use a fake server, so this cannot be shown by a test. The team tries the live calls on the presentation computer before the defense (known issues 10 and 16). | `docs/known-issues.md` |
| 12 (note) | The unique plate index was skipped silently on an old file | A warning is logged and the owner sees a one-time message that duplicate plates must be fixed. | `Database.java`, `AppWindow.java` |

### Week 11 Checklist and Design Document Checklist

| Point | What we did | Where |
|---|---|---|
| No `javafx:run` in `pom.xml` | Added `javafx-maven-plugin`; `mvn javafx:run` starts the app. | `pom.xml` |
| No Cancel during the import; the same file can be imported twice | Cancel import button. The app keeps a SHA-256 of every imported file and refuses the same file again; a rental that is already saved is skipped. | `ImportController.java`, `RentalCsvImporter.java` |
| `VehicleTab` (1,245 lines) does too many jobs; two files over 800 lines | `VehicleTab` is now the form only. The fleet list (`FleetListCard`), the NHTSA lists (`NhtsaPicker`), the photo jobs (`PhotoJobs`), the Maintenance window and the Removed vehicles window are their own classes. `Database` is split into DAOs. The longest file has 710 lines. | `ui/`, `dao/` |
| The 3-second poller and the tab refresh read the database on the JavaFX thread | The poller asks through `AppController.changes`, and every tab refreshes through its controller; both run as background Tasks. | `SyncPoller.java`, `controller/` |
| Table of contents lists only top-level headings | It lists every sub-section with its page. | Design document PDF, page ii |
| Revision history rows out of date order | The rows are in date order, one row for each author and version. | Design document |
| No defense deck in the repository | `docs/FleetRent_Final_Defense.pptx`, made from the template. | `docs/` |

### Code bugs

| # | Bug | What we did | Where |
|---|---|---|---|
| 1 | Renter Undo carries over to the next person | The undo is cleared when a session starts or ends. | `AuthController.java`, `AppController.java` |
| 2 | Owner-only rules missing for add, edit, rent and return | See correction 22. | `controller/` |
| 3 | Database work on the JavaFX thread can freeze the window during an import | Reads and the change check run as background Tasks of the controllers. | `controller/`, `SyncPoller.java` |
| 4 | A failed import leaves half the rows and an unfinished history record | The history keeps the real counts, and the message says how many rows were saved. | `RentalCsvImporter.java` |
| 5 | Unique plate index failure ignored | See correction 12 (note). | `Database.java` |
| 6 | The window closes the database while background jobs may still use it | A closing window cancels its jobs and waits for them (at most 4 seconds) before it closes the connection. | `AppWindow.java`, `TaskRunner.java` |
| 7 | Week and milestone wording in code comments | Renamed to topic headers. | `dao/` |
| 8 | Two files over 800 lines | See the checklist table above. | `dao/`, `ui/` |

### UI issues

| Issue | What we did | Where |
|---|---|---|
| The empty-login message pushes the Log in button down | The error line keeps its space, so nothing moves. | `AuthScreen.java` |
| After an owner account is created the form goes back to "Log in as Renter" | The login form follows the role of the new account. | `AuthScreen.java` |
| The security question box is empty | It says "Choose a question". | `AuthScreen.java` |
| The New renter form has two kinds of labels | One grid with one label for each field. | `RentalTab.java`, `NameFields.java`, `AddressFields.java` |
| Mixed date formats; big numbers with no separator or currency | `Format` writes every date (15 Nov 2026), money (with the peso sign) and number (1,500) the same way. | `ui/Format.java` |
| The Last PMS box is narrower and the Clear link floats | The date box and Clear are one row as wide as the other fields. | `VehicleTab.java` |
| Dashboard bars of almost the same height; long yellow summary | The y-axis does not have to start at zero, so the differences show, and the summary is one normal line. | `DashboardTab.java` |
| Mixed button naming | Sentence case everywhere: "Add renter", "Start rental", "Return vehicle", "Add vehicle". | `ui/` |
| Hard-coded window size and offsets; fixed list size in a dialog | Named constants in `Layout`; the list grows with the dialog. | `Layout.java`, `RemovedVehiclesWindow.java` |
| Inline style instead of the style sheet | A style class in `fleetrent.css`. | `AuthScreen.java`, `fleetrent.css` |
| Small window needs scroll bars on several tabs | The window cannot be smaller than 860 x 620; below about 1100 pixels the wide tabs scroll (known issue 27). `ScreenLayoutTest` checks five sizes. | `AppWindow.java` |
| Raw system text can reach the user | A plain message is shown and the technical text is logged. | `Guard.java`, `ErrorMessages.java` |

## Changes after the fourth review

The team tried the app and asked for these changes.

| Request | What we did | Where |
|---|---|---|
| Dropdowns hard to read; plain window title bar | Every dropdown and menu has a solid background, and the title bar uses the app theme. | `fleetrent.css`, `ThemedWindow.java` |
| The logo instead of the diamond in the title bar | The title bar and the window icon show the FleetRent logo. | `ThemedWindow.java`, `src/main/resources/images/` |
| The photo should have the color chosen for the vehicle | The photo search first looks on Wikimedia Commons for a photo of the model in that color (by the file name or by the pixels in the middle of the picture) and only then takes the usual photo. The status line says whether the photo is in that color. | `PhotoFinder.java`, `CarImageClient.java`, `PhotoColor.java` |
| Category and transmission should follow the vehicle picked from the API | NHTSA sends only makes and model names. The app sets the category it knows for the picked model (`ModelGuide`) and the transmission list then fits that category. The owner can change both. | `ModelGuide.java`, `VehicleTab.java` |
| An email must be in the right format and not in capital letters | A capital letter turns small as it is typed, and the text must look like `name@example.com`. | `Validators.java`, `ValidatedField.java` |
| An export had about 300 rows for a 30-row file | "Export all records" writes every rental in the app, so it has more rows than one file. It now says so, the new "Export this import" writes only the rows of one file, and a rental that is already saved is never imported twice. | `ImportTab.java`, `ImportHistoryTab.java`, `ImportController.java`, `RentalCsvImporter.java` |
| Other faults we found | The utilization of a month that is cut by the dates or not over yet is divided by the days that count. The screen tests skip themselves on a computer with no display instead of waiting forever. | `FleetAnalytics.java`, `FxToolkit.java` (tests) |
