package id.osprey.iamservice.user;

import id.osprey.iamservice.role.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    /**
     * Deprecated findAll diganti ke search()
     * Tidak dihapus karena untuk belajar
     */
    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> search(String q, Pageable pageable) {

        Page<User> page = (q == null || q.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.search(q.trim(), pageable);

        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        User user = findOrThrow(id);

        return toResponse(user);

//        return userRepository.findById(id).map(this::toResponse);
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String hash = passwordEncoder.encode(request.password());
        User user = new User(request.username(), request.email(), hash);

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse update (Long id, UpdateUserRequest request) {
        User user = findOrThrow(id);
        checkVersion(user, request.version());
        user.changeUsername(request.username());
        user.changeEmail(request.email());

        return toResponse(userRepository.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findByRoleName(String role) {

        return userRepository.findByRoles_Name(role).stream().map(this::toResponse).collect(Collectors.toList());

    }

    @Transactional
    public UserResponse patch(Long id,  PatchUserRequest request) {

        User user = findOrThrow(id);
        checkVersion(user, request.version());
        if(request.username() != null) user.changeUsername(request.username());
        if(request.email() != null) user.changeEmail(request.email());

        return toResponse(userRepository.saveAndFlush(user));

    }

    @Transactional
    public void deactivate(Long id) {
        findOrThrow(id).deactivate();
    }

    @Transactional
    public void activate(Long id) {
        findOrThrow(id).activate();
    }

    @Transactional
    private void checkVersion(User user, Long version) {
        if (!user.getVersion().equals(version)) {
            throw new ObjectOptimisticLockingFailureException(User.class, user.getId());
        }
    }

    /**
     * - Tanpa helper, baris findById(...).orElseThrow(...) harus ditulis ulang di getById, update, patch, deactivate, dan activate.
     *      Dengan helper, aturan “user tidak ada berarti 404 dengan pesan ini” cukup ditulis di satu tempat.
     *      Kalau suatu hari pesannya ingin diubah, edit satu baris saja.
     * - Method ini private karena hanya dipakai di dalam UserService, dan kembaliannya User (entity), bukan DTO, jadi tidak boleh bocor keluar service.
     * - Satu hal yang berguna diketahui: karena dipanggil di dalam method @Transactional,
     *      entity yang dikembalikan tetap managed, sehingga dirty checking di deactivate dan activate tetap bekerja.
     */
    private User findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }


}