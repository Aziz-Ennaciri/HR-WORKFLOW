package ma.rh.ai.hr_workflow.user.repositories;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import ma.rh.ai.hr_workflow.user.model.RoleName;
import ma.rh.ai.hr_workflow.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {
    boolean existsByEmail(@Email @NotBlank String email);

    Optional<User> findByEmail(@Email @NotBlank String email);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = :roleName AND u.enabled = true")
    List<User> findByRoleName(@Param("roleName") RoleName roleName);

}
