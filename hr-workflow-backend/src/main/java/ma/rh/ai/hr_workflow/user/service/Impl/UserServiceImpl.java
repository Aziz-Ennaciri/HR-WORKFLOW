package ma.rh.ai.hr_workflow.user.service.Impl;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.BadCredentialsException;
import ma.rh.ai.hr_workflow.exceptions.DuplicateResourceException;
import ma.rh.ai.hr_workflow.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.config.JwtTokenProvider;
import ma.rh.ai.hr_workflow.common.PageResponse;
import ma.rh.ai.hr_workflow.config.SecurityUtils;
import ma.rh.ai.hr_workflow.user.DTOs.ChangeEmailDTO;
import ma.rh.ai.hr_workflow.user.DTOs.ChangePasswordDTO;
import ma.rh.ai.hr_workflow.user.DTOs.CreateUserDTO;
import ma.rh.ai.hr_workflow.user.DTOs.LoginRequestDTO;
import ma.rh.ai.hr_workflow.user.DTOs.LoginResponseDTO;
import ma.rh.ai.hr_workflow.user.DTOs.UpdatePreferencesDTO;
import ma.rh.ai.hr_workflow.user.DTOs.UpdateProfileDTO;
import ma.rh.ai.hr_workflow.user.DTOs.UserResponseDTO;
import ma.rh.ai.hr_workflow.user.mappers.UserMapper;
import ma.rh.ai.hr_workflow.user.model.Role;
import ma.rh.ai.hr_workflow.user.model.RoleName;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.RoleRepository;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;
import ma.rh.ai.hr_workflow.user.service.IUserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements IUserService {
    private static final String USER_NOT_FOUND = "User not found";

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;

    @Override
    @Transactional
    public UserResponseDTO register(CreateUserDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())){
            throw new DuplicateResourceException("Email already exists: " + dto.getEmail());
        }

        User user = new User();
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());

        // Self-registration always yields ROLE_RH; only an authenticated admin may assign a role.
        final RoleName roleName = SecurityUtils.isAdmin() ? determineRole(dto.getRole()) : RoleName.ROLE_RH;

        Role userRole = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + roleName));

        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        user.setRoles(roles);

        User savedUser = userRepository.save(user);
        return userMapper.toResponseDTO(savedUser);
    }

    @Override
    @Transactional
    public LoginResponseDTO login(LoginRequestDTO dto) {
        loginAttemptService.checkAllowed(dto.getEmail());

        User user = userRepository.findByEmail(dto.getEmail()).orElse(null);
        // Same answer for unknown email and wrong password, so accounts cannot be enumerated
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            loginAttemptService.recordFailure(dto.getEmail());
            throw new BadCredentialsException("Invalid email or password");
        }
        if (!user.isEnabled()) {
            throw new DisabledException("Account is disabled");
        }
        loginAttemptService.recordSuccess(dto.getEmail());

        user.setLastLoginAt(LocalDateTime.now());
        return issueTokens(user);
    }

    @Override
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public LoginResponseDTO refresh(String refreshToken) {
        return issueTokens(refreshTokenService.consume(refreshToken));
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private LoginResponseDTO issueTokens(User user) {
        LoginResponseDTO response = new LoginResponseDTO();
        response.setToken(jwtTokenProvider.generateToken(user));
        response.setRefreshToken(refreshTokenService.issue(user));
        response.setExpiresIn(jwtTokenProvider.getExpirationSeconds());
        response.setUser(userMapper.toResponseDTO(user));
        return response;
    }

    @Override
    @Transactional
    public UserResponseDTO updateUser(Long id ,CreateUserDTO dto){
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found with Id"+id));
        if (!user.getEmail().equals(dto.getEmail())) {
            if (userRepository.existsByEmail(dto.getEmail())){
                throw new DuplicateResourceException("Email already exists: " + dto.getEmail());
            }
            user.setEmail(dto.getEmail());
        }
        if (dto.getPassword() != null && !dto.getPassword().isBlank()){
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        return userMapper.toResponseDTO(user);
    }

    @Override
    public UserResponseDTO getUserById(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found with Id"+id));

        return userMapper.toResponseDTO(user);
    }

    @Override
    public PageResponse<UserResponseDTO> getAllUsers(Pageable pageable) {
        return PageResponse.of(userRepository.findAll(pageable), userMapper::toResponseDTO);
    }

    @Override
    public List<UserResponseDTO> getUsersByRole(RoleName role) {
        return userRepository.findByRoleName(role).stream().map(userMapper::toResponseDTO).toList();
    }

    @Override
    @Transactional
    public void disableUser(Long id){
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found with Id"+id));
        user.setEnabled(false);
        userRepository.save(user);
        refreshTokenService.revokeAll(user);
    }

    @Override
    public UserResponseDTO getMe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
        return userMapper.toResponseDTO(user);
    }

    @Override
    @Transactional
    public UserResponseDTO updateProfile(String email, UpdateProfileDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        return userMapper.toResponseDTO(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponseDTO changeEmail(String email, ChangeEmailDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (userRepository.existsByEmail(dto.getNewEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }
        user.setEmail(dto.getNewEmail());
        return userMapper.toResponseDTO(userRepository.save(user));
    }

    @Override
    @Transactional
    public void changePassword(String email, ChangePasswordDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
        // Other devices must sign in again with the new password
        refreshTokenService.revokeAll(user);
    }

    @Override
    @Transactional
    public UserResponseDTO updatePreferences(String email, UpdatePreferencesDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
        if (dto.getTheme() != null) user.setTheme(dto.getTheme());
        if (dto.getLanguage() != null) user.setLanguage(dto.getLanguage());
        if (dto.getEmailNotificationsEnabled() != null) {
            user.setEmailNotificationsEnabled(dto.getEmailNotificationsEnabled());
        }
        return userMapper.toResponseDTO(userRepository.save(user));
    }

    @Override
    @Transactional
    public void deleteMe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
        user.setEnabled(false);
        userRepository.save(user);
        refreshTokenService.revokeAll(user);
    }

    private RoleName determineRole(String roleStr) {
        if (roleStr == null || roleStr.isEmpty()) {
            return RoleName.ROLE_RH;
        }

        try {
            String normalizedRole = roleStr.toUpperCase();
            if (!normalizedRole.startsWith("ROLE_")) {
                normalizedRole = "ROLE_" + normalizedRole;
            }
            return RoleName.valueOf(normalizedRole);
        } catch (IllegalArgumentException e) {
            return RoleName.ROLE_RH;
        }
    }
}
