package ma.rh.ai.hr_workflow.user.model;

import jakarta.persistence.*;
import org.hibernate.Hibernate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Entity
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(unique = true)
    @ToString.Include
    private RoleName name;

    /** Identity is the database id; proxies compare equal to the entity they stand for. */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false;
        return id != null && id.equals(((Role) other).getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
