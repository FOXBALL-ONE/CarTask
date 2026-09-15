package top.foxball.cartask.config

import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import java.nio.file.Files
import java.nio.file.Path

/** Loads optional dotenv key/value pairs without overriding process environment values. */
object DotenvLoader {
    /** 重新指定配置文件位置的环境变量名（也可用同名 `-D` 系统属性）。 */
    const val ENV_FILE_VARIABLE = "APP_ENV_FILE"

    private const val DEFAULT_ENV_FILE = ".env"

    /**
     * 配置文件的位置：`APP_ENV_FILE` 指定时用它，否则是工作目录下的 `.env`。
     *
     * 引导模式判定、Spring 的属性注入、引导写入、管理员口令擦除四处都走这里——只要有一处认的是别的
     * 路径，就会出现「引导写完了但启动时读不到」这种只能靠翻代码才能定位的问题。
     *
     * 只认系统属性与环境变量，不认 `.env` 自己写的值：读 `.env` 之前得先知道 `.env` 在哪，
     * 那是个循环。
     */
    fun defaultPath(): Path {
        val configured = System.getProperty(ENV_FILE_VARIABLE)?.takeIf { it.isNotBlank() }
            ?: System.getenv(ENV_FILE_VARIABLE)?.takeIf { it.isNotBlank() }
        return Path.of(configured ?: DEFAULT_ENV_FILE).toAbsolutePath().normalize()
    }

    /**
     * 解析 `.env` 的键值对；文件不存在时返回空表。
     *
     * 除供 [addTo] 注入 Spring 使用外，配置引导的启动模式判定也在 Spring 起来之前读同一份文件，
     * 两边必须是同一套解析规则，否则会出现「引导写的 `.env` 这次读得到、下次读不到」这类错位。
     */
    fun read(path: Path): Map<String, String> {
        if (!Files.exists(path)) return emptyMap()
        return Files.readAllLines(path).asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { if (it.startsWith("export ")) it.removePrefix("export ").trim() else it }
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) return@mapNotNull null
                val key = line.substring(0, index).trim()
                val raw = line.substring(index + 1).trim()
                val value = if (raw.length >= 2 && raw.first() == '"' && raw.last() == '"') raw.substring(
                    1,
                    raw.length - 1
                ) else raw
                key to value
            }.toMap()
    }

    /**
     * addTo：创建、保存或初始化相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param environment 参与本次处理的输入参数。
     * @param path 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun addTo(environment: ConfigurableEnvironment, path: Path) {
        val values = read(path)
        if (values.isNotEmpty()) environment.propertySources.addLast(MapPropertySource("dotenv", values))
    }
}
