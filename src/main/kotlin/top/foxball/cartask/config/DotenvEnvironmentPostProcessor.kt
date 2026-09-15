package top.foxball.cartask.config

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.env.ConfigurableEnvironment
import java.nio.file.Path

/**
 * 启动时把工作目录下的 `.env` 注入 [ConfigurableEnvironment]，使 `application.yaml` 里的
 * `${VAR:默认值}` 占位符不依赖 IDE 运行配置也能取到值。
 *
 * 生效时机由 `META-INF/spring.factories` 的注册决定：Spring Boot 在
 * `ConfigDataEnvironmentPostProcessor`（加载 `application.yaml`）之后、
 * `LoggingApplicationListener`（初始化日志系统）之前回调本类，因此日志相关配置同样能读到 `.env`。
 *
 * 优先级见 [DotenvLoader]：`.env` 位于所有属性源之后，OS 环境变量、命令行参数、
 * `application.yaml` 本身仍然优先。
 *
 * 激活 `test` profile（`@ActiveProfiles("test")`）时跳过：测试结论只应取决于仓库内的配置，
 * 不能被开发者本机 `.env`（已被 `.gitignore` 忽略）改变。
 *
 * @param dotenvPath `.env` 路径，默认相对进程工作目录（Gradle `bootRun`、IDE 运行配置与
 *   `java -jar` 均以项目根目录/启动目录为准）；文件不存在时静默跳过。测试可传入临时文件。
 */
class DotenvEnvironmentPostProcessor @JvmOverloads constructor(
    private val dotenvPath: Path = DotenvLoader.defaultPath(),
) : EnvironmentPostProcessor {
    /**
     * postProcessEnvironment：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param environment 参与本次处理的输入参数。
     * @param application 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        if ("test" in environment.activeProfiles) return
        DotenvLoader.addTo(environment, dotenvPath)
    }
}
