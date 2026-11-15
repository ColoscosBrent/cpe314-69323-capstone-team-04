package fleetrent.model;

import fleetrent.dto.VehicleModelInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// The factory builds a vehicle from a model info (the object the NHTSA lookup gives) and a type.
class VehicleFactoryModelInfoTest {
    private static final VehicleModelInfo INFO = new VehicleModelInfo("Honda", 7, "Civic");

    @Test
    void theMakeAndTheModelComeFromTheModelInfo() {
        Vehicle v = VehicleFactory.create(0, "SEDAN", INFO, 2021, "ABC 123", "Red", 1500, 10,
                null, "Car > Sedan");
        assertTrue(v instanceof Sedan);
        assertEquals("Honda", v.getMake());
        assertEquals("Civic", v.getModel());
    }

    @Test
    void everyTypeGivesItsOwnSubclass() {
        assertTrue(VehicleFactory.create(0, "SUV", INFO, 2021, "A", "Red", 1, 0, null, null)
                instanceof Suv);
        assertTrue(VehicleFactory.create(0, "MOTORCYCLE", INFO, 2021, "A", "Red", 1, 0, null,
                null) instanceof Motorcycle);
    }

    @Test
    void anUnknownOrMissingTypeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> VehicleFactory.create(0, "BOAT", INFO,
                2021, "A", "Red", 1, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> VehicleFactory.create(0, null, INFO,
                2021, "A", "Red", 1, 0, null, null));
    }

    @Test
    void theTextVersionGivesTheSameVehicle() {
        Vehicle a = VehicleFactory.create(0, "SEDAN", INFO, 2021, "A", "Red", 1, 0, null, null);
        Vehicle b = VehicleFactory.create(0, "SEDAN", "Honda", "Civic", 2021, "A", "Red", 1, 0,
                null, null);
        assertEquals(a.getMake(), b.getMake());
        assertEquals(a.getModel(), b.getModel());
    }
}
