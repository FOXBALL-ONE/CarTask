package top.foxball.setup

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.context.annotation.Import
import top.foxball.cartask.shared.ResponseBuilder

/**
 * 配置引导模式下的独立 Spring 应用。
 *
 * 刻意放在 `top.foxball.cartask` 之外。主应用的组件扫描以 `top.foxball.cartask` 为根，引导接口、
 * 配置口令、连接探测器这些 bean 一旦落进那个包，就会在一个已经配置好的生产系统上被一并扫出来——
 * 一个能写 `.env`、能建管理员的接口，不该存在「被主应用顺手打开」的可能。放到兄弟包，主应用连
 * 看见它们的机会都没有。
 *
 * 数据源、JPA、Redis 的自动配置必须排除：这套应用跑起来的时候，恰恰是这些配置还没有的时候，
 * 留着它们启动会直接失败，而启动失败就没人能打开引导页去填这些配置了。
 *
 * `UserDetailsServiceAutoConfiguration` 也排除，它会在日志里打一行「Using generated security
 * password」，引导期间没人需要这个内存用户，只会让人以为系统已经有个账号可用。
 */
@SpringBootApplication(
    exclude = [
        DataSourceAutoConfiguration::class,
        DataSourceTransactionManagerAutoConfiguration::class,
        JdbcTemplateAutoConfiguration::class,
        HibernateJpaAutoConfiguration::class,
        DataJpaRepositoriesAutoConfiguration::class,
        DataRedisAutoConfiguration::class,
        DataRedisRepositoriesAutoConfiguration::class,
        UserDetailsServiceAutoConfiguration::class,
    ],
)
@EnableConfigurationProperties(SetupProperties::class)
@Import(ResponseBuilder::class)
class SetupApplication
