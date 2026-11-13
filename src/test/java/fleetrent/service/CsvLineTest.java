package fleetrent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvLineTest {
    @Test
    void plainValuesAreSplitOnCommas() {
        assertEquals(List.of("a", "b", "c"), CsvLine.parse("a,b,c"));
        assertEquals(List.of("a", "", "c", ""), CsvLine.parse("a,,c,"));
    }

    @Test
    void aQuotedValueKeepsItsCommasAndQuotes() {
        assertEquals(List.of("12 Rizal St, Cebu", "He said \"hi\""),
                CsvLine.parse("\"12 Rizal St, Cebu\",\"He said \"\"hi\"\"\""));
    }

    @Test
    void anUnclosedQuoteIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> CsvLine.parse("a,\"b,c"));
    }

    @Test
    void joiningThenParsingGivesTheSameValues() {
        List<String> values = Arrays.asList("Reyes, Ana Cruz", "", "say \"x\"", "plain");
        assertEquals(values, CsvLine.parse(CsvLine.join(values)));
    }

    @Test
    void aMissingValueBecomesBlank() {
        assertEquals("a,,c", CsvLine.join(Arrays.asList("a", null, "c")));
    }
}
