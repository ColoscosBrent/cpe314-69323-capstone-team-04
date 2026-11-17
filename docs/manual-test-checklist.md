# Manual Test Checklist (screens)

Run the app with `Main`. Tick each line. Date: ________  Tester: ________

## Owner
- [ ] The login screen shows a photo background, the buttons Log in as Renter and Log in as Owner, and cars that drive along a loading line.
- [ ] The title bar of every window has the FleetRent logo (the key) on the left, the app colors, and working minimize, maximize and close buttons.
- [ ] A wrong username says "The username is not correct", a wrong password says "The password is not correct", and an owner account on the Renter button says to choose Owner.
- [ ] Create an account, "I own the rental shop": no code is asked. Last, first and middle name and a phone number with a country are required. A name with only one word is refused.
- [ ] Sign out returns to the login screen. Log in again works.
- [ ] Forgot password: type the username, answer the question, set a new password, log in with it.
- [ ] Vehicles tab, Add: choose Type, then the Year (there is no 2027), then a make and a model (they are locked until a year is chosen), a Color (each name has its circle), a Category and a Transmission, press Add vehicle. The vehicle is added. The Motorcycle category list has Scooter, Underbone, Standard, Sport, Cruiser, Touring, Adventure, Off-road and Electric, with Manual (clutch) where it fits.
- [ ] Vehicles tab: click a model in the NHTSA list (for example Toyota Camry). The Category box changes to its category (Mid-size Sedan), the Transmission box lists what fits, and a note says it is a suggestion. Both boxes can still be changed.
- [ ] Vehicles tab: add a vehicle with a color and no photo, with internet. The status line says whether the photo that was found is in that color. Find photos for all does the same for every vehicle without a photo.
- [ ] Rentals tab: a new renter needs last, first and middle name, a phone with a country, an email and an address (street, city, country).
- [ ] Email boxes (Create an account, New renter): capital letters turn small while typing, and text that is not like name@example.com is refused with a message.
- [ ] Import tab: import a CSV with the 13 columns. The message says how many rentals were added and how many the app has in all. Import history shows the file and every line (Saved or Skipped). Import the same file again: it is refused. Cancel import stops a long import and keeps the rows saved so far.
- [ ] Export all records (Import tab) writes every rental in the app. Export this import (Import history tab) writes only the rows of the chosen file: a 30-row file gives 30 rows.
- [ ] Edit a vehicle: change the daily rate and the photo, Save changes. The card shows the new values. Cancel editing leaves it unchanged.
- [ ] Remove a vehicle that is not rented out: it leaves the list. Removed vehicles shows it, and Restore brings it back.
- [ ] Remove a vehicle that is rented out: it is refused with a message.
- [ ] Rentals tab: start a rental and return it. A rental on days held by a reservation is refused.
- [ ] Reservations tab: a renter's reservation shows. Hand over works on the start day. Cancel works.
- [ ] Dashboard and Import tabs still work.

## Renter
- [ ] Create an account, "I want to rent": name, phone with a country, email and address are all required. Only the tabs Cars, My bookings and My account show. No owner information is visible.
- [ ] Cars: every card shows picture, make, model, daily rent, extra-mile fee and a status for the chosen dates.
- [ ] Change the dates: the statuses change. A removed vehicle never shows.
- [ ] Rent today: the car becomes Rented. Undo last takes it back.
- [ ] Reserve these days (tomorrow or later): the car shows Reserved on those days. Undo last cancels it.
- [ ] A car that is busy on the chosen days cannot be rented or reserved, and a message says why.
- [ ] My bookings: lists their own rentals and reservations. Cancel works on a reservation.
- [ ] My account: Change password works (the current password is required).
- [ ] Sign out works.

## Two windows
- [ ] Press New window, log in as the owner in one window and as a renter in the other.
- [ ] A renter's Rent or Reserve shows in the owner's Rentals or Reservations tab within about 3 seconds.
- [ ] A vehicle the owner removes disappears from the renter's Cars within about 3 seconds.
- [ ] Both windows try to book the same days at almost the same time: only one succeeds.

## Window size (also tested by `ScreenLayoutTest`)
- [ ] Press Maximize on the owner screen: the header, the tabs and the lists use the whole window. Press it again to restore.
- [ ] Do the same on the renter screen and on the login screen.
- [ ] Drag the window corner to make it as small as it goes (860 x 620): the title and Sign out stay visible, and wide tabs show scroll bars instead of cut-off buttons.
- [ ] Open every tab at a big size (full screen) and at a small size: nothing overlaps and no button is cut off.
- [ ] Owner: pick a vehicle, press Maintenance, save a PMS record. The record is listed and the last PMS date of the vehicle changes.
- [ ] Owner: return a rental after its last day. The Rentals tab shows "paid" with a late fee.
- [ ] Start a rental on a vehicle that is out, close the app, open it again: the waiting count of that vehicle is still the same.
- [ ] Renter account: there is no Remove, Restore or Maintenance button anywhere.
- [ ] The header shows the name of the signed-in person and the role badge.
