Group Name / No.: Group 4

Members:
- Gabasa, Dexter E.
- 
- Coloscos, Brent
- Lagaya, John Carlo Stephen
- Labra, Simone Joaquin S.

Repository URL: https://github.com/ColoscosBrent/cpe314-69323-capstone-team-04

Chosen Theme: Q — Vehicle Rental & Fleet Management

API You Will Use: NHTSA vPIC API — https://vpic.nhtsa.dot.gov/api

---

### 1. One-Paragraph Pitch
FleetRent is a JavaFX desktop application designed for small vehicle rental businesses that manage cars, SUVs, and motorcycles. It helps staff manage vehicle and renter records, track rentals, monitor vehicle availability, and maintain a waiting list when vehicles are unavailable. The system stores data in an SQLite database and retrieves vehicle make, model, and year information through the NHTSA vPIC API. Its dashboard displays the most-rented vehicle type and fleet utilization over time, helping rental staff monitor fleet activity more efficiently.

### 2. Entity-Relationship Sketch
- **Renter**(id PK, name, contact)
- **Vehicle**(id PK, type, make, model, year, dailyRate, mileage, categoryPath)
- **Rental**(id PK, renterId FK, vehicleId FK, startDate, endDate, milesDriven)

**Relationships:** 
- Renter `--1:M-->` Rental (one renter can have many rentals) 
- Vehicle `--1:M-->` Rental (one vehicle can be in many rentals over time). 
- `type` is one of `SEDAN | SUV | MOTORCYCLE`.

**Summary:** We have 3 entities (Renter, Vehicle, Rental), all saved in SQLite using JDBC with PreparedStatement. The waiting-list Queue and the undo Stack are only in memory, not tables. `categoryPath` is a text like "Car > Sedan > Compact Sedan" that we use to build the category tree.

### 3. Feature Checklist
| Planned Feature | Requirement(s) It Satisfies |
| :--- | :--- |
| Vehicle abstract class with Sedan, SUV, and Motorcycle subclasses. Each has its own daily rate and mileage fee rules. | Object-Oriented Design |
| A Queue for the reservation waiting list and a Stack of check-outs so we can undo the last one. | Data Structure (Queue and Stack) |
| Vehicle category tree (Car > Sedan > Compact Sedan). A recursive method adds up the total fleet mileage. | Recursion |
| SQLite tables for Renter, Vehicle, and Rental, accessed with JDBC PreparedStatement. | Data Modeling + Database |
| Get make, model, and year data from the NHTSA vPIC API, parse the JSON, and use it when adding a vehicle. | API Consumption |
| Import the old rental history (CSV) using a JavaFX Task on a background thread, so the window does not freeze. | Concurrency |
| GUI with an add-vehicle form, check-out and check-in buttons, an undo button, an import button, and a dashboard tab. | Event-Driven GUI (3+ controls with handlers) |
| Dashboard: find the most rented vehicle type and fleet utilization over time. Shown as a bar chart and a line chart with titles and axis labels. | Data Mining + Data Visualization |
| At least 6 JUnit 5 tests: rate and fee of each vehicle type, queue order, stack undo, recursive mileage (including an empty tree), and CSV edge cases. | Automated Testing |

### 4. Task Split
| Member | Primary Responsibility (this milestone) |
| :--- | :--- |
| Gabasa, Dexter | Database: SQLite tables for vehicles, renters, and rentals, and the JDBC code with PreparedStatement. Database tests. |
| Rosal, Wendil | Vehicle category tree and the recursive fleet mileage method. Maven project setup. Tests for these. |
| Coloscos, Brent | Domain model and OOD: Vehicle abstract class, its 3 subclasses, and the rate and fee logic. Tests for these. |
| Lagaya, John Carlo | Rental and Renter classes, the waiting list Queue, the undo Stack, and the CSV import on a background thread. Tests for these. |
| Labra, Simone | NHTSA vPIC API: sending the request and parsing the JSON. JavaFX GUI (forms, buttons, dashboard) and the bar and line charts. |
| All members | Commit our own work to the team repo, review each other's pull requests, and attend the Week 8-9 consultation. |

### 5. Public API You Plan to Use
- **NHTSA vPIC API** (`https://vpic.nhtsa.dot.gov/api`). It gives real vehicle make, model, and year data from the US government in JSON. No API key needed.
- **Example call:** `https://vpic.nhtsa.dot.gov/api/vehicles/GetModelsForMake/honda?format=json`
- We will cache the response so we do not call the API too much.

### 6. Risks / Open Questions
- How will we keep the window responsive while importing a large rental history CSV?
- Should the category tree be saved as a text path in the Vehicle table or in its own table?
- How will we read the JSON from the API in Java, and is it OK to use a library like Gson?
- What should the app show when the API is slow or down?
- How will we count utilization over time correctly when rentals overlap?
