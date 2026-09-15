package top.foxball.setup

import top.foxball.cartask.config.DotenvLoader
import java.nio.file.Path

/**
 * 判定这次启动进「配置引导模式」还是正常运行。
 *
 * 判据是环境里有没有 `DB_URL`：这个键只有被人配置过才存在，`application.yaml` 里的
 * `${DB_URL:默认值}` 占位符本身不会在属性源里留下 `DB_URL` 这一项。所以「有没有配过数据库」
 * 与「要不要引导」是同一件事——没有它，数据源建不起来，正常模式连启动都启动不了。
 *
 * 不看 `.env` 文件是否存在：整个配置都走操作系统环境变量、或者由容器注入的部署同样有效。
 *
 * [FORCE_KEY] 供人工覆盖：改配置、换数据库这类操作要重新拉起引导时把它设成 `true`；
 * 反过来想跳过引导（例如配置由外部编排系统托管）就设成 `false`，缺配置时正常模式的启动失败
 * 信息比引导页更能说明问题。
 */
object SetupMode {
    /** 显式开关，取值同 `true/false`，也接受 `1/0`、`yes/no`、`on/off`。 */
    const val FORCE_KEY = "SETUP_MODE"

    /** 唯一能说明这套部署被配置过的键。 */
    const val DATABASE_URL_KEY = "DB_URL"

    /** 按工作目录下的 `.env` 加进程环境变量判定。 */
    fun required(): Boolean = required(effectiveEnvironment())

    /** 按给定的键值环境判定；与 [effectiveEnvironment] 解耦以便测试。 */
    fun required(environment: Map<String, String>): Boolean {
        when (environment[FORCE_KEY]?.trim()?.lowercase()) {
            "true", "1", "yes", "on" -> return true
            "false", "0", "no", "off" -> return false
        }
        return environment[DATABASE_URL_KEY].isNullOrBlank()
    }

    /**
     * 生效的配置来源：工作目录 `.env` < 进程环境变量 < `-D` 系统属性。
     *
     * 与 [top.foxball.cartask.config.DotenvEnvironmentPostProcessor] 把 `.env` 追加到属性源末尾
     * 之后的优先级一致，避免「启动模式判成一套、Spring 读到的又是另一套」。
     */
    fun effectiveEnvironment(envPath: Path = DotenvLoader.defaultPath()): Map<String, String> = buildMap {
        putAll(DotenvLoader.read(envPath))
        putAll(System.getenv())
        System.getProperty(FORCE_KEY)?.let { put(FORCE_KEY, it) }
        System.getProperty(DATABASE_URL_KEY)?.let { put(DATABASE_URL_KEY, it) }
    }
}
