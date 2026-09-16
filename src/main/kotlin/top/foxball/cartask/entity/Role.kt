package top.foxball.cartask.entity

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.*


@Entity
@Table(name = "roles")
class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    
    @Column(nullable = false, unique = true, length = 64)
    lateinit var name: String
    
    
    @Column(length = 255)
    var description: String? = null
    
    
    @Column(nullable = false)
    var enabled: Boolean = true
    
    
    @Transient
    @JsonProperty("code")
    var documentCode: String? = null
    
    @JsonProperty("sort")
    @Column(name = "display_sort")
    var documentSort: Int? = null
    
    @JsonProperty("remark")
    @Column(name = "display_remark", length = 255)
    var documentRemark: String? = null
    
    @Transient
    @JsonProperty("status")
    var documentStatus: Int? = null
    
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "role_permissions",
        joinColumns = [JoinColumn(name = "role_id")],
        inverseJoinColumns = [JoinColumn(name = "permission_id")],
    )
    var permissions: MutableSet<Permission> = linkedSetOf()
}
