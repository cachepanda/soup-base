package dev.soupbase.domain;

import dev.soupbase.db.UserRepository;
import dev.soupbase.domain.model.User;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User findOrCreate(String clerkId, String email) {
        return userRepository.findOrCreate(clerkId, email);
    }
}
