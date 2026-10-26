package fleetrent;

import java.util.LinkedHashMap;
import java.util.Map;

// One box in the vehicle category tree, for example Car > Sedan > Compact Sedan.
public class CategoryNode {
    private final String name;
    private int ownMileage;
    private final Map<String, CategoryNode> children = new LinkedHashMap<>();

    public CategoryNode(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    // Adds a vehicle's mileage under a path like "Car > Sedan > Compact Sedan".
    // Missing boxes on the way are created.
    public void addVehicle(String categoryPath, int mileage) {
        CategoryNode current = this;
        for (String part : categoryPath.split(">")) {
            String childName = part.trim();
            if (childName.isEmpty()) {
                continue;
            }
            CategoryNode next = current.children.get(childName);
            if (next == null) {
                next = new CategoryNode(childName);
                current.children.put(childName, next);
            }
            current = next;
        }
        current.ownMileage += mileage;
    }

    // Recursive method. Base case: a box with no children returns only its own mileage.
    // Otherwise it adds its own mileage to the total of every child below it.
    public int totalMileage() {
        int total = ownMileage;
        for (CategoryNode child : children.values()) {
            total += child.totalMileage();
        }
        return total;
    }

    // Recursive method that prints the tree, one level deeper for each child.
    public String toText(int depth) {
        StringBuilder text = new StringBuilder();
        text.append("  ".repeat(depth)).append(name)
            .append(" (").append(totalMileage()).append(" mi)").append("\n");
        for (CategoryNode child : children.values()) {
            text.append(child.toText(depth + 1));
        }
        return text.toString();
    }
}
