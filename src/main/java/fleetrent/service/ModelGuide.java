package fleetrent.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Suggests the category (body style) of a vehicle from its make and model name. NHTSA lists only
 * the makes and the model names: it does not say the body style or the transmission of a model.
 * So this class keeps a short list of well-known models and a few word rules ("Hybrid", "EV"). It
 * returns null for a model it does not know, and the owner chooses the category by hand. The
 * transmission is never guessed, because one model is sold with several transmissions.
 */
public final class ModelGuide {
    private static final String SEDAN = "SEDAN";
    private static final String SUV = "SUV";
    private static final String MOTORCYCLE = "MOTORCYCLE";
    private static final List<String> LUXURY_MAKES = List.of("lexus", "mercedes-benz", "bmw",
            "audi", "genesis", "cadillac", "infiniti", "acura", "jaguar", "lincoln", "volvo",
            "bentley", "rolls-royce", "maserati", "porsche", "land rover");
    private static final List<String> ELECTRIC_MAKES = List.of("tesla", "rivian", "lucid",
            "polestar", "byd");
    private static final List<String> ELECTRIC_WORDS = List.of("ev", "electric", "e-tron", "leaf",
            "bolt", "ioniq 5", "ioniq 6", "mach-e", "id.4", "ariya", "bz4x", "ev6", "ev9");
    private static final List<String> ELECTRIC_BIKE_WORDS = List.of("electric", "livewire");
    private static final List<String> ELECTRIC_BIKE_MAKES = List.of("zero", "energica");
    private static final List<String> HYBRID_WORDS = List.of("hybrid", "prius", "prime", "hev",
            "phev", "e-power");

    // Style -> the model names (small letters) that have it. A name is matched as a whole word, so
    // "city" matches "City Hatchback" but not "Velocity". A name ending in * matches a word that
    // starts with it ("cbr*" matches CBR500R). The first style that matches wins.
    private static final Map<String, List<String>> SEDANS = new LinkedHashMap<>();
    private static final Map<String, List<String>> SUVS = new LinkedHashMap<>();
    private static final Map<String, List<String>> BIKES = new LinkedHashMap<>();

    static {
        SEDANS.put("Sports Sedan", List.of("wrx", "stinger", "civic type r", "gr corolla"));
        SEDANS.put("Subcompact Sedan", List.of("vios", "yaris", "city", "mirage", "mirage g4",
                "almera", "versa", "accent", "rio", "soluto", "dzire", "attrage", "fit", "sonic",
                "fiesta", "mazda2"));
        SEDANS.put("Compact Sedan", List.of("civic", "corolla", "corolla altis", "altis",
                "elantra", "sentra", "mazda3", "forte", "jetta", "impreza", "cruze", "focus",
                "lancer", "sylphy", "k3"));
        SEDANS.put("Mid-size Sedan", List.of("camry", "accord", "altima", "sonata", "mazda6",
                "optima", "k5", "malibu", "fusion", "passat", "legacy", "teana"));
        SEDANS.put("Full-size Sedan", List.of("avalon", "maxima", "impala", "taurus", "charger",
                "300", "crown", "azera"));

        SUVS.put("MPV / Minivan", List.of("innova", "avanza", "xpander", "ertiga", "veloz",
                "carnival", "sienna", "odyssey", "alphard", "hiace", "starex", "staria", "livina",
                "br-v", "mobilio", "spin", "zenix", "pacifica", "grand caravan", "sedona", "xl7",
                "stargazer", "rush"));
        SUVS.put("Off-road SUV", List.of("wrangler", "jimny", "bronco", "defender", "fj cruiser",
                "4runner"));
        SUVS.put("Full-size SUV", List.of("land cruiser", "patrol", "expedition", "tahoe",
                "suburban", "sequoia", "armada", "yukon", "durango"));
        SUVS.put("Mid-size SUV", List.of("fortuner", "everest", "montero", "montero sport",
                "mu-x", "terra", "santa fe", "sorento", "highlander", "pilot", "cx-8", "cx-9",
                "explorer", "edge", "pathfinder", "passport", "murano", "grand cherokee",
                "trailblazer", "blazer", "telluride", "palisade"));
        SUVS.put("Compact SUV", List.of("rav4", "cr-v", "cx-5", "tucson", "sportage", "forester",
                "rogue", "x-trail", "escape", "territory", "tiguan", "corolla cross", "equinox",
                "compass", "cherokee", "outlander"));
        SUVS.put("Subcompact SUV", List.of("raize", "hr-v", "kicks", "venue", "ecosport",
                "stonic", "cx-3", "cx-30", "trax", "seltos", "kona", "juke", "sonet",
                "yaris cross"));
        SUVS.put("Crossover", List.of("venza", "crosstrek", "outback", "cx-50", "c-hr"));

        BIKES.put("Scooter", List.of("nmax", "aerox", "pcx*", "click*", "beat", "adv 160",
                "adv160", "adv 150", "adv150", "x-adv", "mio*", "burgman*", "vespa*", "fino",
                "genio", "xmax", "forza*", "scoopy", "zoomer*", "primavera*"));
        BIKES.put("Underbone (moped)", List.of("wave*", "sniper*", "raider*", "xrm*", "rs125",
                "smash*", "supra*", "exciter*", "winner*", "sight", "vega*"));
        BIKES.put("Adventure (dual-purpose)", List.of("africa twin", "versys*", "v-strom*",
                "tenere*", "cb500x", "nx500", "tiger*", "himalayan", "crf300 rally",
                "crf250 rally", "crf1100*", "adventure"));
        BIKES.put("Touring", List.of("gold wing*", "goldwing*", "fjr*", "electra glide*",
                "road king*", "street glide*", "k1600*"));
        BIKES.put("Cruiser", List.of("rebel*", "vulcan*", "bolt*", "boulevard*", "sportster*",
                "fat boy*", "softail*", "intruder*", "scout*", "iron 883", "street 750"));
        BIKES.put("Sport", List.of("ninja*", "cbr*", "r15*", "yzf*", "gsx-r*", "r1", "r3", "r6",
                "r7", "rc*", "panigale*", "zx-*"));
        BIKES.put("Off-road (dirt)", List.of("crf*", "klx*", "wr*", "yz*", "xr*", "tt-r*",
                "kx*", "rm-z*", "xtz*"));
        BIKES.put("Standard (naked)", List.of("mt-*", "cb*", "z400", "z650", "z900", "duke*",
                "sv650", "fz*", "xsr*", "tmx*", "barako*", "street triple*", "monster*"));
    }

    private ModelGuide() {
    }

    /**
     * Returns the category that fits this make and model for one of our vehicle types (SEDAN, SUV
     * or MOTORCYCLE), or null when the model is not known. The answer is always one of
     * VehicleTypes.styles(type).
     */
    public static String suggestStyle(String type, String make, String model) {
        if (type == null || model == null || model.isBlank()) {
            return null;
        }
        String name = model.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        String brand = make == null ? "" : make.trim().toLowerCase(Locale.ROOT);
        switch (type) {
            case SEDAN:
                return forCar(name, brand, SEDANS, "Sedan");
            case SUV:
                return forCar(name, brand, SUVS, "SUV");
            case MOTORCYCLE:
                if (hasWord(name, ELECTRIC_BIKE_WORDS) || ELECTRIC_BIKE_MAKES.contains(brand)) {
                    return "Electric motorcycle";
                }
                return fromList(name, BIKES);
            default:
                return null;
        }
    }

    // Electric and hybrid come first (the name says it), then a luxury make, then the list.
    private static String forCar(String name, String brand, Map<String, List<String>> styles,
            String kind) {
        if (hasWord(name, ELECTRIC_WORDS) || ELECTRIC_MAKES.contains(brand)) {
            return "Electric " + kind;
        }
        if (hasWord(name, HYBRID_WORDS)) {
            return "Hybrid " + kind;
        }
        if (LUXURY_MAKES.contains(brand)) {
            return "Luxury " + kind;
        }
        return fromList(name, styles);
    }

    private static String fromList(String name, Map<String, List<String>> styles) {
        for (Map.Entry<String, List<String>> style : styles.entrySet()) {
            for (String known : style.getValue()) {
                boolean prefix = known.endsWith("*");
                String text = prefix ? known.substring(0, known.length() - 1) : known;
                String end = prefix ? "" : "([^a-z0-9]|$)";
                if (Pattern.compile("(^|[^a-z0-9])" + Pattern.quote(text) + end).matcher(name)
                        .find()) {
                    return style.getKey();
                }
            }
        }
        return null;
    }

    // True when the name has one of the words as a whole word ("ev" matches "Kona EV", not
    // "Everest").
    private static boolean hasWord(String name, List<String> words) {
        for (String word : words) {
            if (Pattern.compile("(^|[^a-z0-9])" + Pattern.quote(word) + "([^a-z0-9]|$)")
                    .matcher(name).find()) {
                return true;
            }
        }
        return false;
    }
}
