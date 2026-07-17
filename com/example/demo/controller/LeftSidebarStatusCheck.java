package com.example.demo.controller;

import com.example.demo.model.User;

import java.time.Instant;

public final class LeftSidebarStatusCheck {
    private LeftSidebarStatusCheck() {
    }

    public static void main(String[] args) {
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        User user = new User(1, "Test", "User");

        assertStatus(user, now, LeftSidebarController.UserStatus.STOPPED);
        user.setMonitoringStatus(true, false, now.minusSeconds(60));
        assertStatus(user, now, LeftSidebarController.UserStatus.CONNECTED);
        user.setMonitoringStatus(true, false, now.minusSeconds(601));
        assertStatus(user, now, LeftSidebarController.UserStatus.NOT_RESPONDING);
        user.setMonitoringStatus(true, true, now.minusSeconds(601));
        assertStatus(user, now, LeftSidebarController.UserStatus.ALERT_SENT);
    }

    private static void assertStatus(User user, Instant now, LeftSidebarController.UserStatus expected) {
        LeftSidebarController.UserStatus actual = LeftSidebarController.UserStatus.from(user, now);
        if (actual != expected) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
