package fleetrent.service;

import fleetrent.dto.VehicleMakeDto;
import fleetrent.dto.VehicleModelDto;
import fleetrent.dto.VehicleModelInfo;
import fleetrent.dto.VpicResponse;
import fleetrent.dto.WikipediaPageDto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

// The mapper turns the saved answers of NHTSA and Wikipedia (src/test/resources) into DTO objects
// and the DTOs into the program's own objects. No network is used.
class ApiMapperTest {
    private static String saved(String name) throws IOException {
        try (InputStream in = ApiMapperTest.class.getResourceAsStream("/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void theOuterShapeOfAVpicAnswerIsRead() throws Exception {
        VpicResponse response = ApiMapper.readVpic(saved("vpic/models-toyota.json"));
        assertEquals(3, response.getCount());
        assertEquals("Response returned successfully", response.getMessage());
        assertEquals(3, response.getResults().size());
    }

    @Test
    void modelRowsBecomeDtosWithTheFieldsOfTheAnswer() throws Exception {
        List<VehicleModelDto> models = ApiMapper.readModels(
                ApiMapper.readVpic(saved("vpic/models-toyota.json")));
        assertEquals(3, models.size());
        assertEquals(448, models.get(0).getMakeId());
        assertEquals("TOYOTA", models.get(0).getMakeName());
        assertEquals(2208, models.get(0).getModelId());
        assertEquals("Corolla", models.get(0).getModelName());
    }

    @Test
    void modelDtosBecomeModelInfosSortedByName() throws Exception {
        List<VehicleModelInfo> infos = ApiMapper.toModelInfos(ApiMapper.readModels(
                ApiMapper.readVpic(saved("vpic/models-toyota.json"))));
        assertEquals(List.of("4Runner", "camry", "Corolla"),
                infos.stream().map(VehicleModelInfo::getModelName).toList());
        assertEquals("TOYOTA", infos.get(0).getMakeName());
        assertEquals(2210, infos.get(0).getModelId());
    }

    @Test
    void makeRowsBecomeDtosAndTheNamesAreSortedWithoutRepeats() throws Exception {
        List<VehicleMakeDto> makes = ApiMapper.readMakes(
                ApiMapper.readVpic(saved("vpic/makes-motorcycle.json")));
        assertEquals(3, makes.size());
        assertEquals(474, makes.get(0).getMakeId());
        assertEquals(List.of("Aprilia", "HONDA"), ApiMapper.makeNames(makes));
    }

    @Test
    void aRowWithAWrongTypeOrNoObjectIsSkipped() throws Exception {
        List<VehicleModelInfo> infos = VpicClient.parseModels(saved("vpic/models-bad-row.json"));
        assertEquals(1, infos.size());
        assertEquals("Everest", infos.get(0).getModelName());
    }

    @Test
    void aMissingModelIdBecomesZero() {
        VehicleModelInfo info = VpicClient.parseModels(
                "{\"Results\":[{\"Make_Name\":\"FORD\",\"Model_Name\":\"Everest\"}]}").get(0);
        assertEquals(0, info.getModelId());
    }

    @Test
    void badVpicTextIsRefusedWithAClearReason() {
        assertThrows(IllegalArgumentException.class, () -> ApiMapper.readVpic(""));
        assertThrows(IllegalArgumentException.class, () -> ApiMapper.readVpic("nope"));
        assertThrows(IllegalArgumentException.class, () -> ApiMapper.readVpic("[1,2]"));
        assertThrows(IllegalArgumentException.class, () -> ApiMapper.readVpic("{\"Count\":0}"));
    }

    @Test
    void wikipediaPagesBecomeDtosWithTitleRankAndThumbnail() throws Exception {
        List<WikipediaPageDto> pages = ApiMapper.readPages(saved("wikipedia/search-honda-civic"
                + ".json"));
        assertEquals(4, pages.size());
        WikipediaPageDto main = pages.stream().filter(p -> p.getIndex() == 1).findFirst()
                .orElseThrow();
        assertEquals("Honda Civic", main.getTitle());
        assertEquals("https://upload.example.org/civic-main.jpg", main.getThumbnail().getSource());
        WikipediaPageDto noPicture = pages.stream().filter(p -> p.getIndex() == 4).findFirst()
                .orElseThrow();
        assertNull(noPicture.getThumbnail());
    }

    @Test
    void theBestRankedPageWithAPictureGivesTheThumbnail() throws Exception {
        String json = saved("wikipedia/search-honda-civic.json");
        assertEquals("https://upload.example.org/civic-main.jpg",
                CarImageClient.parseThumbnailUrl(json));
        assertEquals("https://upload.example.org/civic-main.jpg",
                CarImageClient.parseThumbnailUrl(json, "Honda", "Civic"));
        assertEquals("https://upload.example.org/accord.jpg",
                CarImageClient.parseThumbnailUrl(json, "Honda", "Accord"));
        assertNull(CarImageClient.parseThumbnailUrl(json, "Toyota", "Vios"));
    }

    @Test
    void anAnswerWithoutQueryGivesNoPagesAndNoThumbnail() throws Exception {
        String json = saved("wikipedia/search-nothing.json");
        assertTrue(ApiMapper.readPages(json).isEmpty());
        assertNull(CarImageClient.parseThumbnailUrl(json));
    }

    @Test
    void badWikipediaTextIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ApiMapper.readPages(" "));
        assertThrows(IllegalArgumentException.class, () -> ApiMapper.readPages("{oops"));
    }
}
