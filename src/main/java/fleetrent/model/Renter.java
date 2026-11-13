package fleetrent.model;


/**
 * One customer who rents vehicles.
 */
public class Renter {
    private int id;
    private final String name;
    private final String contact;
    private final String email;
    private final String address;

    /** Creates a renter. */
    public Renter(int id, String name, String contact, String email, String address) {
        this.id = id;
        this.name = name;
        this.contact = contact;
        this.email = email;
        this.address = address;
    }

    /** Returns the id. */
    public int getId() { return id; }
    /** Sets the id. */
    public void setId(int id) { this.id = id; }
    /** Returns the name of this category. */
    public String getName() { return name; }
    /** Returns the contact. */
    public String getContact() { return contact; }
    /** Returns the email. */
    public String getEmail() { return email; }
    /** Returns the address. */
    public String getAddress() { return address; }

    @Override
    public String toString() {
        return name + " (" + contact + ")";
    }
}
