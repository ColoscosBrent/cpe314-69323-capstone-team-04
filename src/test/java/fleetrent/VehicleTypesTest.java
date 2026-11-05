package fleetrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(VehicleFactory.defaultCategoryPath("SEDAN"), VehicleTypes.categoryOptions("SEDAN").get(0));
        assertEquals(VehicleFactory.defaultCategoryPath("SUV"), VehicleTypes.categoryOptions("SUV").get(0));
        assertEquals(VehicleFactory.defaultCategoryPath("MOTORCYCLE"), VehicleTypes.categoryOptions("MOTORCYCLE").get(0));
    }

    @Test
    void toStringHidesAnUnknownYear() {
        Vehicle withYear = VehicleFactory.create(0, "SUV", "Ford", "Everest", 2021, "AAA 111", "Black", 2000, 0, null, "Car > SUV");
        Vehicle noYear = VehicleFactory.create(0, "SUV", "Ford", "Everest", 0, "AAA 111", "Black", 2000, 0, null, "Car > SUV");
        assertEquals("SUV - Ford Everest 2021 (AAA 111)", withYear.toString());
        assertEquals("SUV - Ford Everest (AAA 111)", noYear.toString());
    }
}
