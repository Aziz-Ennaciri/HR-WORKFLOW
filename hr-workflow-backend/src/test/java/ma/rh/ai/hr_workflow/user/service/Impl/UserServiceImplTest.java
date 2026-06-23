package ma.rh.ai.hr_workflow.user.service.Impl;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl")
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private UserServiceImpl userService;
    

    private User buildEnabledUser(Long id, String email, String encodedPwd) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setPassword(encodedPwd);
        u.setFirstName("John");
        u.setLastName("Doe");
        u.setEnabled(true);
        Role role = new Role();
        role.setName(RoleName.ROLE_RH);
        u.setRoles(Set.of(role));
        return u;
    }

    private UserResponseDTO buildResponseDTO(Long id, String email) {
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(id);
        dto.setEmail(email);
        dto.setFirstName("John");
        dto.setLastName("Doe");
        dto.setRoles(Set.of("ROLE_RH"));
        return dto;
    }
    

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("happy path — creates user with default RH role when no role provided")
        void register_happyPath_noRole() {
             
            CreateUserDTO dto = new CreateUserDTO("alice@example.com", "secret", "Alice", "Smith", null);
            Role rhRole = new Role(); rhRole.setName(RoleName.ROLE_RH);
            User savedUser = buildEnabledUser(1L, "alice@example.com", "encoded");
            UserResponseDTO expectedResponse = buildResponseDTO(1L, "alice@example.com");

            when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
            when(passwordEncoder.encode("secret")).thenReturn("encoded");
            when(roleRepository.findByName(RoleName.ROLE_RH)).thenReturn(Optional.of(rhRole));
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(userMapper.toResponseDTO(savedUser)).thenReturn(expectedResponse);

             
            UserResponseDTO result = userService.register(dto);

             
            assertThat(result).isEqualTo(expectedResponse);
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("happy path — explicit ADMIN role is parsed correctly")
        void register_happyPath_adminRole() {
             
            CreateUserDTO dto = new CreateUserDTO("bob@example.com", "pass", "Bob", "K", "ADMIN");
            Role adminRole = new Role(); adminRole.setName(RoleName.ROLE_ADMIN);
            User savedUser = buildEnabledUser(2L, "bob@example.com", "hashed");
            UserResponseDTO expectedResponse = buildResponseDTO(2L, "bob@example.com");

            when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
            when(passwordEncoder.encode("pass")).thenReturn("hashed");
            when(roleRepository.findByName(RoleName.ROLE_ADMIN)).thenReturn(Optional.of(adminRole));
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(userMapper.toResponseDTO(savedUser)).thenReturn(expectedResponse);

             
            UserResponseDTO result = userService.register(dto);

             
            assertThat(result.getEmail()).isEqualTo("bob@example.com");
            verify(roleRepository).findByName(RoleName.ROLE_ADMIN);
        }

        @Test
        @DisplayName("happy path — role with ROLE_ prefix is accepted as-is")
        void register_happyPath_roleWithPrefix() {
             
            CreateUserDTO dto = new CreateUserDTO("carol@example.com", "pw", "Carol", "X", "ROLE_RH");
            Role rhRole = new Role(); rhRole.setName(RoleName.ROLE_RH);
            User savedUser = buildEnabledUser(3L, "carol@example.com", "enc");
            UserResponseDTO response = buildResponseDTO(3L, "carol@example.com");

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("enc");
            when(roleRepository.findByName(RoleName.ROLE_RH)).thenReturn(Optional.of(rhRole));
            when(userRepository.save(any())).thenReturn(savedUser);
            when(userMapper.toResponseDTO(savedUser)).thenReturn(response);

             
            UserResponseDTO result = userService.register(dto);

             
            assertNotNull(result);
            verify(roleRepository).findByName(RoleName.ROLE_RH);
        }

        @Test
        @DisplayName("edge case — unknown role string falls back to ROLE_RH")
        void register_unknownRole_fallsBackToRH() {
             
            CreateUserDTO dto = new CreateUserDTO("d@example.com", "pw", "D", "E", "DOES_NOT_EXIST");
            Role rhRole = new Role(); rhRole.setName(RoleName.ROLE_RH);
            User savedUser = buildEnabledUser(4L, "d@example.com", "enc");
            UserResponseDTO response = buildResponseDTO(4L, "d@example.com");

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("enc");
            when(roleRepository.findByName(RoleName.ROLE_RH)).thenReturn(Optional.of(rhRole));
            when(userRepository.save(any())).thenReturn(savedUser);
            when(userMapper.toResponseDTO(any())).thenReturn(response);

             
            userService.register(dto);

            verify(roleRepository).findByName(RoleName.ROLE_RH);
        }

        @Test
        @DisplayName("exception — email already in use throws RuntimeException")
        void register_duplicateEmail_throws() {
             
            CreateUserDTO dto = new CreateUserDTO("dup@example.com", "pw", "D", "P", null);
            when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

               
            RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.register(dto));
            assertThat(ex.getMessage()).contains("Email already exists");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("exception — role not found in DB throws RuntimeException")
        void register_roleNotFound_throws() {
             
            CreateUserDTO dto = new CreateUserDTO("e@example.com", "pw", "E", "F", null);
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("enc");
            when(roleRepository.findByName(RoleName.ROLE_RH)).thenReturn(Optional.empty());

               
            RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.register(dto));
            assertThat(ex.getMessage()).contains("Role not found");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("edge case — empty role string falls back to ROLE_RH")
        void register_emptyRoleString_fallsBackToRH() {
             
            CreateUserDTO dto = new CreateUserDTO("g@example.com", "pw", "G", "H", "");
            Role rhRole = new Role(); rhRole.setName(RoleName.ROLE_RH);
            User savedUser = buildEnabledUser(5L, "g@example.com", "enc");
            UserResponseDTO response = buildResponseDTO(5L, "g@example.com");

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("enc");
            when(roleRepository.findByName(RoleName.ROLE_RH)).thenReturn(Optional.of(rhRole));
            when(userRepository.save(any())).thenReturn(savedUser);
            when(userMapper.toResponseDTO(any())).thenReturn(response);

             
            userService.register(dto);

             
            verify(roleRepository).findByName(RoleName.ROLE_RH);
        }

        @Test
        @DisplayName("edge case — password is encoded before saving")
        void register_passwordIsEncoded() {
             
            CreateUserDTO dto = new CreateUserDTO("h@example.com", "rawPassword", "H", "I", null);
            Role rhRole = new Role(); rhRole.setName(RoleName.ROLE_RH);
            User savedUser = buildEnabledUser(6L, "h@example.com", "ENCODED_PW");
            UserResponseDTO response = buildResponseDTO(6L, "h@example.com");

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode("rawPassword")).thenReturn("ENCODED_PW");
            when(roleRepository.findByName(any())).thenReturn(Optional.of(rhRole));
            when(userRepository.save(any())).thenReturn(savedUser);
            when(userMapper.toResponseDTO(any())).thenReturn(response);

            userService.register(dto);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getPassword()).isEqualTo("ENCODED_PW");
        }
    }



    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("happy path — valid credentials return JWT and user DTO")
        void login_happyPath() {
             
            LoginRequestDTO dto = new LoginRequestDTO("alice@example.com", "rawPwd");
            User user = buildEnabledUser(1L, "alice@example.com", "encoded");
            UserResponseDTO userResponse = buildResponseDTO(1L, "alice@example.com");

            when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("rawPwd", "encoded")).thenReturn(true);
            when(jwtTokenProvider.generateToken(user)).thenReturn("jwt-token");
            when(userMapper.toResponseDTO(user)).thenReturn(userResponse);

             
            LoginResponseDTO result = userService.login(dto);

             
            assertThat(result.getToken()).isEqualTo("jwt-token");
            assertThat(result.getUser()).isEqualTo(userResponse);
        }

        @Test
        @DisplayName("exception — user not found throws RuntimeException")
        void login_userNotFound_throws() {
             
            LoginRequestDTO dto = new LoginRequestDTO("nobody@example.com", "pw");
            when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

               
            RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.login(dto));
            assertThat(ex.getMessage()).contains("Invalid email or password");
        }

        @Test
        @DisplayName("exception — account disabled throws RuntimeException")
        void login_accountDisabled_throws() {
             
            LoginRequestDTO dto = new LoginRequestDTO("dis@example.com", "pw");
            User user = buildEnabledUser(2L, "dis@example.com", "enc");
            user.setEnabled(false);
            when(userRepository.findByEmail("dis@example.com")).thenReturn(Optional.of(user));

               
            RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.login(dto));
            assertThat(ex.getMessage()).contains("Account is disabled");
            verify(jwtTokenProvider, never()).generateToken(any());
        }

        @Test
        @DisplayName("exception — wrong password throws RuntimeException")
        void login_wrongPassword_throws() {
             
            LoginRequestDTO dto = new LoginRequestDTO("alice@example.com", "wrong");
            User user = buildEnabledUser(1L, "alice@example.com", "encoded");
            when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

               
            RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.login(dto));
            assertThat(ex.getMessage()).contains("Invalid email or password");
            verify(jwtTokenProvider, never()).generateToken(any());
        }
    }


    @Nested
    @DisplayName("updateUser()")
    class UpdateUser {

        @Test
        @DisplayName("happy path — name updated, email unchanged")
        void updateUser_happyPath_sameEmail() {
             
            User user = buildEnabledUser(1L, "alice@example.com", "enc");
            CreateUserDTO dto = new CreateUserDTO("alice@example.com", null, "Alicia", "Smith", null);
            UserResponseDTO response = buildResponseDTO(1L, "alice@example.com");

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userMapper.toResponseDTO(user)).thenReturn(response);

             
            UserResponseDTO result = userService.updateUser(1L, dto);

             
            assertThat(result).isEqualTo(response);
            verify(userRepository, never()).existsByEmail(anyString());
        }

        @Test
        @DisplayName("happy path — email changed to an available address")
        void updateUser_happyPath_emailChanged() {
             
            User user = buildEnabledUser(1L, "old@example.com", "enc");
            CreateUserDTO dto = new CreateUserDTO("new@example.com", null, "Old", "Name", null);
            UserResponseDTO response = buildResponseDTO(1L, "new@example.com");

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
            when(userMapper.toResponseDTO(user)).thenReturn(response);

             
            UserResponseDTO result = userService.updateUser(1L, dto);

             
            assertThat(result).isNotNull();
            assertThat(user.getEmail()).isEqualTo("new@example.com");
        }

        @Test
        @DisplayName("edge case — non-empty password is NOT encoded (isEmpty() check in source)")
        void updateUser_nonEmptyPassword_notEncoded() {
            User user = buildEnabledUser(1L, "a@example.com", "old-enc");
            CreateUserDTO dto = new CreateUserDTO("a@example.com", "somePass", "A", "B", null);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userMapper.toResponseDTO(user)).thenReturn(buildResponseDTO(1L, "a@example.com"));

            userService.updateUser(1L, dto);

            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("edge case — empty-string password is encoded and set")
        void updateUser_emptyPassword_isEncoded() {
            User user = buildEnabledUser(1L, "a@example.com", "old-enc");
            CreateUserDTO dto = new CreateUserDTO("a@example.com", "", "A", "B", null);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("")).thenReturn("encoded-empty");
            when(userMapper.toResponseDTO(user)).thenReturn(buildResponseDTO(1L, "a@example.com"));

            userService.updateUser(1L, dto);

             
            verify(passwordEncoder).encode("");
            assertThat(user.getPassword()).isEqualTo("encoded-empty");
        }

        @Test
        @DisplayName("exception — user not found throws RuntimeException")
        void updateUser_notFound_throws() {
             
            CreateUserDTO dto = new CreateUserDTO("x@example.com", null, "X", "Y", null);
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> userService.updateUser(99L, dto));
            assertThat(ex.getMessage()).contains("User not found");
        }

        @Test
        @DisplayName("exception — new email already taken throws RuntimeException")
        void updateUser_newEmailAlreadyTaken_throws() {
             
            User user = buildEnabledUser(1L, "old@example.com", "enc");
            CreateUserDTO dto = new CreateUserDTO("taken@example.com", null, "O", "N", null);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

               
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> userService.updateUser(1L, dto));
            assertThat(ex.getMessage()).contains("Email already exists");
        }
    }


    @Nested
    @DisplayName("getUserById()")
    class GetUserById {

        @Test
        @DisplayName("happy path — returns mapped DTO")
        void getUserById_happyPath() {
             
            User user = buildEnabledUser(1L, "a@example.com", "enc");
            UserResponseDTO response = buildResponseDTO(1L, "a@example.com");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userMapper.toResponseDTO(user)).thenReturn(response);

             
            UserResponseDTO result = userService.getUserById(1L);

             
            assertThat(result).isEqualTo(response);
        }

        @Test
        @DisplayName("exception — id not found throws RuntimeException")
        void getUserById_notFound_throws() {
             
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

               
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> userService.getUserById(99L));
            assertThat(ex.getMessage()).contains("User not found");
        }
    }



    @Nested
    @DisplayName("getAllUsers()")
    class GetAllUsers {

        @Test
        @DisplayName("happy path — returns list of mapped DTOs")
        void getAllUsers_happyPath() {
             
            User u1 = buildEnabledUser(1L, "a@example.com", "enc1");
            User u2 = buildEnabledUser(2L, "b@example.com", "enc2");
            UserResponseDTO r1 = buildResponseDTO(1L, "a@example.com");
            UserResponseDTO r2 = buildResponseDTO(2L, "b@example.com");

            when(userRepository.findAll()).thenReturn(List.of(u1, u2));
            when(userMapper.toResponseDTO(u1)).thenReturn(r1);
            when(userMapper.toResponseDTO(u2)).thenReturn(r2);

             
            List<UserResponseDTO> result = userService.getAllUsers();

             
            assertThat(result).containsExactly(r1, r2);
        }

        @Test
        @DisplayName("edge case — empty repository returns empty list")
        void getAllUsers_empty_returnsEmptyList() {
             
            when(userRepository.findAll()).thenReturn(List.of());

             
            List<UserResponseDTO> result = userService.getAllUsers();

             
            assertThat(result).isEmpty();
        }
    }


    @Nested
    @DisplayName("disableUser()")
    class DisableUser {

        @Test
        @DisplayName("happy path — user is marked disabled")
        void disableUser_happyPath() {
             
            User user = buildEnabledUser(1L, "a@example.com", "enc");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

             
            userService.disableUser(1L);

             
            assertThat(user.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("exception — user not found throws RuntimeException")
        void disableUser_notFound_throws() {
             
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

               
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> userService.disableUser(99L));
            assertThat(ex.getMessage()).contains("User not found");
        }
    }



    @Nested
    @DisplayName("getMe()")
    class GetMe {

        @Test
        @DisplayName("happy path — returns mapped DTO for existing email")
        void getMe_happyPath() {
             
            User user = buildEnabledUser(1L, "me@example.com", "enc");
            UserResponseDTO response = buildResponseDTO(1L, "me@example.com");
            when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(user));
            when(userMapper.toResponseDTO(user)).thenReturn(response);

             
            UserResponseDTO result = userService.getMe("me@example.com");

             
            assertThat(result).isEqualTo(response);
        }

        @Test
        @DisplayName("exception — email not found throws RuntimeException")
        void getMe_notFound_throws() {
             
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

               
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> userService.getMe("ghost@example.com"));
            assertThat(ex.getMessage()).contains("User not found");
        }
    }


    @Nested
    @DisplayName("updateProfile()")
    class UpdateProfile {

        @Test
        @DisplayName("happy path — first/last name updated and saved")
        void updateProfile_happyPath() {
             
            User user = buildEnabledUser(1L, "u@example.com", "enc");
            UpdateProfileDTO dto = new UpdateProfileDTO();
            dto.setFirstName("NewFirst");
            dto.setLastName("NewLast");
            UserResponseDTO response = buildResponseDTO(1L, "u@example.com");

            when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));
            when(userRepository.save(user)).thenReturn(user);
            when(userMapper.toResponseDTO(user)).thenReturn(response);

             
            UserResponseDTO result = userService.updateProfile("u@example.com", dto);

             
            assertThat(result).isEqualTo(response);
            assertThat(user.getFirstName()).isEqualTo("NewFirst");
            assertThat(user.getLastName()).isEqualTo("NewLast");
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("exception — user not found throws RuntimeException")
        void updateProfile_notFound_throws() {
             
            when(userRepository.findByEmail("none@example.com")).thenReturn(Optional.empty());
            UpdateProfileDTO dto = new UpdateProfileDTO();
            dto.setFirstName("A");
            dto.setLastName("B");

               
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> userService.updateProfile("none@example.com", dto));
            assertThat(ex.getMessage()).contains("User not found");
            verify(userRepository, never()).save(any());
        }
    }


    @Nested
    @DisplayName("changeEmail()")
    class ChangeEmail {

        @Test
        @DisplayName("happy path — correct password and available new email updates email")
        void changeEmail_happyPath() {
             
            User user = buildEnabledUser(1L, "old@example.com", "encoded");
            ChangeEmailDTO dto = new ChangeEmailDTO();
            dto.setCurrentPassword("rawPwd");
            dto.setNewEmail("new@example.com");
            UserResponseDTO response = buildResponseDTO(1L, "new@example.com");

            when(userRepository.findByEmail("old@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("rawPwd", "encoded")).thenReturn(true);
            when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
            when(userRepository.save(user)).thenReturn(user);
            when(userMapper.toResponseDTO(user)).thenReturn(response);

             
            UserResponseDTO result = userService.changeEmail("old@example.com", dto);

             
            assertThat(result).isEqualTo(response);
            assertThat(user.getEmail()).isEqualTo("new@example.com");
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("exception — user not found throws RuntimeException")
        void changeEmail_userNotFound_throws() {
             
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
            ChangeEmailDTO dto = new ChangeEmailDTO();
            dto.setCurrentPassword("pw");
            dto.setNewEmail("x@example.com");

               
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> userService.changeEmail("ghost@example.com", dto));
            assertThat(ex.getMessage()).contains("User not found");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("exception — wrong current password throws IllegalArgumentException")
        void changeEmail_wrongPassword_throws() {
             
            User user = buildEnabledUser(1L, "u@example.com", "encoded");
            ChangeEmailDTO dto = new ChangeEmailDTO();
            dto.setCurrentPassword("wrong");
            dto.setNewEmail("new@example.com");

            when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

               
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> userService.changeEmail("u@example.com", dto));
            assertThat(ex.getMessage()).contains("Current password is incorrect");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("exception — new email already in use throws IllegalArgumentException")
        void changeEmail_emailAlreadyInUse_throws() {
             
            User user = buildEnabledUser(1L, "u@example.com", "encoded");
            ChangeEmailDTO dto = new ChangeEmailDTO();
            dto.setCurrentPassword("rawPwd");
            dto.setNewEmail("taken@example.com");

            when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("rawPwd", "encoded")).thenReturn(true);
            when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

               
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> userService.changeEmail("u@example.com", dto));
            assertThat(ex.getMessage()).contains("Email already in use");
            verify(userRepository, never()).save(any());
        }
    }


    @Nested
    @DisplayName("changePassword()")
    class ChangePassword {

        @Test
        @DisplayName("happy path — correct current password encodes and saves new password")
        void changePassword_happyPath() {
             
            User user = buildEnabledUser(1L, "u@example.com", "oldEncoded");
            ChangePasswordDTO dto = new ChangePasswordDTO();
            dto.setCurrentPassword("oldRaw");
            dto.setNewPassword("newPassword1");

            when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("oldRaw", "oldEncoded")).thenReturn(true);
            when(passwordEncoder.encode("newPassword1")).thenReturn("newEncoded");
            when(userRepository.save(user)).thenReturn(user);

             
            userService.changePassword("u@example.com", dto);

             
            assertThat(user.getPassword()).isEqualTo("newEncoded");
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("exception — user not found throws RuntimeException")
        void changePassword_userNotFound_throws() {
             
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
            ChangePasswordDTO dto = new ChangePasswordDTO();
            dto.setCurrentPassword("pw");
            dto.setNewPassword("newpass12");

               
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> userService.changePassword("ghost@example.com", dto));
            assertThat(ex.getMessage()).contains("User not found");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("exception — wrong current password throws IllegalArgumentException")
        void changePassword_wrongCurrentPassword_throws() {
             
            User user = buildEnabledUser(1L, "u@example.com", "encoded");
            ChangePasswordDTO dto = new ChangePasswordDTO();
            dto.setCurrentPassword("wrong");
            dto.setNewPassword("newpass12");

            when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

               
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> userService.changePassword("u@example.com", dto));
            assertThat(ex.getMessage()).contains("Current password is incorrect");
            verify(userRepository, never()).save(any());
        }
    }



    @Nested
    @DisplayName("updatePreferences()")
    class UpdatePreferences {

        @Test
        @DisplayName("happy path — all preferences updated when all fields non-null")
        void updatePreferences_allFieldsPresent() {
             
            User user = buildEnabledUser(1L, "u@example.com", "enc");
            UpdatePreferencesDTO dto = new UpdatePreferencesDTO();
            dto.setTheme("dark");
            dto.setLanguage("fr");
            dto.setEmailNotificationsEnabled(false);
            UserResponseDTO response = buildResponseDTO(1L, "u@example.com");

            when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));
            when(userRepository.save(user)).thenReturn(user);
            when(userMapper.toResponseDTO(user)).thenReturn(response);

             
            UserResponseDTO result = userService.updatePreferences("u@example.com", dto);

             
            assertThat(result).isEqualTo(response);
            assertThat(user.getTheme()).isEqualTo("dark");
            assertThat(user.getLanguage()).isEqualTo("fr");
            assertThat(user.isEmailNotificationsEnabled()).isFalse();
        }

        @Test
        @DisplayName("edge case — null fields are skipped, existing values preserved")
        void updatePreferences_nullFieldsSkipped() {
             
            User user = buildEnabledUser(1L, "u@example.com", "enc");
            user.setTheme("light");
            user.setLanguage("en");
            UpdatePreferencesDTO dto = new UpdatePreferencesDTO();
            UserResponseDTO response = buildResponseDTO(1L, "u@example.com");

            when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));
            when(userRepository.save(user)).thenReturn(user);
            when(userMapper.toResponseDTO(user)).thenReturn(response);

             
            userService.updatePreferences("u@example.com", dto);

            assertThat(user.getTheme()).isEqualTo("light");
            assertThat(user.getLanguage()).isEqualTo("en");
        }

        @Test
        @DisplayName("exception — user not found throws RuntimeException")
        void updatePreferences_notFound_throws() {
             
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
            UpdatePreferencesDTO dto = new UpdatePreferencesDTO();

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> userService.updatePreferences("ghost@example.com", dto));
            assertThat(ex.getMessage()).contains("User not found");
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteMe()")
    class DeleteMe {

        @Test
        @DisplayName("happy path — user account is soft-disabled")
        void deleteMe_happyPath() {
             
            User user = buildEnabledUser(1L, "u@example.com", "enc");
            when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));
            when(userRepository.save(user)).thenReturn(user);

             
            userService.deleteMe("u@example.com");

             
            assertThat(user.isEnabled()).isFalse();
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("exception — user not found throws RuntimeException")
        void deleteMe_notFound_throws() {
             
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

               
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> userService.deleteMe("ghost@example.com"));
            assertThat(ex.getMessage()).contains("User not found");
            verify(userRepository, never()).save(any());
        }
    }
}
