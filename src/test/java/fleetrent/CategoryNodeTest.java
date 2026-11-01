package fleetrent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CategoryNodeTest {
    @Test
    void emptyTreeHasZeroMileage() {
        assertEquals(0, new CategoryNode("Fleet").totalMileage());
    }

    @Test
    void totalAddsEveryBranch() {
        CategoryNode root = new CategoryNode("Fleet");
        root.addVehicle("Car > Sedan > Compact Sedan", 1000);
        root.addVehicle("Car > Sedan", 500);
        root.addVehicle("Car > SUV", 2000);
        root.addVehicle("Motorcycle", 300);
        assertEquals(3800, root.totalMileage());
    }

    @Test
    void sameCategoryAddsUp() {
        CategoryNode root = new CategoryNode("Fleet");
        root.addVehicle("Car > Sedan", 100);
        root.addVehicle("Car > Sedan", 250);
        assertEquals(350, root.totalMileage());
    }
}
