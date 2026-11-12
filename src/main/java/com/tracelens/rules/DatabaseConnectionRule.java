package com.tracelens.rules;

import com.tracelens.parser.Cause;

import java.util.List;

/**
 * Database cannot be reached. Needs its own logic: a SQLException can mean many
 * things (bad SQL, wrong table...), so the message decides if it is a connection problem.
 */
public class DatabaseConnectionRule implements Rule {

    private static final List<String> CONNECTION_WORDS = List.of(
            "Communications link failure", "Connection refused", "Unable to open JDBC connection",
            "Connection timed out", "Unknown host");

    @Override
    public boolean matches(Cause rootCause) {
        String name = rootCause.simpleType();
        boolean databaseError = name.contains("SQL") || name.contains("JDBC")
                || name.equals("CommunicationsException");
        if (!databaseError) {
            return false;
        }
        for (String words : CONNECTION_WORDS) {
            if (rootCause.message().contains(words)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String why() {
        return "Your app cannot reach the database server.";
    }

    @Override
    public List<String> fixes() {
        return List.of(
                "Check the database is running (Windows: services.msc, Linux: systemctl status mysql)",
                "Check host, port and database name in your config (e.g. application.properties)",
                "Check username/password, and that a firewall is not blocking the port");
    }
}
