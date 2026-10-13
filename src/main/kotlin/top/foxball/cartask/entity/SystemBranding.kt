package top.foxball.cartask.entity

import jakarta.persistence.*
import java.time.LocalDateTime


/**
 * 系统品牌：侧边栏与登录页顶部展示的图标和标题。全表只有一行。
 *
 * 图标以 data URL 内联存储：登录页在会话之外加载，<img> 也带不上 Bearer 头，
 * 走受保护的文件下载接口取不到图。
 */
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "system_branding")
class SystemBranding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    @Column(nullable = false, length = 64)
    lateinit var title: String
    
    @Column(name = "logo", columnDefinition = "text")
    var logo: String? = null
    
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime
    
    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime
}
