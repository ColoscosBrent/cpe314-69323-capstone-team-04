# cpe314-69323-capstone-team-04

## Project Title: FleetRent

Vehicle Rental & Fleet Management — CPE 314 capstone (Theme Q)

FleetRent is a JavaFX desktop app that helps small shops renting out cars, SUVs, and motorcycles replace their paper logbooks, which make it easy to lose track of which vehicle is out, its mileage, and who is next in line. It stores vehicles, renters, and rentals together in a single SQLite database, so staff can check vehicles out and in, look up rental history by renter or vehicle, and undo the last check-out if they make a mistake. When a vehicle is already rented, the app keeps a waiting list so the next customer is served in order, and it pulls verified make and model data from the NHTSA vPIC API instead of relying on typed-in entries. A dashboard then shows the most rented vehicle type and how busy the fleet is over time, giving owners a clear, organized view of their business.

There are two screens in one app: the **rental owner** runs the fleet, and a **renter** books vehicles from a separate screen that shows only what a renter needs. Both log in with a username and password and read the same database, so they stay in sync.

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
2. Run the tests: `mvn test` (452 tests, all should pass; on a computer with no display the 20 screen tests are skipped).
3. Start the app: in VS Code open `src/main/java/fleetrent/app/Main.java` and press Run above `main`.
4. The app creates `fleetrent.db` in the project folder the first time it starts. This file is not committed.
5. The first screen is the login, on a photo, with moving cars. Choose **Log in as Renter** or **Log in as Owner** first. Create an account with **Create an account**: choose **I own the rental shop** (open to every shop owner, no code) or **I want to rent**. Every account gives a last, first and middle name and a phone number with its country; a renter also gives an email and an address (street, city, country). The login says whether the username or the password is wrong. Forgot your password? Use **Forgot password** and answer your security question.
6. To see both screens together, press **New window** in the header and log in as the other role in the new window. A booking made by the renter shows for the owner within about 3 seconds.

## What the app does

- **Vehicles tab (owner):** Edit changes a saved vehicle (photo, fees, plate, and so on). Remove hides a broken vehicle from the fleet and from renters (Removed vehicles lists them and can Restore). Add vehicles with every field of the Vehicle ERD (type, make, model, year, color, plate, daily rate, mileage, last PMS, category). The Type decides which makes and models are loaded from the NHTSA API (runs in the background), and Make and Model are chosen from those lists, so they always match the type. The year is optional. NHTSA has no color data, so the color comes from a fixed list. Each vehicle shows a real photo: your own file (Choose photo...), or one found on Wikipedia, or a drawn car in its color. Photos are kept in `vehicle_images/` (not committed).
- **Rentals tab:** add renters with every Renter field (name, contact, email, address), start and return rentals, waiting list, undo last check-out. Ongoing and returned rentals show the vehicle's photo.
- **Input checks and errors:** each text box accepts only the right characters and shows a red message when the text is not valid. Clear messages appear when NHTSA, Wikipedia or the database cannot be reached.
- **Reservations tab (owner):** see every reservation, Hand over a vehicle on its start day (it becomes a rental) or Cancel it.
- **Renter screen:** Cars (picture, make, model, daily rent, extra-mile fee, free or busy on the chosen dates, Rent today, Reserve these days, Undo last), My bookings (cancel a reservation), My account (details and change password). Renters never see owner data.
- **Import tab:** load many rentals from a CSV file with a progress bar (runs in the background).
- **Dashboard tab:** pick a question (most rented vehicle type, fleet utilization per month, revenue, rentals per month, top models, NHTSA models per make) and a date range to see mined results in a bar chart or a line chart.

More documents: `docs/design-document.md` (and the PDF), `docs/known-issues.md`, `docs/requirements-map.md`, `docs/review-response.md`, `docs/manual-test-checklist.md`.
