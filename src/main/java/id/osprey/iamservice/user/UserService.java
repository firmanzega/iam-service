package id.osprey.iamservice.user;

import id.osprey.iamservice.role.Role;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private UserResponse toResponse(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.isActive(), user.getVersion() ,roleNames);
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
        String hash = passwordEncoder.encode(request.password());
        User user = new User(request.username(), request.email(), hash);

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public Optional<UserResponse> update (Long id, UpdateUserRequest request) {
        return userRepository.findById(id).map(user -> {
            checkVersion(user, request.version());
            user.changeUsername(request.username());
            user.changeEmail(request.email());
            return toResponse(userRepository.saveAndFlush(user));
        });
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findByRoleName(String role) {

        return userRepository.findByRoles_Name(role).stream().map(this::toResponse).collect(Collectors.toList());

    }

    @Transactional
    public Optional<UserResponse> patch(Long id,  PatchUserRequest request) {
        return userRepository.findById(id).map(user -> {
            checkVersion(user, request.version());
            if (request.username() != null) user.changeUsername(request.username());
            if (request.email() != null) user.changeEmail(request.email());

            return toResponse(userRepository.saveAndFlush(user));
        });

    }

    @Transactional
    public boolean deactivate(Long id) {
        return userRepository.findById(id).map(user -> {
            user.deactivate();
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean activate(Long id) {
        return userRepository.findById(id).map(user -> {
            user.activate();
            return true;
        }).orElse(false);
    }

    @Transactional

    private void checkVersion(User user, Long version) {
        if (!user.getVersion().equals(version)) {
            throw new ObjectOptimisticLockingFailureException(User.class, user.getId());
        }
    }


}