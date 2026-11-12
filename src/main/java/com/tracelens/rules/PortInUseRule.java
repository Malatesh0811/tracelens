package com.tracelens.rules;

import com.tracelens.parser.Cause;

import java.util.List;

/**
 * Port already taken. Needs its own logic: different libraries throw different
 * exceptions for it (BindException, PortInUseException...), but the message is similar.
 */
public class PortInUseRule implements Rule {

    @Override
    public boolean matches(Cause rootCause) {
        return rootCause.simpleType().equals("BindException")
                || rootCause.message().contains("already in use");
    }

    @Override
    public String why() {
        return "Another program is already using the port your app wants.";
    }

    @Override
    public List<String> fixes() {
        return List.of(
                "Stop the old copy of your app (often it is still running in another terminal)",
                "Find who uses the port: netstat -ano | findstr :8080  then  taskkill /PID <pid> /F",
                "Or run on another port (e.g. server.port=8081)");
    }
}
