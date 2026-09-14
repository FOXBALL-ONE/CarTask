package top.foxball.setup

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path
import top.foxball.cartask.config.DotenvLoader

/**
 * 配置引导模式的可调项。
 *
 * [token] 是这套接口唯一的门禁。配置模式下数据库还不存在，没有账号体系可依赖，而引导接口能写配置、
 * 建管理员，谁先访问到端口谁就是这套系统的主人。留空时由 [SetupToken] 每次启动随机生成并打印在启动
 * 横幅里，让「能读到启动日志」成为动手配置的前提。
 *
 * 配置文件的位置不在这里开配置项：引导写下的必须是下次启动时 Spring 读的那一份，而
 * [top.foxball.cartask.config.DotenvEnvironmentPostProcessor] 认的是工作目录下的 `.env`。
 * 开一个 `env-file` 开关，就等于允许把引导写到一份永远不会被加载的文件里。
 */
@ConfigurationProperties(prefix = "app.setup")
data class SetupProperties(
    val token: String = "",
    val draftFile: String = ".setup-draft.json",
) {
    /** 与 [DotenvLoader.defaultPath] 同一个文件。 */
    fun envPath(): Path = DotenvLoader.defaultPath()

    /** 草稿文件，同样落在工作目录下；它只是引导过程中的暂存，不参与启动。 */
    fun draftPath(): Path = Path.of(draftFile).toAbsolutePath().normalize()
}
