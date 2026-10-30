package id.osprey.iamservice.role;

import jakarta.persistence.*;

@Entity
@Table (name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(unique = true, nullable = false, length = 50)
    String name;


    public Role() {
    }
}
