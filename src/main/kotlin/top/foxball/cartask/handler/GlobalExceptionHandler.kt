package top.foxball.cartask.handler

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
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
import top.foxball.cartask.shared.GatePersonFields
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder


@Order(2)
@RestControllerAdvice
class GlobalExceptionHandler(
    private val auditService: AuditService,
    
    @param:Value("\${spring.servlet.multipart.max-file-size:5MB}") private val maxUploadSize: String,
) {
    private val log = LoggerFactory.getLogger(this.javaClass)
    private val builder = ResponseBuilder()
    
    private companion object {
        
        const val SPRING_DENIED_MESSAGE = "Access Denied"
    }
    
    @ExceptionHandler(HomeRecommendationVersionConflictException::class)
    
    
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
    
    
    fun onBusinessException(ex: BusinessException): ResponseEntity<Response> {
        return builder.status(ex.status)
            .message(ex.message)
            .build()
    }
    
    @ExceptionHandler(OrderProcessingException::class)
    
    
    fun onOrderProcessingException(ex: OrderProcessingException): ResponseEntity<Response> {
        return builder.status(ex.status)
            .retryAfter(1)
            .message(ex.message)
            .build()
    }
    
    @ExceptionHandler(OrderWindowLimitException::class)
    
    
    fun onOrderWindowLimitException(ex: OrderWindowLimitException): ResponseEntity<Response> {
        return builder.status(ex.status)
            .retryAfter(ex.retryAfterSeconds)
            .message(ex.message)
            .build()
    }
    
    @ExceptionHandler(SupportTicketRateLimitException::class)
    
    
    fun onSupportTicketRateLimitException(ex: SupportTicketRateLimitException): ResponseEntity<Response> {
        return builder.status(ex.status)
            .retryAfter(ex.retryAfterSeconds)
            .message(ex.message)
            .build()
    }
    
    
    @ExceptionHandler(AccessDeniedException::class)
    
    
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
        val message = ex.message?.takeIf { it.isNotBlank() && it != SPRING_DENIED_MESSAGE } ?: "没有操作权限"
        return builder.forbidden()
            .header("Cache-Control", "no-store")
            .message(message)
            .build()
    }
    
    
    @ExceptionHandler(AuthenticationException::class)
    fun onAuthenticationException(ex: AuthenticationException): ResponseEntity<Response> {
        return builder.unauthorized()
            .header("WWW-Authenticate", "Bearer")
            .header("Cache-Control", "no-store")
            .message(ex.message ?: "未授权")
            .build()
    }
    
    @ExceptionHandler(LoginRateLimitException::class)
    
    
    fun onLoginRateLimitException(ex: LoginRateLimitException): ResponseEntity<Response> {
        return builder.status(HttpStatus.TOO_MANY_REQUESTS)
            .retryAfter(ex.retryAfterSeconds)
            .header("Cache-Control", "no-store")
            .message(ex.message ?: "登录尝试过于频繁，请稍后重试")
            .build()
    }
    
    @ExceptionHandler(AuthenticationInfrastructureException::class)
    
    
    fun onAuthenticationInfrastructureException(ex: AuthenticationInfrastructureException): ResponseEntity<Response> {
        log.error("Authentication infrastructure unavailable", ex)
        return builder.serviceUnavailable()
            .retryAfter(1)
            .header("Cache-Control", "no-store")
            .message("认证服务暂不可用")
            .build()
    }
    
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    
    
    fun onHttpRequestMethodNotSupportedException(ex: HttpRequestMethodNotSupportedException): ResponseEntity<Response> {
        return builder.badRequest()
            .message("该接口不支持 ${ex.method} 方法")
            .build()
    }
    
    @ExceptionHandler(NoResourceFoundException::class, NoHandlerFoundException::class)
    
    
    fun onNoResourceOrHandlerFoundException(): ResponseEntity<Response> {
        return builder.notFound().build()
    }
    
    @ExceptionHandler(MissingServletRequestParameterException::class)
    
    
    fun onMissingServletRequestParameterException(ex: MissingServletRequestParameterException): ResponseEntity<Response> {
        return builder.badRequest()
            .message("缺少必需的参数：${ex.parameterName}")
            .build()
    }
    
    @ExceptionHandler(MissingServletRequestPartException::class)
    
    
    fun onMissingServletRequestPartException(ex: MissingServletRequestPartException): ResponseEntity<Response> {
        return builder.badRequest()
            .message("缺少必需的请求部分：${ex.requestPartName}")
            .build()
    }
    
    @ExceptionHandler(MissingRequestHeaderException::class)
    
    
    fun onMissingRequestHeaderException(ex: MissingRequestHeaderException): ResponseEntity<Response> {
        return builder.badRequest()
            .message("缺少必需的请求头：${ex.headerName}")
            .build()
    }
    
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    
    
    fun onMethodArgumentTypeMismatchException(ex: MethodArgumentTypeMismatchException): ResponseEntity<Response> {
        return builder.badRequest()
            .message("参数「${ex.parameter.parameterName}」格式不正确")
            .build()
    }
    
    @ExceptionHandler(MethodArgumentNotValidException::class)
    
    
    fun onMethodArgumentNotValid(ex: MethodArgumentNotValidException): ResponseEntity<Response> {
        val detail = ex.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return builder.badRequest()
            .message("参数校验失败：$detail")
            .build()
    }
    
    @ExceptionHandler(HandlerMethodValidationException::class)
    
    
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
    
    
    fun onHttpMessageNotReadable(ex: HttpMessageNotReadableException): ResponseEntity<Response> {
        return builder.badRequest()
            .message("请求体格式错误或必填字段缺失")
            .build()
    }
    
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    
    
    fun onMaxUploadSizeExceededException(): ResponseEntity<Response> {
        return builder.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .message("上传文件超过大小限制（单个文件最大 $maxUploadSize）")
            .build()
    }
    
    @ExceptionHandler(IllegalArgumentException::class)
    
    
    fun onIllegalArgumentException(ex: IllegalArgumentException?): ResponseEntity<Response> {
        log.warn("Illegal argument access happened: ", ex)
        return builder.badRequest()
            .message(ex?.message ?: "Invalid argument.")
            .build()
    }
    
    @ExceptionHandler(TransientDataAccessException::class)
    
    
    fun onTransientDataAccessException(ex: TransientDataAccessException): ResponseEntity<Response> {
        log.warn("Transient data access error: {}", ex.message)
        return builder.serviceUnavailable()
            .retryAfter(1)
            .message("系统繁忙，请稍后重试")
            .build()
    }
    
    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    
    
    fun onOptimisticLockingFailureException(ex: ObjectOptimisticLockingFailureException): ResponseEntity<Response> {
        log.warn("Optimistic locking conflict: {}", ex.message)
        return builder.status(HttpStatus.CONFLICT)
            .message("数据已被其他操作更新，请刷新后重试")
            .build()
    }
    
    @ExceptionHandler(DataIntegrityViolationException::class)
    
    
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
            "uk_gate_person_code" in detail -> GatePersonFields.CODE_EXISTS_MESSAGE
            "uk_gate_person_id_card" in detail -> GatePersonFields.ID_CARD_EXISTS_MESSAGE
            "uk_parking_owner_card_id" in detail -> "车主卡号已存在，请重试或改用已建档车牌登记"
            "uk_parking_plate_number" in detail -> "车牌号已存在，请改用已建档车牌登记"
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
    
    
    fun onDataAccessException(ex: DataAccessException): ResponseEntity<Response> {
        log.error("Non-transient data access error", ex)
        return builder.exception().build()
    }
    
    @ExceptionHandler(Exception::class)
    
    
    fun onException(req: HttpServletRequest, ex: Exception?): ResponseEntity<Response> {
        log.error("Got an exception while process request: {}", req.requestURI, ex)
        return builder.exception().build()
    }
}
