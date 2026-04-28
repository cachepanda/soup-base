package dev.soupbase.infra;

import java.security.Principal;

public record ClerkPrincipal(String clerkId) implements Principal {

    @Override
    public String getName() {
        return clerkId;
    }
}
