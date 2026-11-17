package fleetrent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

// The color check of a photo. The pictures are drawn here: a sky, a road and a vehicle body in the
// middle, so no file and no internet are needed.
class PhotoColorTest {
    private static final Color SKY = new Color(150, 190, 235);
    private static final Color ROAD = new Color(120, 120, 124);

    // A 400 x 300 picture: sky on top, road below, and a body of the given color in the middle.
    static BufferedImage photo(Color body, Color background) {
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(SKY);
        g.fillRect(0, 0, 400, 110);
        g.setColor(background);
        g.fillRect(0, 110, 400, 190);
        g.setColor(body);
        g.fillRect(70, 120, 260, 110);
        g.setColor(new Color(20, 20, 24));
        g.fillOval(100, 215, 44, 44);
        g.fillOval(256, 215, 44, 44);
        g.dispose();
        return image;
    }

    static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void aRedCarOnAGrayRoadIsRedAndNoOtherColor() throws IOException {
        byte[] red = png(photo(new Color(205, 30, 45), ROAD));
        assertTrue(PhotoColor.matches(red, "Red"));
        assertTrue(PhotoColor.matches(red, " red "), "spaces and capital letters do not matter");
        assertFalse(PhotoColor.matches(red, "Blue"));
        assertFalse(PhotoColor.matches(red, "White"));
        assertFalse(PhotoColor.matches(red, "Black"));
        assertFalse(PhotoColor.matches(red, "Green"));
    }

    @Test
    void everyColorOfTheFormIsRecognizedOnItsOwnPicture() throws IOException {
        Object[][] cars = {
            {"White", new Color(244, 244, 248)}, {"Black", new Color(18, 18, 22)},
            {"Silver", new Color(196, 199, 205)}, {"Gray", new Color(110, 112, 118)},
            {"Red", new Color(217, 43, 69)}, {"Maroon", new Color(122, 22, 48)},
            {"Blue", new Color(47, 109, 240)}, {"Navy", new Color(29, 47, 111)},
            {"Teal", new Color(22, 163, 163)}, {"Green", new Color(31, 157, 107)},
            {"Yellow", new Color(242, 201, 76)}, {"Gold", new Color(212, 160, 23)},
            {"Orange", new Color(242, 129, 46)}, {"Brown", new Color(123, 74, 45)},
            {"Beige", new Color(217, 199, 163)}, {"Purple", new Color(124, 58, 237)},
            {"Pink", new Color(236, 111, 168)}};
        for (Object[] car : cars) {
            // A dark road for the light cars and a light road for the dark ones, so the body is
            // the only part with that color.
            Color road = "Gray".equals(car[0]) || "Black".equals(car[0]) || "Navy".equals(car[0])
                    ? new Color(225, 215, 190) : new Color(60, 62, 70);
            assertTrue(PhotoColor.matches(png(photo((Color) car[1], road)), (String) car[0]),
                    car[0] + " car is seen as " + car[0]);
        }
    }

    @Test
    void aSilverCarInFrontOfTreesIsNotGreen() throws IOException {
        // Trees fill a part of the picture, but the middle is mostly the silver body.
        byte[] picture = png(photo(new Color(196, 199, 205), new Color(52, 120, 60)));
        assertFalse(PhotoColor.matches(picture, "Green"));
        assertTrue(PhotoColor.matches(picture, "Silver"));
    }

    @Test
    void theShareIsThePartOfTheMiddleThatHasTheColor() {
        BufferedImage allRed = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = allRed.createGraphics();
        g.setColor(new Color(210, 30, 40));
        g.fillRect(0, 0, 50, 50);
        g.dispose();
        assertEquals(1.0, PhotoColor.share(allRed, "Red"), 0.0001);
        assertEquals(0.0, PhotoColor.share(allRed, "Blue"), 0.0001);
        assertEquals(-1, PhotoColor.share(allRed, "Sparkle"), 0.0001, "an unknown color name");
    }

    @Test
    void anUnknownColorOrABrokenPictureNeverMatches() throws IOException {
        byte[] red = png(photo(new Color(205, 30, 45), ROAD));
        assertFalse(PhotoColor.matches(red, "Sparkle"));
        assertFalse(PhotoColor.matches(red, null));
        assertFalse(PhotoColor.matches(null, "Red"));
        assertFalse(PhotoColor.matches(new byte[] {1, 2, 3}, "Red"));
        assertFalse(PhotoColor.matches(new byte[0], "Red"));
        // A one-pixel picture is still read without a crash.
        BufferedImage dot = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        dot.setRGB(0, 0, new Color(210, 30, 40).getRGB());
        assertTrue(PhotoColor.matches(png(dot), "Red"));
    }
}
