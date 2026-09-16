package top.foxball.setup

/**
 * SetupExceptionHandler 配置引导组件说明。
 *
 * 该文件负责系统初始化向导中的配置探测、持久化、校验或 Web 入口逻辑。
 */
/**
 * SetupExceptionHandler 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder


@RestControllerAdvice
/**
 * SetupExceptionHandler 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
/**
 * SetupExceptionHandler 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
class SetupExceptionHandler(
    private val responseBuilder: ResponseBuilder,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    @ExceptionHandler(SetupException::class)
            
            
            /**
             * handleSetup 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
            /**
             * handleSetup 的职责与行为说明。
             * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
             */
    fun handleSetup(exception: SetupException): ResponseEntity<Response> =
        responseBuilder.badRequest().message(exception.message).build()
    
    @ExceptionHandler(
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class,
    )
            
            
            /**
             * handleBadRequest 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
            /**
             * handleBadRequest 的职责与行为说明。
             * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
             */
    fun handleBadRequest(exception: Exception): ResponseEntity<Response> =
        responseBuilder.badRequest().message("请求参数不完整或格式不正确：${exception.message}").build()
    
    @ExceptionHandler(Exception::class)
            
            
            /**
             * handleUnexpected 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
            /**
             * handleUnexpected 的职责与行为说明。
             * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
             */
    fun handleUnexpected(exception: Exception): ResponseEntity<Response> {
        logger.error("配置引导接口异常", exception)
        return responseBuilder.exception()
            .message("服务器内部错误：${exception.message ?: exception.javaClass.simpleName}").build()
    }
}





