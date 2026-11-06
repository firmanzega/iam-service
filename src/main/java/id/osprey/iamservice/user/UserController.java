package id.osprey.iamservice.user;

import id.osprey.iamservice.role.ReplaceRolesRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Page<UserResponse> list(@RequestParam(required = false) String q,
                                   @PageableDefault(size = 10, sort = ("id")) Pageable pageable) {

        return userService.search(q, pageable);
    }

    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @PatchMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody PatchUserRequest request) {
        return userService.patch(id, request);
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        userService.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activate(@PathVariable Long id) {
        userService.activate(id);
    }

    @PutMapping("/{id}/roles")
    public UserResponse updateRoles(@PathVariable Long id, @Valid @RequestBody ReplaceRolesRequest request) {
        return userService.replaceRoles(id, request);
    }

}
