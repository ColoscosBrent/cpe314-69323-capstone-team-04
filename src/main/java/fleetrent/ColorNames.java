package fleetrent;

import java.util.LinkedHashMap;
import java.util.Map;
import javafx.scene.paint.Color;

// Turns the color text of a vehicle ("White", "pearl black", "#ff0000") into a real Color,
// so the car picture on the screen has the same color as the vehicle in the database.
public final class ColorNames {
    public static final Color FALLBACK = Color.web("#9aa0b4");

    private static final Map<String, Color> KNOWN = new LinkedHashMap<>();

    static {
        KNOWN.put("white", Color.web("#f4f4fa"));
        KNOWN.put("black", Color.web("#1d1b26"));
        KNOWN.put("silver", Color.web("#c3c7d1"));
        KNOWN.put("gray", Color.web("#8a8f9e"));
        KNOWN.put("grey", Color.web("#8a8f9e"));
        KNOWN.put("red", Color.web("#d92b45"));
        KNOWN.put("maroon", Color.web("#7a1630"));
        KNOWN.put("blue", Color.web("#2f6df0"));
        KNOWN.put("navy", Color.web("#1d2f6f"));
        KNOWN.put("teal", Color.web("#16a3a3"));
        KNOWN.put("green", Color.web("#1f9d6b"));
        KNOWN.put("yellow", Color.web("#f2c94c"));
        KNOWN.put("gold", Color.web("#d4a017"));
        KNOWN.put("orange", Color.web("#f2812e"));
        KNOWN.put("brown", Color.web("#7b4a2d"));
        KNOWN.put("beige", Color.web("#d9c7a3"));
        KNOWN.put("purple", Color.web("#7c3aed"));
        KNOWN.put("violet", Color.web("#8b5cf6"));
        KNOWN.put("pink", Color.web("#ec6fa8"));
    }

    private ColorNames() {
    }

    // Never returns null. Unknown or empty text gives a neutral gray.
    public static Color toColor(String text) {
        if (text == null || text.isBlank()) {
            return FALLBACK;
        }
        String key = text.trim().toLowerCase(java.util.Locale.ROOT);
        Color exact = KNOWN.get(key);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, Color> e : KNOWN.entrySet()) {
            if (key.contains(e.getKey())) {
                return e.getValue();
            }
        }
        try {
            return Color.web(key);
        } catch (IllegalArgumentException ex) {
            return FALLBACK;
        }
    }

    // The colors a vehicle can have in FleetRent. NHTSA has no color data, so this fixed list is used.
    public static String[] choices() {
        return new String[] {"White", "Black", "Silver", "Gray", "Red", "Maroon", "Blue", "Navy", "Teal",
            "Green", "Yellow", "Gold", "Orange", "Brown", "Beige", "Purple", "Pink"};
    }

    // The color names shown as quick swatches in the Add Vehicle form.
    public static String[] swatchNames() {
        return new String[] {"White", "Black", "Silver", "Red", "Blue", "Green", "Orange", "Purple"};
    }
}
