package ma.rh.ai.hr_workflow.user.repositories;

import ma.rh.ai.hr_workflow.config.AbstractIntegrationTest;
import ma.rh.ai.hr_workflow.user.model.Role;
import ma.rh.ai.hr_workflow.user.model.RoleName;
import ma.rh.ai.hr_workflow.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserRepository Integration Tests")
class UserRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    private Role rhRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        rhRole = roleRepository.findByName(RoleName.ROLE_RH).orElseThrow();
        adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();
    }

    private User buildUser(String email, Role role, boolean enabled) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("pass"));
        u.setFirstName("First");
        u.setLastName("Last");
        u.setEnabled(enabled);
        u.setRoles(Set.of(role));
        return u;
    }

    @Nested
    @DisplayName("findByEmail — login query")
    class FindByEmail {

        @Test
        @DisplayName("returns the user matching the email")
        void findByEmail_returns_correct_user() {
             
            userRepository.save(buildUser("alice@test.com", rhRole, true));

             
            Optional<User> found = userRepository.findByEmail("alice@test.com");

             
            assertThat(found).isPresent();
            assertThat(found.get().getEmail()).isEqualTo("alice@test.com");
        }

        @Test
        @DisplayName("returns empty when email not found")
        void findByEmail_returns_empty_for_unknown() {
            assertThat(userRepository.findByEmail("unknown@test.com")).isEmpty();
        }

        @Test
        @DisplayName("disabled user is still findable by email")
        void disabled_user_is_findable_by_email() {
             
            userRepository.save(buildUser("disabled@test.com", rhRole, false));

            Optional<User> found = userRepository.findByEmail("disabled@test.com");

            assertThat(found).isPresent();
            assertThat(found.get().isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("existsByEmail — duplicate check")
    class ExistsByEmail {

        @Test
        @DisplayName("returns true when email already registered")
        void existsByEmail_returns_true_for_existing() {
             
            userRepository.save(buildUser("bob@test.com", rhRole, true));

            assertThat(userRepository.existsByEmail("bob@test.com")).isTrue();
        }

        @Test
        @DisplayName("returns false when email not yet registered")
        void existsByEmail_returns_false_for_new() {
            assertThat(userRepository.existsByEmail("new@test.com")).isFalse();
        }
    }

    @Nested
    @DisplayName("findAll — returns all users")
    class FindAll {

        @Test
        @DisplayName("findAll returns every inserted user")
        void findAll_returns_all_users() {
             
            userRepository.save(buildUser("u1@test.com", rhRole, true));
            userRepository.save(buildUser("u2@test.com", adminRole, true));

            List<User> all = userRepository.findAll();

             
            assertThat(all).hasSizeGreaterThanOrEqualTo(2);
            assertThat(all).extracting(User::getEmail).contains("u1@test.com", "u2@test.com");
        }
    }

    @Nested
    @DisplayName("role persistence")
    class RolePersistence {

        @Test
        @DisplayName("user is saved with assigned role and role survives a reload")
        void user_saved_with_role_survives_reload() {
             
            User saved = userRepository.save(buildUser("admin@test.com", adminRole, true));

             
            User reloaded = userRepository.findById(saved.getId()).orElseThrow();

             
            assertThat(reloaded.getRoles()).extracting(Role::getName)
                    .containsExactly(RoleName.ROLE_ADMIN);
        }
    }
}
