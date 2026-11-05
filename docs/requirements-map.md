# Requirement-to-Code Map (Milestone 2)

| # | Mandatory requirement | Where it lives |
|---|---|---|
| 1 | Object-Oriented Design | Abstract class `Vehicle` with subclasses `Sedan`, `Suv`, `Motorcycle` (each overrides `computeRentalCost`); `VehicleFactory` builds the right one. Single Responsibility: `Database` (saving), `RentalService` (rental rules), `VpicClient` (API), `FleetAnalytics` (mining), one class per screen tab. |
| 2 | Data Structure / Recursion | Queue: `WaitingList`. Stack: `CheckoutHistory`. Map: one `WaitingList` per vehicle in `RentalService`. Recursion: `CategoryNode.totalMileage()` and `CategoryNode.toText()` (shown under the chart on the Dashboard tab). |
| 3 | Automated Testing | `src/test/java/fleetrent/` - 124 JUnit 5 tests, run with `mvn test`. |
| 4 | Data Modeling + Database | `src/main/resources/schema.sql` (Renter, Vehicle, Rental, with foreign keys) and `Database.java` (JDBC, every query with values uses `PreparedStatement`). ERD in `docs/erd.png`. |
| 5 | API Consumption | `VpicClient.java` (HTTP call and JSON parsing) and `VehicleModelInfo.java`. `VehicleTypes` maps our types to NHTSA types. Used in `VehicleTab` (make and model lists chosen by type) and `DashboardTab` (live question). Extra: `CarImageClient`, `CarImageStore`, `PhotoFinder` fetch and keep car photos. |
| 6 | Event-Driven GUI | `App.java` (tabs) with `VehicleTab`, `RentalTab`, `ImportTab`, `DashboardTab`: buttons, choice boxes, date pickers, text fields and lists, each with an event handler. The forms follow the ERD: Renter (name, contact, email, address), Vehicle (all fields) and Rental (renter, vehicle, dates, return date, miles, status). `Validators` and `ValidatedField` allow only the right characters in each box and show an error message; `ErrorMessages` explains API and database problems; `VehiclePictures` shows a vehicle photo in both the Vehicles and Rentals tabs. `CarGraphic` and `ColorNames` draw each vehicle in its real color. |
| 7 | Concurrency | JavaFX `Task` on a background thread: `VehicleTab.loadMakes`, `VehicleTab.loadModels`, `VehicleTab.afterAdd` and `findAllPhotos` (photos), `ImportTab.runImport`, `DashboardTab.showLive`; helper `UiHelper.runInBackground`. |
| 8 | Data Mining | `FleetAnalytics.java` (`rentalsByType`, `utilizationByMonth`, `revenueByType`, `rentalsPerMonth`, `topModels`, `averageDays`, `summary`). |
| 9 | Data Visualization | `DashboardTab.java`: `BarChart` (groups) and `LineChart` (over time) with title and axis labels, rebuilt from the mining result each time. |
