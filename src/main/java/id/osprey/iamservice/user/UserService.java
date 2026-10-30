package id.osprey.iamservice.user;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class UserService {
    private final Map<Long, UserResponse> users = new LinkedHashMap<>();
    private final AtomicLong idCounter  = new AtomicLong();

    public UserService() {
        addUser(new CreateUserRequest("user1","user1@mail.com"));
        addUser(new CreateUserRequest("user2","user2@mail.com"));
        addUser(new CreateUserRequest("user3","user3@mail.com"));
    }

    public UserResponse addUser(CreateUserRequest request) {
        long id = idCounter.incrementAndGet();
        UserResponse user = new UserResponse(id,request.username(), request.email());
        users.put(id, user);
        return user;
    }

    public List<UserResponse> findAll() {
        return new ArrayList<>(users.values());
    }

    public Optional<UserResponse> findById(Long id) {
        return Optional.ofNullable(users.get(id));
    }

    public boolean removeUser(Long id) {
        return users.remove(id) != null;
    }

    public Optional<UserResponse> updateUser(Long id, CreateUserRequest request) {
        if(!users.containsKey(id)) {
            return Optional.empty();
        }
        UserResponse updated = new  UserResponse(id,request.username(), request.email());
        users.put(id, updated);
        return Optional.of(updated);
    }
}