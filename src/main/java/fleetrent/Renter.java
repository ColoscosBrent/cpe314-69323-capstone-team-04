package fleetrent;

// One customer who rents vehicles.
public class Renter {
    private int id;
    private final String name;
    private final String contact;
    private final String email;
    private final String address;

    public Renter(int id, String name, String contact, String email, String address) {
        this.id = id;
        this.name = name;
        this.contact = contact;
        this.email = email;
        this.address = address;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public String getContact() { return contact; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }

    @Override
    public String toString() {
        return name + " (" + contact + ")";
    }
}
