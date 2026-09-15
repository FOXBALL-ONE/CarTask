package top.foxball.cartask.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** 浏览器跨域访问白名单；生产环境通过环境变量覆盖本地开发 Origin。 */
@ConfigurationProperties(prefix = "cartask.security.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
    val allowedOriginPatterns: List<String> = emptyList(),
    val allowCredentials: Boolean = true,
) {
    /**
     * origins：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param String 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun origins(): List<String> = allowedOrigins.map(String::trim).filter(String::isNotEmpty).distinct()

    /**
     * originPatterns：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param String 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun originPatterns(): List<String> = allowedOriginPatterns.map(String::trim).filter(String::isNotEmpty).distinct()

    /**
     * validate：校验输入、状态或访问条件。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun validate() {
        if (allowCredentials) {
            require("*" !in origins() && "*" !in originPatterns()) {
                "CORS 允许凭据时不能使用全局通配 Origin"
            }
        }
    }
}
