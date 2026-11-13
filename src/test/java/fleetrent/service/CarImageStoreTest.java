package fleetrent.service;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CarImageStoreTest {
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0};
    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

    @TempDir
    Path temp;
    private CarImageStore store;

    @BeforeEach
    void setUp() {
        store = new CarImageStore(temp.resolve("pics"));
    }

    @Test
    void savedPictureCanBeFoundAgain() throws IOException {
        assertNull(store.find(5));
        Path saved = store.save(5, PNG);
        assertEquals(saved, store.find(5));
        assertTrue(saved.getFileName().toString().equals("vehicle-5.png"));
        assertTrue(store.has(5));
        assertFalse(store.has(6));
    }

    @Test
    void savingAgainReplacesTheOldPictureEvenWithAnotherType() throws IOException {
        store.save(1, PNG);
        store.save(1, JPG);
        assertEquals("vehicle-1.jpg", store.find(1).getFileName().toString());
        assertFalse(Files.exists(temp.resolve("pics").resolve("vehicle-1.png")));
    }

    @Test
    void textOrEmptyBytesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> store.save(1, "hello world".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> store.save(1, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> store.save(1, null));
    }

    @Test
    void copyFromChecksTheFile() throws IOException {
        Path good = temp.resolve("my car.jpeg");
        Files.write(good, JPG);
        assertNotNull(store.copyFrom(9, good));
        assertTrue(store.has(9));
        assertThrows(IllegalArgumentException.class, () -> store.copyFrom(9,
                temp.resolve("missing.png")));
        Path fake = temp.resolve("fake.png");
        Files.writeString(fake, "not a picture");
        assertThrows(IllegalArgumentException.class, () -> store.copyFrom(10, fake));
        assertFalse(store.has(10));
    }

    @Test
    void deleteRemovesThePicture() throws IOException {
        store.save(3, PNG);
        store.delete(3);
        assertFalse(store.has(3));
        store.delete(3);
    }

    @Test
    void pictureNamesAreCheckedIgnoringCase() {
        assertTrue(CarImageStore.isPictureName("CAR.JPG"));
        assertTrue(CarImageStore.isPictureName("a.png"));
        assertFalse(CarImageStore.isPictureName("notes.txt"));
        assertFalse(CarImageStore.isPictureName(null));
    }
}
