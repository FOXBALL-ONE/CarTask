package top.foxball.setup

import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 配置引导模式的口令。
 *
 * 用固定口令而不是「首次访问者得」，是因为一台还没配置的机器往往正对着公网或办公网：
 * 端口一开，任何扫到它的人都能把系统配成自己的。口令印在启动日志里，等于把控制权和
 * 服务器/容器的日志读取权绑在一起——那本来就是能改这套部署的人。
 *
 * 口令也可以由 `SETUP_TOKEN` 显式指定：无人值守的编排环境里日志不好取，从密钥管理器注入更稳妥。
 */
@Component
class SetupToken(properties: SetupProperties) {
    /** 口令来自配置而非本次随机生成，横幅据此提示不同的排查方向。 */
    val fromConfiguration: Boolean = properties.token.trim().isNotEmpty()

    private val value: String = properties.token.trim().ifEmpty { randomValue() }

    /**
     * value：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun value(): String = value

    /** 常量时间比较，避免逐字符比较的耗时差把口令一位位试出来。 */
    fun matches(candidate: String?): Boolean {
        val provided = candidate?.trim().orEmpty()
        return provided.isNotEmpty() && MessageDigest.isEqual(
            provided.toByteArray(StandardCharsets.UTF_8),
            value.toByteArray(StandardCharsets.UTF_8),
        )
    }

    /**
     * 8 位随机口令。
     *
     * 字符表刻意去掉 `I/O/0/1`：口令要从终端日志里用眼睛抄到浏览器，这几组字形最容易抄错，
     * 而抄错的代价是反复怀疑自己而不是怀疑输入。
     */
    private fun randomValue(): String {
        val random = SecureRandom()
        return (1..LENGTH)
            .map { ALPHABET[random.nextInt(ALPHABET.length)] }
            .joinToString("")
    }

    private companion object {
        const val LENGTH = 8
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}
