package id.osprey.iamservice.user;

import id.osprey.iamservice.role.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private UserResponse toResponse(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), roleNames);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Optional<UserResponse> findById(Long id) {
        return userRepository.findById(id).map(this::toResponse);
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        User user = new User(request.username(), request.email(), "placeholder");

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public Optional<UserResponse> update (Long id, UpdateUserRequest request) {
        return userRepository.findById(id).map(user -> {
            user.setUsername(request.username());
            user.setEmail(request.email());
            return toResponse(userRepository.save(user));
        });
    }

    @Transactional
    public boolean removeUser(Long id) {
        if (!userRepository.existsById(id)) {
            return false;
        }

        userRepository.deleteById(id);
        return true;
    }

}