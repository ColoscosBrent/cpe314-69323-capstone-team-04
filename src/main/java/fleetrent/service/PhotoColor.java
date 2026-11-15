package fleetrent.service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Tells whether a photo shows a vehicle of one color by looking at its pixels. It counts how many
 * pixels in the middle of the picture (where the vehicle usually is) have that color. It is a
 * simple estimate, not a promise: the photo search uses it only to prefer a photo in the color the
 * owner chose over a photo in another color.
 */
public final class PhotoColor {
    // The part of the picture that is looked at: the middle, without the sky and the edges.
    private static final double LEFT = 0.15;
    private static final double RIGHT = 0.85;
    private static final double TOP = 0.30;
    private static final double BOTTOM = 0.90;
    private static final int SAMPLES_PER_SIDE = 90;
    // How much of the middle must have the color. Gray shades and the colors of roads, trees and
    // soil are also in the background of many photos, so they need a bigger share.
    private static final double SHARE_BRIGHT_COLOR = 0.22;
    private static final double SHARE_NATURE_COLOR = 0.30;
    private static final double SHARE_GRAY_SHADE = 0.35;
    private static final float DEGREES = 360f;
    private static final float DARK = 0.20f;
    private static final float PALE = 0.16f;
    private static final float VIVID = 0.35f;

    private PhotoColor() {
    }

    /**
     * Returns true when the middle of the picture is mostly of that color. Returns false for a
     * color name it does not know, and for bytes that are not a picture.
     */
    public static boolean matches(byte[] picture, String colorName) {
        if (picture == null || colorName == null) {
            return false;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(picture));
            return image != null && share(image, colorName) >= neededShare(colorName);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Returns the part (0 to 1) of the middle of the picture that has the color, or -1 for a color
     * name it does not know.
     */
    public static double share(BufferedImage image, String colorName) {
        String name = colorName.trim().toLowerCase(Locale.ROOT);
        if (!isKnown(name)) {
            return -1;
        }
        int x0 = (int) (image.getWidth() * LEFT);
        int x1 = Math.max(x0 + 1, (int) (image.getWidth() * RIGHT));
        int y0 = (int) (image.getHeight() * TOP);
        int y1 = Math.max(y0 + 1, (int) (image.getHeight() * BOTTOM));
        int stepX = Math.max(1, (x1 - x0) / SAMPLES_PER_SIDE);
        int stepY = Math.max(1, (y1 - y0) / SAMPLES_PER_SIDE);
        int seen = 0;
        int hits = 0;
        float[] hsb = new float[3];
        for (int y = y0; y < y1; y += stepY) {
            for (int x = x0; x < x1; x += stepX) {
                int rgb = image.getRGB(x, y);
                Color.RGBtoHSB((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff, hsb);
                seen++;
                if (hasColor(name, hsb[0] * DEGREES, hsb[1], hsb[2])) {
                    hits++;
                }
            }
        }
        return seen == 0 ? 0 : (double) hits / seen;
    }

    private static boolean isKnown(String name) {
        return neededShare(name) > 0;
    }

    // The share of the middle that must have the color, or 0 for a color that is not known.
    private static double neededShare(String colorName) {
        switch (colorName.trim().toLowerCase(Locale.ROOT)) {
            case "white":
            case "black":
            case "silver":
            case "gray":
            case "grey":
                return SHARE_GRAY_SHADE;
            case "green":
            case "brown":
            case "beige":
                return SHARE_NATURE_COLOR;
            case "red":
            case "maroon":
            case "blue":
            case "navy":
            case "teal":
            case "yellow":
            case "gold":
            case "orange":
            case "purple":
            case "violet":
            case "pink":
                return SHARE_BRIGHT_COLOR;
            default:
                return 0;
        }
    }

    // True when one pixel has the color. hue is 0 to 360; sat and bright are 0 to 1.
    private static boolean hasColor(String name, float hue, float sat, float bright) {
        boolean vivid = sat >= VIVID && bright > DARK;
        switch (name) {
            case "black":
                return bright <= DARK;
            case "white":
                return bright >= 0.80f && sat <= PALE;
            case "silver":
                return bright >= 0.55f && bright < 0.90f && sat <= PALE;
            case "gray":
            case "grey":
                return bright > DARK && bright < 0.70f && sat <= PALE;
            case "red":
                return vivid && bright >= 0.45f && (hue < 15 || hue >= 345);
            case "maroon":
                return vivid && bright < 0.55f && (hue < 15 || hue >= 330);
            case "orange":
                return vivid && bright >= 0.55f && hue >= 15 && hue < 40;
            case "brown":
                return sat >= 0.30f && bright > DARK && bright < 0.60f && hue >= 10 && hue < 45;
            case "yellow":
                return vivid && bright >= 0.55f && hue >= 40 && hue < 70;
            case "gold":
                return vivid && bright >= 0.45f && hue >= 30 && hue < 58;
            case "beige":
                return sat > 0.10f && sat < VIVID && bright >= 0.65f && hue >= 20 && hue < 60;
            case "green":
                return vivid && hue >= 70 && hue < 165;
            case "teal":
                return vivid && hue >= 160 && hue < 200;
            case "blue":
                return vivid && bright >= 0.40f && hue >= 195 && hue < 255;
            case "navy":
                return vivid && bright < 0.50f && hue >= 205 && hue < 260;
            case "purple":
            case "violet":
                return vivid && hue >= 255 && hue < 300;
            case "pink":
                return sat >= 0.25f && bright >= 0.60f && hue >= 300 && hue < 345;
            default:
                return false;
        }
    }
}
