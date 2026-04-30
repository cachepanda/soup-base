package dev.soupbase.api;

import dev.soupbase.api.dto.SessionRequest;
import dev.soupbase.api.dto.SessionResponse;
import dev.soupbase.domain.UserService;
import dev.soupbase.domain.model.User;
import dev.soupbase.infra.ClerkPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/session")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SessionResponse> session(
            @RequestBody @Valid SessionRequest request,
            @AuthenticationPrincipal ClerkPrincipal principal) {
        User user = userService.findOrCreate(principal.clerkId(), request.email());
        return ResponseEntity.ok(SessionResponse.from(user));
    }
}
