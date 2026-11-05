# Known Issues and Limitations (Milestone 2)

1. **Needs internet for NHTSA.** The make and model lists and the live dashboard question need a connection. Without one, the app shows a red message that says what is wrong (no internet, too slow, blocked by a firewall, NHTSA problem) and Make and Model can be typed by hand instead; everything else still works. Results are never cached on purpose.
2. **US market models.** NHTSA lists vehicles sold in the United States, so some models are not sold in the Philippines. Approved by the instructor for this capstone.
3. **Waiting list and undo history are in memory.** They are lost when the app closes. Rentals themselves are saved in SQLite.
4. **CSV format is simple.** Names with commas or quotes inside are not supported. Bad lines are skipped and listed in the Import tab.
5. **Plate numbers are unique only in the screen.** The Add Vehicle button and the CSV import check for duplicates, but the database table has no UNIQUE rule for plates.
6. **Dates have no time of day.** A rental is billed in whole days, and a same-day return counts as 1 day.
7. **Dashboard counts only returned rentals.** Ongoing rentals are not in the totals.
8. **Single user, single file.** The data is one local file (`fleetrent.db`). There is no login and no multi-user use.
9. **Live dashboard question is slow for many makes.** It makes one API call per make, one after the other.
10. **Car photos need the internet and are not always right.** The photo comes from a Wikipedia search for "make model". Some models have no photo, and a few may show a different car or a logo. The user can press "Choose photo..." to use their own picture, or the app shows the drawn car. We tested the photo code with a fake server, not with live Wikipedia.
11. **Photo files are not in the database.** They are saved in the folder `vehicle_images` next to `fleetrent.db` (named by vehicle id) and are not committed to Git. A new computer starts without photos until the user adds them or presses "Find photos for all".
12. **Utilization uses the whole month.** Fleet utilization divides by every vehicle times every day of the month, even for the current month that is not over yet.
13. **No colors from NHTSA.** The vPIC service has no color data, so the Color box uses a fixed list of 17 names.
14. **Only cars, SUVs and motorcycles.** NHTSA also lists trucks, buses and trailers. FleetRent has only the three types, so those are not offered. NHTSA files SUVs under "mpv" (multipurpose passenger vehicle), so a few vans may show up in the SUV list.
15. **Model year is optional.** NHTSA does not say which years a model was sold when no year is chosen. A vehicle added without a year is saved with an empty year and shown without one.
16. **The NHTSA addresses were taken from NHTSA's documentation.** The tests use a fake server; the live service must be checked once on a real computer with internet (see the guide, Part 8).
