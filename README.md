# cpe314-69323-capstone-team-04

## Project Title: FleetRent

Vehicle Rental & Fleet Management — CPE 314 capstone (Theme Q)

FleetRent is a JavaFX desktop app that helps small shops renting out cars, SUVs, and motorcycles replace their paper logbooks, which make it easy to lose track of which vehicle is out, its mileage, and who is next in line. It stores vehicles, renters, and rentals together in a single SQLite database, so staff can check vehicles out and in, look up rental history by renter or vehicle, and undo the last check-out if they make a mistake. When a vehicle is already rented, the app keeps a waiting list so the next customer is served in order, and it pulls verified make and model data from the NHTSA vPIC API instead of relying on typed-in entries. A dashboard then shows the most rented vehicle type and how busy the fleet is over time, giving owners a clear, organized view of their business.

## Team Members

- Gabasa, Dexter E.
- Rosal, Wendil C.
- Coloscos, Brent
- Lagaya, John Carlo Stephen
- Labra, Simone Joaquin S.

## API

NHTSA vPIC API (https://vpic.nhtsa.dot.gov/api)

## How to run

1. Install JDK 17 or newer and Maven (see the setup guide).
2. Run the tests: `mvn test` (118 tests, all should pass).
3. Start the app: in VS Code open `src/main/java/fleetrent/Main.java` and press Run above `main`.
4. The app creates `fleetrent.db` in the project folder the first time it starts. This file is not committed.

## What the app does (Milestone 2)

- **Vehicles tab:** add vehicles with every field of the Vehicle ERD (type, make, model, year, color, plate, daily rate, mileage, last PMS, category). The Type decides which makes and models are loaded from the NHTSA API (runs in the background), and Make and Model are chosen from those lists, so they always match the type. The year is optional. NHTSA has no color data, so the color comes from a fixed list. Each vehicle shows a real photo: your own file (Choose photo...), or one found on Wikipedia, or a drawn car in its color. Photos are kept in `vehicle_images/` (not committed).
- **Rentals tab:** add renters with every Renter field (name, contact, email, address), start and return rentals, waiting list, undo last check-out. Ongoing and returned rentals show the vehicle's photo.
- **Input checks and errors:** each text box accepts only the right characters and shows a red message when the text is not valid. Clear messages appear when NHTSA, Wikipedia or the database cannot be reached.
- **Import tab:** load many rentals from a CSV file with a progress bar (runs in the background).
- **Dashboard tab:** pick a question (most rented vehicle type, fleet utilization per month, revenue, rentals per month, top models, NHTSA models per make) and a date range to see mined results in a bar chart or a line chart.

More documents: `docs/design-document.md`, `docs/known-issues.md`, `docs/requirements-map.md`.
