package ma.rh.ai.hr_workflow.user.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.common.PageResponse;
import ma.rh.ai.hr_workflow.config.SecurityUtils;
import ma.rh.ai.hr_workflow.user.DTOs.ChangeEmailDTO;
import ma.rh.ai.hr_workflow.user.DTOs.ChangePasswordDTO;
import ma.rh.ai.hr_workflow.user.DTOs.CreateUserDTO;
import ma.rh.ai.hr_workflow.user.DTOs.LoginRequestDTO;
import ma.rh.ai.hr_workflow.user.DTOs.LoginResponseDTO;
import ma.rh.ai.hr_workflow.user.DTOs.RefreshTokenRequestDTO;
import ma.rh.ai.hr_workflow.user.DTOs.UpdatePreferencesDTO;
import ma.rh.ai.hr_workflow.user.DTOs.UpdateProfileDTO;
import ma.rh.ai.hr_workflow.user.DTOs.UserResponseDTO;
import ma.rh.ai.hr_workflow.user.model.RoleName;
import ma.rh.ai.hr_workflow.user.service.IUserService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/api/v1/users")
@RestController
@RequiredArgsConstructor
public class UserController {
    private final IUserService userService;

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

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access token (the refresh token is rotated)")
    public LoginResponseDTO refresh(@Valid @RequestBody RefreshTokenRequestDTO dto) {
        return userService.refresh(dto.getRefreshToken());
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke a refresh token")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequestDTO dto) {
        userService.logout(dto.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDTO> updateUser(@PathVariable @Parameter(description = "User ID") Long id,
                                                      @Valid @RequestBody CreateUserDTO createUserDTO) {
        UserResponseDTO userResponseDTO = userService.updateUser(id, createUserDTO);
        return ResponseEntity.ok(userResponseDTO);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @access.isCurrentUser(#id)")
    public ResponseEntity<UserResponseDTO> getUserId(@PathVariable @Parameter(description = "User ID") Long id) {
        UserResponseDTO userResponseDTO = userService.getUserById(id);
        return ResponseEntity.ok(userResponseDTO);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserResponseDTO> getAllUsers(
            @PageableDefault(size = 20, sort = "email") Pageable pageable) {
        return userService.getAllUsers(pageable);
    }

    @GetMapping("/by-role")
    @Operation(summary = "Get users by role", description = "Returns users with a specific role (e.g., ROLE_ADMIN, ROLE_RH)")
    public ResponseEntity<List<UserResponseDTO>> getUsersByRole(
            @RequestParam @Parameter(description = "Role name, e.g. ROLE_ADMIN") String role) {
        String normalized = role.toUpperCase().startsWith("ROLE_") ? role.toUpperCase() : "ROLE_" + role.toUpperCase();
        RoleName roleName;
        try {
            roleName = RoleName.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown role: " + role);
        }
        return ResponseEntity.ok(userService.getUsersByRole(roleName));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDTO> disableUser(@PathVariable @Parameter(description = "User ID") Long id) {
        userService.disableUser(id);
        return ResponseEntity.noContent().build();
    }

    // ── /me endpoints (act on the authenticated user) ─────────────────────

    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<UserResponseDTO> getMe() {
        String email = currentUserEmail();
        if (email == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(userService.getMe(email));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update current user name")
    public ResponseEntity<UserResponseDTO> updateProfile(@Valid @RequestBody UpdateProfileDTO dto) {
        String email = currentUserEmail();
        if (email == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(userService.updateProfile(email, dto));
    }

    @PutMapping("/me/email")
    @Operation(summary = "Change current user email (requires current password)")
    public ResponseEntity<UserResponseDTO> changeEmail(@Valid @RequestBody ChangeEmailDTO dto) {
        String email = currentUserEmail();
        if (email == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(userService.changeEmail(email, dto));
    }

    @PutMapping("/me/password")
    @Operation(summary = "Change current user password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        String email = currentUserEmail();
        if (email == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        userService.changePassword(email, dto);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/preferences")
    @Operation(summary = "Update current user preferences")
    public ResponseEntity<UserResponseDTO> updatePreferences(@RequestBody UpdatePreferencesDTO dto) {
        String email = currentUserEmail();
        if (email == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(userService.updatePreferences(email, dto));
    }

    @DeleteMapping("/me")
    @Operation(summary = "Soft-delete current user account")
    public ResponseEntity<Void> deleteMe() {
        String email = currentUserEmail();
        if (email == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        userService.deleteMe(email);
        return ResponseEntity.noContent().build();
    }

    private String currentUserEmail() {
        return SecurityUtils.currentUserEmail();
    }
}