package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.ViolationSetting

interface ViolationSettingRepository : JpaRepository<ViolationSetting, Long> {
    fun findFirstByOrderByIdAsc(): ViolationSetting?
}
