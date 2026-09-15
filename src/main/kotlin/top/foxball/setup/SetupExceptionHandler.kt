package top.foxball.setup

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

/**
 * 引导接口的错误出口。
 *
 * 与主应用那套 `GlobalExceptionHandler` 是两份：配置模式下没有数据库、没有审计表，主应用那套依赖的
 * 东西一样都没有，硬要共用只会让引导页在连不上库时连错误提示都拿不到。
 *
 * 未预期的异常把原因一并回给前端。这些请求已经过了配置口令，能看到的都是这套部署的主人，
 * 让他对着「服务器内部错误」去翻日志，只会让配置在最后一公里卡住。
 */
@RestControllerAdvice
class SetupExceptionHandler(
    private val responseBuilder: ResponseBuilder,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(SetupException::class)
            /**
             * handleSetup：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param exception 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun handleSetup(exception: SetupException): ResponseEntity<Response> =
        responseBuilder.badRequest().message(exception.message).build()

    @ExceptionHandler(
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class,
    )
            /**
             * handleBadRequest：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param exception 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun handleBadRequest(exception: Exception): ResponseEntity<Response> =
        responseBuilder.badRequest().message("请求参数不完整或格式不正确：${exception.message}").build()

    @ExceptionHandler(Exception::class)
            /**
             * handleUnexpected：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param exception 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun handleUnexpected(exception: Exception): ResponseEntity<Response> {
        logger.error("配置引导接口异常", exception)
        return responseBuilder.exception()
            .message("服务器内部错误：${exception.message ?: exception.javaClass.simpleName}").build()
    }
}
