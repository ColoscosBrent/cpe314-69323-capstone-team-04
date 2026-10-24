# FleetRent: Vehicle Rental & Fleet Management System

## Project Design Document

CPE 314 - Software Design | University of Cebu Computer Engineering Department | Instructor: Richyll Son | First Semester, S.Y. 2026-2027

| Item                    | Details                                                                           |
| ----------------------- | --------------------------------------------------------------------------------- |
| Group Name / No.        | Group 4                                                                           |
| Members                 | Gabasa, Dexter; Rosal, Wendil; Coloscos, Brent; Lagaya, John Carlo; Labra, Simone |
| Repository URL          | https://github.com/ColoscosBrent/cpe314-69323-capstone-team-04                    |
| Instructor              | Richyll Son                                                                       |
| Term                    | First Semester, S.Y. 2026-2027                                                    |
| Document Version / Date | 0.1 / October 21, 2026                                                            |

## Revision History

# Revision History

**Table 1: Revision History of This Document**

| Version | Date | Author | Changes |
|---|---|---|---|
| 0.1 | October 21, 2026 | Team 4 | Initial draft — Week 8 sections |
| 0.2 | — | — | Week 9 sections added |
| 0.3 | — | — | Week 10 sections added |
| 1.0 | — | — | Final compilation and polish — Week 11 |
			


## 1. Introduction & Project Overview

FleetRent is a JavaFX desktop app that helps small shops renting out cars, SUVs, and motorcycles replace their paper logbooks, which make it easy to lose track of which vehicle is out, its mileage, and who is next in line. It stores vehicles, renters, and rentals together in a single SQLite database, so staff can check vehicles out and in, look up rental history by renter or vehicle, and undo the last check-out if they make a mistake. When a vehicle is already rented, the app keeps a waiting list so the next customer is served in order, and it pulls verified make and model data from the NHTSA vPIC API instead of relying on typed-in entries. A dashboard then shows the most rented vehicle type and how busy the fleet is over time, giving owners a clear, organized view of their business.



## 2. Preliminary ER Sketch & Team Roles

### 2.1 Preliminary ER sketch

                
| mileage      | -   | Total miles the vehicle has driven so far.                                            |
| categoryPath | -   | Category text such as "Car > Sedan > Compact Sedan", used to build the category tree. |

**Table 5: Rental entity (preliminary attributes)**

| Attribute   | Key | Description                            |
| ----------- | --- | -------------------------------------- |
| id          | PK  | Unique number for each rental.         |
| renterId    | FK  | Links to Renter.id (who rented).       |
| vehicleId   | FK  | Links to Vehicle.id (what was rented). |
| startDate   | -   | Day the vehicle was taken.             |
| endDate     | -   | Day the vehicle was returned.          |
| milesDriven | -   | Miles driven during this rental.       |

**Relationships:** One Renter can have many Rentals (1:M), and one Vehicle can be in many Rentals over time (1:M). The Rental table connects a renter to a vehicle. The Queue (waiting list) and the Stack (undo) are kept in memory only, so they are not tables. categoryPath is a text such as "Car > Sedan > Compact Sedan" that we use to build the vehicle category tree in our code.

```text
+---------------+          +------------------+          +----------------+
|     Renter    |          |      Rental      |          |    Vehicle     |
+---------------+          +------------------+          +----------------+
| id (PK)       |          | id (PK)          |          | id (PK)        |
| name          |1--------M| renterId (FK)    |          | type           |
| contact       |          | vehicleId (FK)   |M--------1| make           |
+---------------+          | startDate        |          | model          |
                           | endDate          |          | year           |
                           | milesDriven      |          | dailyRate      |
                           +------------------+          | mileage        |
                                                         | categoryPath   |
                                                         +----------------+
```

*Figure 1: Preliminary ER sketch of FleetRent (Renter, Rental, Vehicle)*

**Team roster and primary roles:** Each member has one main job for Milestone 1, the same as in our project proposal.


### 2.2 Team roster and primary roles

**Table 6: Team roster and primary roles (Group 4)**

| Member (GitHub username)                  | Primary role / responsibility                                                                                                  |
| ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| Gabasa, Dexter (gabasad84-afk)            | Database: SQLite tables for vehicles, renters, and rentals, and the JDBC code with PreparedStatement. Database tests.          |
| Rosal, Wendil (wendilrosalx22)            | Vehicle category tree and the recursive fleet mileage method. Maven project setup. Tests for these.                            |
| Coloscos, Brent (ColoscosBrent)           | Domain model and OOD: Vehicle abstract class, its 3 subclasses, and the rate and fee logic. Tests for these.                   |
| Lagaya, John Carlo (caloylagaya2k4-pixel) | Rental and Renter classes, the waiting list Queue, the undo Stack, and the CSV import on a background thread. Tests for these. |
| Labra, Simone (simonelabra21)             | NHTSA vPIC API: sending the request and parsing the JSON. JavaFX GUI (forms, buttons, dashboard) and the bar and line charts.  |
| All members                               | Commit our own work to the team repo, review each other's pull requests, and attend the Week 8-9 consultation.                 |


## 3. System Architecture & Object-Oriented Design

_To be completed in Week 9._

## 4. Data Model & Database Design

_To be completed in Week 9._

## 5. API Integration Design

_To be completed in Week 9._

## 6. Concurrency Design

_To be completed in Week 10._

## 7. Data Mining & Visualization Design

_To be completed in Week 10._

## 8. Testing Summary

_To be completed in Week 10._

## 9. Individual Contributions

_To be completed in Week 11._

## Appendix

_To be completed in Week 11._
