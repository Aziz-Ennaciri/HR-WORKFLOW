package ma.rh.ai.hr_workflow.user.repositories;

import ma.rh.ai.hr_workflow.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {
}
