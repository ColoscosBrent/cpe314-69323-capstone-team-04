# Requirement-to-Code Map

| # | Mandatory requirement | Where it lives |
|---|---|---|
| 1 | Object-Oriented Design | Abstract class `Vehicle` with subclasses `Sedan`, `Suv`, `Motorcycle` (each overrides `computeRentalCost`); `VehicleFactory` builds the right one. Single Responsibility: `Database` (saving), `RentalService` (rental rules), `VpicClient` (API), `FleetAnalytics` (mining), `AuthService` (accounts), `BookingService` (renter bookings), `FleetService` (remove and edit), `PasswordHasher`, one class per screen tab. |
| 2 | Data Structure / Recursion | Queue: `WaitingList`. Stack: `CheckoutHistory`. Map: one `WaitingList` per vehicle in `RentalService` (saved in `WaitingEntry`). Recursion: `CategoryNode.totalMileage()` and `CategoryNode.toText()` (shown under the chart on the Dashboard tab). |
| 3 | Automated Testing | `src/test/java/fleetrent/` - 452 JUnit 5 tests in 49 classes, including `ScreenFlowTest` (clicks through the screens and checks the window at five sizes), run with `mvn test`. |
| 4 | Data Modeling + Database | `src/main/resources/schema.sql` (eleven tables: Renter, Vehicle, Rental, Account, Reservation, Payment, Maintenance, WaitingEntry, CheckoutLog, ImportBatch, ImportRecord, with foreign keys) and `Database.java` (JDBC, every query with values uses `PreparedStatement`). ERD in `docs/erd.png`. |
| 5 | API Consumption | `VpicClient.java` (HTTP call and JSON parsing) and `VehicleModelInfo.java`. `VehicleTypes` maps our types to NHTSA types. Used in `VehicleTab` (make and model lists chosen by type) and `DashboardTab` (live question). Extra: `CarImageClient`, `CarImageStore`, `PhotoFinder` fetch and keep car photos. |
| 6 | Event-Driven GUI | `AppWindow`, `AuthScreen` (log in, sign up, forgot password), `AdminView` (`VehicleTab`, `RentalTab`, `ReservationTab`, `ImportTab`, `DashboardTab`) and `RenterView` (`RenterBrowseTab`, `RenterBookingsTab`, `RenterAccountTab`), `SyncPoller`: buttons, choice boxes, date pickers, text fields and lists, each with an event handler. The forms follow the ERD: Renter (name, contact, email, address), Vehicle (all fields) and Rental (renter, vehicle, dates, return date, miles, status). `Validators` and `ValidatedField` allow only the right characters in each box and show an error message; `ErrorMessages` explains API and database problems; `VehiclePictures` shows a vehicle photo in both the Vehicles and Rentals tabs. `CarGraphic` and `ColorNames` draw each vehicle in its real color. |
| 7 | Concurrency | JavaFX `Task` on a background thread: `VehicleTab.loadMakes`, `VehicleTab.loadModels`, `VehicleTab.afterAdd` and `findAllPhotos` (photos), `ImportTab.runImport`, `DashboardTab.showLive`; helper `UiHelper.runInBackground`, which hands the work to `controller.Background` (one shared pool). |
| 8 | Data Mining | `FleetAnalytics.java` (`rentalsByType`, `utilizationByMonth`, `revenueByType`, `rentalsPerMonth`, `topModels`, `averageDays`, `summary`). |
| 9 | Data Visualization | `DashboardTab.java`: `BarChart` (groups) and `LineChart` (over time) with title and axis labels, rebuilt from the mining result each time. |

## Owner and renter requests

| Request | Where it lives |
|---|---|
| Remove a broken vehicle (owner only) | `VehicleTab` Remove button, `VehicleController.remove` (checks `Session`), `FleetService.removeVehicle`, `Database.removeVehicle` (`Vehicle.active`). Restore: Removed vehicles dialog. |
| Edit a vehicle (photo, fees, and so on) | `VehicleTab` Edit button and edit mode, `FleetService.updateVehicle`. |
| Make and model bug | `VehicleTab` remembers the clicked make and model (`pickedMake`, `pickedModel`); `ValidatedField.setValue`; `Validators.sameName`. |
| Owner sign up, log in, forgot password, sign out | `AuthScreen`, `AuthService`, `PasswordHasher`, `Chrome` (Sign out). |
| Renter screen with its own login | `RenterView`, `RenterBrowseTab`, `RenterBookingsTab`, `RenterAccountTab`. Shows only what a renter should see. |
| Cars with rent and extra fees, picture, make, model, rented cars | `BookingService.browse`, `Vehicle.getFreeMilesPerDay`, `getFeePerExtraMile`, `BusySpan`. |
| Availability on a span of dates | `Database.isVehicleFree`, `getBusySpans`. |
| Rent, Reserve, Undo | `BookingService.rentNow`, `reserve`, `undoLast`; the buttons in `RenterBrowseTab`. |
| Renter and owner in sync | `SyncPoller`, `Database.changeStamp`, `insertRentalIfFree`, `insertReservationIfFree`. |
| Same style for both screens | `fleetrent.css` and `Chrome`. |
| Payment of a returned rental | `Payment`, table `Payment`, `Database.saveReturn`, `RentalService.returnRental`, `RentalMath.lateFee`. |
| Maintenance of a vehicle | `Maintenance`, table `Maintenance`, `VehicleController.logMaintenance`, Maintenance window in `VehicleTab`. |
| Waiting list and undo kept after a restart | Tables `WaitingEntry` and `CheckoutLog`, `RentalService`. |
| Layered packages | `model`, `dto`, `dao`, `service`, `controller`, `ui`, `app` (Section 3 of the design document). |
