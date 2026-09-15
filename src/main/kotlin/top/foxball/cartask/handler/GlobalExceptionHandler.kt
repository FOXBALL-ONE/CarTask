package top.foxball.cartask.handler

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.TransientDataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditRequestContext
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.authentication.AuthenticationInfrastructureException
import top.foxball.cartask.authentication.LoginRateLimitException
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder


/** 全局异常处理：将各类异常转换为统一 [Response] 响应。 */
@Order(2)
@RestControllerAdvice
class GlobalExceptionHandler(
    private val auditService: AuditService,
) {
    private val log = LoggerFactory.getLogger(this.javaClass)
    private val builder = ResponseBuilder()

    @ExceptionHandler(HomeRecommendationVersionConflictException::class)
            /**
             * onHomeRecommendationVersionConflictException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onHomeRecommendationVersionConflictException(
        ex: HomeRecommendationVersionConflictException,
    ): ResponseEntity<Response> {
        data class Response(
            @param:JsonProperty("actual_version")
            val actualVersion: Long,
        )

        val rs = Response(actualVersion = ex.actualVersion)
        return builder.status(ex.status)
            .message(ex.message)
            .data(rs)
            .build()
    }

    @ExceptionHandler(AnnouncementVersionConflictException::class)
            /**
             * onAnnouncementVersionConflictException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onAnnouncementVersionConflictException(
        ex: AnnouncementVersionConflictException,
    ): ResponseEntity<Response> {
        data class Response(
            @param:JsonProperty("actual_version")
            val actualVersion: Long,
        )

        val rs = Response(actualVersion = ex.actualVersion)
        return builder.status(ex.status)
            .message(ex.message)
            .data(rs)
            .build()
    }

    @ExceptionHandler(BusinessException::class)
            /**
             * onBusinessException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onBusinessException(ex: BusinessException): ResponseEntity<Response> {
        return builder.status(ex.status)
            .message(ex.message)
            .build()
    }

    @ExceptionHandler(OrderProcessingException::class)
            /**
             * onOrderProcessingException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onOrderProcessingException(ex: OrderProcessingException): ResponseEntity<Response> {
        return builder.status(ex.status)
            .retryAfter(1)
            .message(ex.message)
            .build()
    }

    @ExceptionHandler(OrderWindowLimitException::class)
            /**
             * onOrderWindowLimitException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onOrderWindowLimitException(ex: OrderWindowLimitException): ResponseEntity<Response> {
        return builder.status(ex.status)
            .retryAfter(ex.retryAfterSeconds)
            .message(ex.message)
            .build()
    }

    @ExceptionHandler(SupportTicketRateLimitException::class)
            /**
             * onSupportTicketRateLimitException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onSupportTicketRateLimitException(ex: SupportTicketRateLimitException): ResponseEntity<Response> {
        return builder.status(ex.status)
            .retryAfter(ex.retryAfterSeconds)
            .message(ex.message)
            .build()
    }


    @ExceptionHandler(AccessDeniedException::class)
            /**
             * onAccessDeniedException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param req 参与本次处理的输入参数。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onAccessDeniedException(req: HttpServletRequest, ex: AccessDeniedException): ResponseEntity<Response> {
        runCatching {
            auditService.record(
                AuditCommand(
                    AuditAction.AUTHORIZATION_DENIED,
                    "http_request",
                    req.requestURI,
                    result = top.foxball.cartask.entity.AuditEvent.Result.DENIED,
                    reasonCode = "ACCESS_DENIED",
                    reason = ex.message,
                    targetSummary = mapOf("method" to req.method, "path" to req.requestURI),
                    idempotencyKey = AuditRequestContext.current()?.requestId?.let { "denied:$it:${req.method}:${req.requestURI}" },
                ),
            )
        }.onFailure { log.error("写入授权拒绝审计事件失败", it) }
        return builder.forbidden()
            .header("Cache-Control", "no-store")
            .message(ex.message ?: "禁止访问")
            .build()
    }

    /** 业务代码或方法级安全校验抛出的认证异常统一转换为 401。 */
    @ExceptionHandler(AuthenticationException::class)
    fun onAuthenticationException(ex: AuthenticationException): ResponseEntity<Response> {
        return builder.unauthorized()
            .header("WWW-Authenticate", "Bearer")
            .header("Cache-Control", "no-store")
            .message(ex.message ?: "未授权")
            .build()
    }

    @ExceptionHandler(LoginRateLimitException::class)
            /**
             * onLoginRateLimitException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onLoginRateLimitException(ex: LoginRateLimitException): ResponseEntity<Response> {
        return builder.status(HttpStatus.TOO_MANY_REQUESTS)
            .retryAfter(ex.retryAfterSeconds)
            .header("Cache-Control", "no-store")
            .message(ex.message ?: "登录尝试过于频繁，请稍后重试")
            .build()
    }

    @ExceptionHandler(AuthenticationInfrastructureException::class)
            /**
             * onAuthenticationInfrastructureException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onAuthenticationInfrastructureException(ex: AuthenticationInfrastructureException): ResponseEntity<Response> {
        log.error("Authentication infrastructure unavailable", ex)
        return builder.serviceUnavailable()
            .retryAfter(1)
            .header("Cache-Control", "no-store")
            .message("认证服务暂不可用")
            .build()
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
            /**
             * onHttpRequestMethodNotSupportedException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onHttpRequestMethodNotSupportedException(ex: HttpRequestMethodNotSupportedException): ResponseEntity<Response> {
        return builder.badRequest()
            .message("Method \"${ex.method}\" is not supported on this endpoint.")
            .build()
    }

    @ExceptionHandler(NoResourceFoundException::class, NoHandlerFoundException::class)
            /**
             * onNoResourceOrHandlerFoundException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onNoResourceOrHandlerFoundException(): ResponseEntity<Response> {
        return builder.notFound().build()
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
            /**
             * onMissingServletRequestParameterException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onMissingServletRequestParameterException(ex: MissingServletRequestParameterException): ResponseEntity<Response> {
        return builder.badRequest()
            .message("Required parameter \"${ex.parameterName}\" is not provided!")
            .build()
    }

    @ExceptionHandler(MissingServletRequestPartException::class)
            /**
             * onMissingServletRequestPartException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onMissingServletRequestPartException(ex: MissingServletRequestPartException): ResponseEntity<Response> {
        return builder.badRequest()
            .message("Required request part \"${ex.requestPartName}\" is not provided!")
            .build()
    }

    @ExceptionHandler(MissingRequestHeaderException::class)
            /**
             * onMissingRequestHeaderException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onMissingRequestHeaderException(ex: MissingRequestHeaderException): ResponseEntity<Response> {
        return builder.badRequest()
            .message("Required request header \"${ex.headerName}\" is not provided!")
            .build()
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
            /**
             * onMethodArgumentTypeMismatchException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onMethodArgumentTypeMismatchException(ex: MethodArgumentTypeMismatchException): ResponseEntity<Response> {
        return builder.badRequest()
            .message("Parameter \"${ex.parameter.parameterName}\" type mismatch. Expected ${ex.requiredType}.")
            .build()
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
            /**
             * onMethodArgumentNotValid：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onMethodArgumentNotValid(ex: MethodArgumentNotValidException): ResponseEntity<Response> {
        val detail = ex.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return builder.badRequest()
            .message("参数校验失败：$detail")
            .build()
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
            /**
             * onHandlerMethodValidationException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onHandlerMethodValidationException(ex: HandlerMethodValidationException): ResponseEntity<Response> {
        val detail = ex.parameterValidationResults.joinToString("; ") { result ->
            val parameterName = result.methodParameter.parameterName ?: "parameter"
            val messages = result.resolvableErrors.joinToString(", ") { error ->
                error.defaultMessage ?: "invalid value"
            }
            "$parameterName: $messages"
        }
        return builder.badRequest()
            .message(if (detail.isBlank()) "参数校验失败" else "参数校验失败: $detail")
            .build()
    }


    @ExceptionHandler(HttpMessageNotReadableException::class)
            /**
             * onHttpMessageNotReadable：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onHttpMessageNotReadable(ex: HttpMessageNotReadableException): ResponseEntity<Response> {
        return builder.badRequest()
            .message("请求体格式错误或必填字段缺失")
            .build()
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
            /**
             * onMaxUploadSizeExceededException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onMaxUploadSizeExceededException(): ResponseEntity<Response> {
        return builder.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .message("Uploaded file exceeds the configured size limit.")
            .build()
    }

    @ExceptionHandler(IllegalArgumentException::class)
            /**
             * onIllegalArgumentException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onIllegalArgumentException(ex: IllegalArgumentException?): ResponseEntity<Response> {
        log.warn("Illegal argument access happened: ", ex)
        return builder.badRequest()
            .message(ex?.message ?: "Invalid argument.")
            .build()
    }

    @ExceptionHandler(TransientDataAccessException::class)
            /**
             * onTransientDataAccessException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onTransientDataAccessException(ex: TransientDataAccessException): ResponseEntity<Response> {
        log.warn("Transient data access error: {}", ex.message)
        return builder.serviceUnavailable()
            .retryAfter(1)
            .message("系统繁忙，请稍后重试")
            .build()
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
            /**
             * onOptimisticLockingFailureException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onOptimisticLockingFailureException(ex: ObjectOptimisticLockingFailureException): ResponseEntity<Response> {
        log.warn("Optimistic locking conflict: {}", ex.message)
        return builder.status(HttpStatus.CONFLICT)
            .message("数据已被其他操作更新，请刷新后重试")
            .build()
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
            /**
             * onDataIntegrityViolationException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onDataIntegrityViolationException(ex: DataIntegrityViolationException): ResponseEntity<Response> {
        val detail = generateSequence<Throwable>(ex) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()
        val message = when {
            "uk_shipment_item_active" in detail -> "商品已分配给其他有效运单"
            "uk_shipment_carrier_tracking" in detail -> "承运商追踪号已绑定其他运单"
            "uk_logistics_idempotency" in detail -> "幂等键冲突，请重试查询原结果"
            "uk_order_idempotency" in detail -> "下单幂等键冲突，请重试查询原订单"
            "fk_support_ticket_message_attachment_file" in detail -> "工单消息使用中的附件不能删除"
            // 门禁人员的唯一性是先查后存，并发下会落到数据库约束上；不映射就是一句 500。
            "uk_gate_person_code" in detail -> "人员编号已存在"
            "uk_gate_person_id_card" in detail -> "身份证号已存在"
            // 车主卡号与车牌号同理：登记进出申请时顺带建档走的是同一条先查后存路径。
            "uk_parking_owner_card_id" in detail -> "车主卡号已存在，请重试或改用已建档车牌登记"
            "uk_parking_plate_number" in detail -> "车牌号已存在，请改用已建档车牌登记"
            // 账号唯一性是先查后存，并发下同样会落到数据库约束上。
            "uk_users_username" in detail -> "用户名已存在"
            "uk_users_phone" in detail -> "该手机号已被其他账号绑定"
            else -> null
        }
        if (message != null) {
            return builder.status(HttpStatus.CONFLICT).message(message).build()
        }
        log.error("Unhandled data integrity violation", ex)
        return builder.exception().build()
    }

    @ExceptionHandler(DataAccessException::class)
            /**
             * onDataAccessException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onDataAccessException(ex: DataAccessException): ResponseEntity<Response> {
        log.error("Non-transient data access error", ex)
        return builder.exception().build()
    }

    @ExceptionHandler(Exception::class)
            /**
             * onException：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param req 参与本次处理的输入参数。
             * @param ex 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onException(req: HttpServletRequest, ex: Exception?): ResponseEntity<Response> {
        log.error("Got an exception while process request: {}", req.requestURI, ex)
        return builder.exception().build()
    }
}
