package fleetrent.model;


import java.time.LocalDate;

/**
 * One login: a username, a password (saved only as a hash), a security question for "Forgot
 * password", a phone number and the Renter row this account belongs to (renters only).
 */
public class Account {
    private int id;
    private final String username;
    private final Role role;
    private final String displayName;
    private final String phone;
    private final String passwordHash;
    private final String passwordSalt;
    private final String securityQuestion;
    private final String answerHash;
    private final String answerSalt;
    private final Integer renterId;
    private final LocalDate createdOn;

    /** Creates an account without a phone number (an account made by an older version). */
    public Account(int id, String username, Role role, String displayName, String passwordHash,
                   String passwordSalt, String securityQuestion, String answerHash,
<<<<<<< HEAD
                   String answerSalt, Integer renterId, LocalDate createdOn) {
        this(id, username, role, displayName, null, passwordHash, passwordSalt, securityQuestion,
                answerHash, answerSalt, renterId, createdOn);
    }

    /** Creates an account. The phone is saved like "+63 9171234567". */
    public Account(int id, String username, Role role, String displayName, String phone,
                   String passwordHash, String passwordSalt, String securityQuestion,
                   String answerHash, String answerSalt, Integer renterId,
                   LocalDate createdOn) {
=======
                           String answerSalt,
                   Integer renterId, LocalDate createdOn) {
>>>>>>> origin/project/milestone-2
        this.id = id;
        this.username = username;
        this.role = role;
        this.displayName = displayName;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.securityQuestion = securityQuestion;
        this.answerHash = answerHash;
        this.answerSalt = answerSalt;
        this.renterId = renterId;
        this.createdOn = createdOn;
    }

    /** Returns the id. */
    public int getId() { return id; }
    /** Sets the id. */
    public void setId(int id) { this.id = id; }
    /** Returns the username. */
    public String getUsername() { return username; }
    /** Returns the role. */
    public Role getRole() { return role; }
    /** Returns the display name. */
    public String getDisplayName() { return displayName; }
    /** Returns the phone number with its calling code, or null for an older account. */
    public String getPhone() { return phone; }
    /** Returns the password hash. */
    public String getPasswordHash() { return passwordHash; }
    /** Returns the password salt. */
    public String getPasswordSalt() { return passwordSalt; }
    /** Returns the security question. */
    public String getSecurityQuestion() { return securityQuestion; }
    /** Returns the answer hash. */
    public String getAnswerHash() { return answerHash; }
    /** Returns the answer salt. */
    public String getAnswerSalt() { return answerSalt; }
    /**
     * The renter row of a RENTER account. Null for an ADMIN.
     */
    public Integer getRenterId() { return renterId; }
    /** Returns the created on. */
    public LocalDate getCreatedOn() { return createdOn; }

    @Override
    public String toString() {
        return username + " (" + role + ")";
    }
}
