package fleetrent.controller;

import fleetrent.model.Account;
import fleetrent.model.Role;

/**
 * Who is signed in on this window. The controllers ask it before a job that only the owner
 * (the manager) may do, so the rule does not depend on which buttons the screen shows.
 */
public class Session {
    private Account current;

    /** Remembers the signed-in account. */
    public synchronized void start(Account account) {
        this.current = account;
    }

    /** Forgets the signed-in account (sign out). */
    public synchronized void end() {
        this.current = null;
    }

    /** Returns the signed-in account, or null when nobody is signed in. */
    public synchronized Account current() {
        return current;
    }

    /** Tells whether the signed-in account is the owner (manager). */
    public synchronized boolean isOwner() {
        return current != null && current.getRole() == Role.ADMIN;
    }

    /** Returns the id of the signed-in account, or 0 when nobody is signed in. */
    public synchronized int accountId() {
        return current == null ? 0 : current.getId();
    }

    /**
     * Returns the renter id of the signed-in renter. Stops an owner or a signed-out window with a
     * message the screen can show.
     */
    public synchronized int requireRenterId(String job) throws RuleException {
        if (current == null || current.getRole() != Role.RENTER || current.getRenterId() == null) {
            throw new RuleException("Only a signed-in renter can " + job + ".");
        }
        return current.getRenterId();
    }

    /**
     * Stops anyone who is not the owner. Renters and signed-out windows get a message the screen
     * can show.
     */
    public void requireOwner(String job) throws RuleException {
        if (!isOwner()) {
            throw new RuleException("Only the owner (manager) can " + job + ".");
        }
    }
}
