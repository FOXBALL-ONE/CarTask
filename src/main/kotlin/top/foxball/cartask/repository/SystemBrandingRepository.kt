package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.SystemBranding

interface SystemBrandingRepository : JpaRepository<SystemBranding, Long> {
    
    
    fun findFirstByOrderByIdAsc(): SystemBranding?
}
