package dev.soupbase.api;

import dev.soupbase.api.dto.CreateDatabaseRequest;
import dev.soupbase.api.dto.CreateDatabaseResponse;
import dev.soupbase.api.dto.ErrorResponse;
import dev.soupbase.domain.DatabaseService;
import dev.soupbase.domain.Result;
import dev.soupbase.domain.UserService;
import dev.soupbase.domain.model.Database;
import dev.soupbase.domain.model.User;
import dev.soupbase.infra.ClerkPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/databases")
public class DatabaseController {

    private final DatabaseService databaseService;
    private final UserService userService;

    public DatabaseController(DatabaseService databaseService, UserService userService) {
        this.databaseService = databaseService;
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> create(
            @RequestBody(required = false) CreateDatabaseRequest request,
            @AuthenticationPrincipal ClerkPrincipal principal) {

        Optional<User> userOpt = userService.findByClerkId(principal.clerkId());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String requestedName = (request != null) ? request.name() : null;
        Result<Database> result = databaseService.createDatabase(userOpt.get(), requestedName);

        return switch (result) {
            case Result.Ok<Database> ok ->
                    ResponseEntity.status(HttpStatus.ACCEPTED).body(CreateDatabaseResponse.from(ok.value()));
            case Result.Err<Database> err -> switch (err.code()) {
                case "DATABASE_LIMIT_REACHED" ->
                        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse(err.code()));
                case "DUPLICATE_NAME" ->
                        ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(err.code()));
                default ->
                        ResponseEntity.badRequest().body(new ErrorResponse(err.code()));
            };
        };
    }
}
