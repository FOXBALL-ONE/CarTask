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
    fun handleSetup(exception: SetupException): ResponseEntity<Response> =
        responseBuilder.badRequest().message(exception.message).build()

    @ExceptionHandler(
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class,
    )
    fun handleBadRequest(exception: Exception): ResponseEntity<Response> =
        responseBuilder.badRequest().message("请求参数不完整或格式不正确：${exception.message}").build()

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception): ResponseEntity<Response> {
        logger.error("配置引导接口异常", exception)
        return responseBuilder.exception().message("服务器内部错误：${exception.message ?: exception.javaClass.simpleName}").build()
    }
}
