package fleetrent.model;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class VehicleTypesTest {
    @Test
    void apiTypeMapsEveryType() {
        assertEquals("car", VehicleTypes.apiType("SEDAN"));
        assertEquals("mpv", VehicleTypes.apiType("SUV"));
        assertEquals("motorcycle", VehicleTypes.apiType("MOTORCYCLE"));
        assertThrows(IllegalArgumentException.class, () -> VehicleTypes.apiType("BOAT"));
    }

    @Test
    void fromApiNameUnderstandsNhtsaNames() {
        assertEquals("MOTORCYCLE", VehicleTypes.fromApiName("Motorcycle"));
        assertEquals("SUV", VehicleTypes.fromApiName("Multipurpose Passenger Vehicle (MPV)"));
        assertEquals("SEDAN", VehicleTypes.fromApiName("Passenger Car"));
        assertNull(VehicleTypes.fromApiName("Truck"));
        assertNull(VehicleTypes.fromApiName(null));
    }

    @Test
    void categoryOptionsBelongToTheirType() {
        for (String type : VehicleTypes.ALL) {
            assertFalse(VehicleTypes.categoryOptions(type).isEmpty());
            for (String path : VehicleTypes.categoryOptions(type)) {
                assertTrue(VehicleTypes.isCategoryOf(type, path));
            }
        }
        assertFalse(VehicleTypes.isCategoryOf("SUV", "Motorcycle"));
        for (String type : VehicleTypes.ALL) {
            assertTrue(VehicleTypes.categoryOptions(type).contains(
                    VehicleFactory.defaultCategoryPath(type))
                    || VehicleTypes.isCategoryOf(type, VehicleFactory.defaultCategoryPath(type)));
        }
    }

    @Test
    void toStringHidesAnUnknownYear() {
        Vehicle withYear = VehicleFactory.create(0, "SUV", "Ford", "Everest", 2021, "AAA 111",
                "Black", 2000, 0, null, "Car > SUV");
        Vehicle noYear = VehicleFactory.create(0, "SUV", "Ford", "Everest", 0, "AAA 111", "Black",
                2000, 0, null, "Car > SUV");
        assertEquals("SUV - Ford Everest 2021 (AAA 111)", withYear.toString());
        assertEquals("SUV - Ford Everest (AAA 111)", noYear.toString());
    }

    @Test
    void motorcyclesOfferEveryGearbox() {
        assertTrue(VehicleTypes.styles("MOTORCYCLE").containsAll(List.of("Scooter", "Sport",
                "Cruiser", "Touring")));
        assertEquals(List.of("Automatic (twist-and-go)"),
                VehicleTypes.transmissions("MOTORCYCLE", "Scooter"));
        assertTrue(VehicleTypes.transmissions("MOTORCYCLE", "Sport").contains("Manual (clutch)"));
        assertTrue(VehicleTypes.transmissions("MOTORCYCLE", "Sport")
                .contains("Semi-automatic (no clutch lever)"));
        assertEquals(List.of("Semi-automatic (no clutch lever)"),
                VehicleTypes.transmissions("MOTORCYCLE", "Underbone (moped)"));
    }

    @Test
    void carsOfferManualAutomaticAndTheOthers() {
        for (String type : List.of("SEDAN", "SUV")) {
            String style = VehicleTypes.styles(type).get(0);
            List<String> gears = VehicleTypes.transmissions(type, style);
            assertTrue(gears.containsAll(List.of("Automatic", "Manual (clutch)", "CVT",
                    "Dual-clutch")), type);
        }
        assertEquals(List.of("Automatic"), VehicleTypes.transmissions("SEDAN", "Electric Sedan"));
        assertEquals(List.of("Automatic"), VehicleTypes.transmissions("SUV", "Electric SUV"));
    }

    @Test
    void aPathCanBeSplitBackIntoItsStyleAndTransmission() {
        for (String type : VehicleTypes.ALL) {
            for (String style : VehicleTypes.styles(type)) {
                for (String gear : VehicleTypes.transmissions(type, style)) {
                    String path = VehicleTypes.categoryPath(type, style, gear);
                    assertEquals(style, VehicleTypes.styleOf(type, path), path);
                    assertEquals(gear, VehicleTypes.transmissionOf(type, path), path);
                    assertTrue(VehicleTypes.isCategoryOf(type, path), path);
                }
            }
        }
        assertFalse(VehicleTypes.isCategoryOf("SEDAN", "Car > Sedan > Cruiser > Automatic"));
        assertNull(VehicleTypes.styleOf("SEDAN", "Car > Sedan"));
    }
}
