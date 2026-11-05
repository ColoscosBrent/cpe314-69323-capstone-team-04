package fleetrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SampleDataTest {
    private static final LocalDate END = LocalDate.of(2026, 10, 1);

    @Test
    void sameSeedGivesSameLinesAndDifferentSeedDoesNot() {
        List<Vehicle> v = SampleData.demoVehicles();
        assertEquals(SampleData.rentalLines(v, 20, 5L, END), SampleData.rentalLines(v, 20, 5L, END));
        assertNotEquals(SampleData.rentalLines(v, 20, 5L, END), SampleData.rentalLines(v, 20, 6L, END));
    }

    @Test
    void firstLineIsTheHeaderAndRowCountMatches() {
        List<String> lines = SampleData.rentalLines(SampleData.demoVehicles(), 10, 1L, END);
        assertEquals(11, lines.size());
        assertEquals(RentalCsvImporter.HEADER, lines.get(0));
    }

    @Test
    void everyGeneratedLineIsAcceptedByTheImporterRules() {
        List<Vehicle> vehicles = SampleData.demoVehicles();
        Map<String, Vehicle> plates = new HashMap<>();
        for (Vehicle v : vehicles) {
            plates.put(v.getPlateNumber().toUpperCase(), v);
        }
        List<String> lines = SampleData.rentalLines(vehicles, 200, 9L, END);
        for (String line : lines.subList(1, lines.size())) {
            RentalCsvImporter.parseRow(line, plates);
        }
    }

    @Test
    void badArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SampleData.rentalLines(new ArrayList<>(), 5, 1L, END));
        assertThrows(IllegalArgumentException.class, () -> SampleData.rentalLines(SampleData.demoVehicles(), 0, 1L, END));
    }

    @Test
    void demoVehiclesCoverAllThreeTypes() {
        List<Vehicle> list = SampleData.demoVehicles();
        assertEquals(6, list.size());
        assertEquals("SEDAN", list.get(0).getType());
        assertEquals("SUV", list.get(2).getType());
        assertEquals("MOTORCYCLE", list.get(4).getType());
    }
}
