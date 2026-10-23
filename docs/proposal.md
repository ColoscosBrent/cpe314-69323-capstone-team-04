# Project Proposal - Team 04 (Group 4)

CPE 314 - Software Design | Week 8 | Instructor: Richyll Son | First Semester, S.Y. 2026-2027

| Item             | Details                                                                           |
| ---------------- | --------------------------------------------------------------------------------- |
| Group Name / No. | Group 4                                                                           |
| Members          | Gabasa, Dexter; Rosal, Wendil; Coloscos, Brent; Lagaya, John Carlo; Labra, Simone |
| Repository URL   | https://github.com/ColoscosBrent/cpe314-69323-capstone-team-04                    |
| Chosen Theme     | Q - Vehicle Rental & Fleet Management                                             |
| API You Will Use | NHTSA vPIC API - https://vpic.nhtsa.dot.gov/api                                   |

## 1. One-Paragraph Pitch

_TODO (Gabasa): delete this line and paste Section 1 (the pitch) here._

### 2. Entity-Relationship Sketch
## Renter
| Attribute | Key | Description |
| --------- | --- | ------------------------------------ |
| id | PK | Unique number for each renter. |
| name | - | Full name of the renter. |
| contact | - | Phone number or email of the renter. |

## Vehicle
| Attribute | Key | Description |
| ------------ | --- | ------------------------------------------------------------------------------------- |
| id | PK | Unique number for each vehicle. |
| type | - | SEDAN, SUV, or MOTORCYCLE. |
| make | - | Brand of the vehicle (from the NHTSA vPIC API). |
| model | - | Model name of the vehicle (from the NHTSA vPIC API). |
| year | - | Model year of the vehicle. |
| dailyRate | - | Rental price per day. |
| mileage | - | Total miles the vehicle has driven so far. |
| categoryPath | - | Category text such as "Car > Sedan > Compact Sedan", used to build the category
tree. |

## Rental
| Attribute | Key | Description |
| ----------- | --- | -------------------------------------- |
| id | PK | Unique number for each rental. |
| renterId | FK | Links to Renter.id (who rented). |
| vehicleId | FK | Links to Vehicle.id (what was rented). |
| startDate | - | Day the vehicle was taken. |
| endDate | - | Day the vehicle was returned. |
| milesDriven | - | Miles driven during this rental. |

**Relationships:** Renter --1:M--> Rental (one renter can have many rentals) and Vehicle --1:M-->
Rental (one vehicle can be in many rentals over time). type is one of SEDAN \| SUV \| MOTORCYCLE.
**Summary:** we have 3 entities (Renter, Vehicle, Rental), all saved in SQLite using JDBC with
PreparedStatement. The waiting-list Queue and the undo Stack are only in memory, not tables.
categoryPath is a text like "Car > Sedan > Compact Sedan" that we use to build the category tree

## 3. Feature Checklist

_TODO (Labra): delete this line and paste Section 3 (the feature table) here._

## 4. Task Split

_TODO (Wendil): delete this line and paste Section 4 (the task split table) here._

## 5. Public API You Plan to Use

_TODO (Lagaya): delete this line and paste Section 5 (the API text) here._

## 6. Risks / Open Questions

_TODO (Lagaya): delete this line and paste Section 6 (the risk questions) here._
