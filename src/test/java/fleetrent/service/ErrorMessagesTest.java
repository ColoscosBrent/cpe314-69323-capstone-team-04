package fleetrent.service;

import fleetrent.dao.Database;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class ErrorMessagesTest {
    @Test
    void noInternetIsExplained() {
        String m = ErrorMessages.forApi("NHTSA", new UnknownHostException("vpic.nhtsa.dot.gov"));
        assertTrue(m.contains("No internet"));
        assertTrue(m.contains("NHTSA"));
    }

    @Test
    void wrappedCauseIsFound() {
        String m = ErrorMessages.forApi("NHTSA", new RuntimeException("x", new UnknownHostException("h")));
        assertTrue(m.contains("No internet"));
    }

    @Test
    void timeoutAndConnectAreDifferent() {
        assertTrue(ErrorMessages.forApi("NHTSA", new HttpTimeoutException("t")).contains("too long"));
        assertTrue(ErrorMessages.forApi("NHTSA", new ConnectException("c")).contains("Could not connect"));
    }

    @Test
    void httpStatusAndBadAnswer() {
        assertTrue(ErrorMessages.forApi("NHTSA", new IOException("NHTSA answered with HTTP 503")).contains("problems"));
        assertTrue(ErrorMessages.forApi("NHTSA", new IOException("NHTSA answered with HTTP 404")).contains("refused"));
        assertTrue(ErrorMessages.forApi("NHTSA",
                new IOException("NHTSA sent an answer we could not read: not valid JSON")).contains("could not read"));
    }

    @Test
    void nullErrorStillGivesAMessage() {
        assertTrue(ErrorMessages.forApi("NHTSA", null).contains("NHTSA"));
    }

    @Test
    void databaseMessagesMentionTheFile() {
        assertTrue(ErrorMessages.forDatabase("saving the vehicle", new SQLException("database is locked"))
                .contains("fleetrent.db"));
        assertTrue(ErrorMessages.forDatabase("saving the vehicle", new SQLException("FOREIGN KEY constraint failed"))
                .contains("does not exist"));
        assertTrue(ErrorMessages.forDatabase("opening", new SQLException("unable to open database file"))
                .contains("cannot be opened"));
        assertTrue(ErrorMessages.forDatabase("opening", new SQLException()).contains("Database error"));
    }
}
