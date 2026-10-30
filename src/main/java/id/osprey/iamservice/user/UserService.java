package id.osprey.iamservice.user;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {


    public List<UserResponse> findAll() {
        return List.of(
                new UserResponse(1L,"user1","user1@mail.com"),
                new UserResponse(2L,"user2","user2@mail.com"),
                new UserResponse(3L,"user3","user3@mail.com"));

    }
}
