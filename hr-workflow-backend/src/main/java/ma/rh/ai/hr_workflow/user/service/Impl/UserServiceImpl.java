package ma.rh.ai.hr_workflow.user.service.Impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.config.JwtTokenProvider;
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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {
    private static final String USER_NOT_FOUND = "User not found";

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public UserResponseDTO register(CreateUserDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())){
            throw new RuntimeException("Email already exists: " + dto.getEmail());
        }

        User user = new User();
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());

        final RoleName roleName = determineRole(dto.getRole());

        Role userRole = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));

        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        user.setRoles(roles);

        User savedUser = userRepository.save(user);
        return userMapper.toResponseDTO(savedUser);
    }

    @Override
    public LoginResponseDTO login(LoginRequestDTO dto) {
        User user = userRepository.findByEmail(dto.getEmail()).orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!user.isEnabled()){
            throw new RuntimeException("Account is disabled");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtTokenProvider.generateToken(user);

        LoginResponseDTO response = new LoginResponseDTO();
        response.setToken(token);
        response.setUser(userMapper.toResponseDTO(user));

        return response;
    }

    @Override
    @Transactional
    public UserResponseDTO updateUser(Long id ,CreateUserDTO dto){
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found with Id"+id));
        if (!user.getEmail().equals(dto.getEmail())) {
            if (userRepository.existsByEmail(dto.getEmail())){
                throw new RuntimeException("Email already exists: " + dto.getEmail());
            }
            user.setEmail(dto.getEmail());
        }
        if (dto.getPassword() != null && dto.getPassword().isEmpty()){
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        return userMapper.toResponseDTO(user);
    }

    @Override
    public UserResponseDTO getUserById(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found with Id"+id));

        return userMapper.toResponseDTO(user);
    }

    @Override
    public List<UserResponseDTO> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream().map(userMapper::toResponseDTO).toList();
    }

    @Override
    public void disableUser(Long id){
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found with Id"+id));
        user.setEnabled(false);
    }

    @Override
    public UserResponseDTO getMe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException(USER_NOT_FOUND));
        return userMapper.toResponseDTO(user);
    }

    @Override
    @Transactional
    public UserResponseDTO updateProfile(String email, UpdateProfileDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException(USER_NOT_FOUND));
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        return userMapper.toResponseDTO(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponseDTO changeEmail(String email, ChangeEmailDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException(USER_NOT_FOUND));
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
                .orElseThrow(() -> new RuntimeException(USER_NOT_FOUND));
        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public UserResponseDTO updatePreferences(String email, UpdatePreferencesDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException(USER_NOT_FOUND));
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
                .orElseThrow(() -> new RuntimeException(USER_NOT_FOUND));
        user.setEnabled(false);
        userRepository.save(user);
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
