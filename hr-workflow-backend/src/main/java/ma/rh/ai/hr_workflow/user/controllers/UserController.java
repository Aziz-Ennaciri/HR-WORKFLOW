package ma.rh.ai.hr_workflow.user.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.user.DTOs.CreateUserDTO;
import ma.rh.ai.hr_workflow.user.DTOs.LoginRequestDTO;
import ma.rh.ai.hr_workflow.user.DTOs.LoginResponseDTO;
import ma.rh.ai.hr_workflow.user.DTOs.UserResponseDTO;
import ma.rh.ai.hr_workflow.user.mappers.UserMapper;
import ma.rh.ai.hr_workflow.user.model.RoleName;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;
import ma.rh.ai.hr_workflow.user.service.IUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/api/v1/users")
@RestController
@RequiredArgsConstructor
public class UserController {
    private final IUserService userService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> registerUser(@Valid @RequestBody CreateUserDTO createUserDTO) {
        UserResponseDTO userResponseDTO = userService.register(createUserDTO);
        return new ResponseEntity<>(userResponseDTO, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> loginUser(@Valid @RequestBody LoginRequestDTO dto) {
        LoginResponseDTO login = userService.login(dto);
        return ResponseEntity.ok(login);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> updateUser(@PathVariable @Parameter(description = "User ID") Long id,
                                                      @Valid @RequestBody CreateUserDTO createUserDTO) {
        UserResponseDTO userResponseDTO = userService.updateUser(id, createUserDTO);
        return ResponseEntity.ok(userResponseDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> getUserId(@PathVariable @Parameter(description = "User ID") Long id) {
        UserResponseDTO userResponseDTO = userService.getUserById(id);
        return ResponseEntity.ok(userResponseDTO);
    }

    @GetMapping
    public ResponseEntity<List<UserResponseDTO>> getAllUsers() {
        List<UserResponseDTO> userResponseDTO = userService.getAllUsers();
        return ResponseEntity.ok(userResponseDTO);
    }

    @GetMapping("/by-role")
    @Operation(summary = "Get users by role", description = "Returns users with a specific role (e.g., ROLE_ADMIN, ROLE_RH)")
    public ResponseEntity<List<UserResponseDTO>> getUsersByRole(
            @RequestParam @Parameter(description = "Role name, e.g. ROLE_ADMIN") String role) {
        try {
            RoleName roleName = RoleName.valueOf(role.toUpperCase());
            List<User> users = userRepository.findByRoleName(roleName);
            List<UserResponseDTO> dtos = users.stream().map(userMapper::toResponseDTO).toList();
            return ResponseEntity.ok(dtos);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<UserResponseDTO> disableUser(@PathVariable @Parameter(description = "User ID") Long id) {
        userService.disableUser(id);
        return ResponseEntity.noContent().build();
    }
}