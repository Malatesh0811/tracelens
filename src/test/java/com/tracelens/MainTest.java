package com.tracelens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @Test
    void usageShowsVersion() {
        assertTrue(Main.usage().contains(Main.VERSION));
    }

    @Test
    void usageListsAllCommands() {
        String usage = Main.usage();
        assertTrue(usage.contains("analyze"));
        assertTrue(usage.contains("solve"));
        assertTrue(usage.contains("history"));
    }
}
