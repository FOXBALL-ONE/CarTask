package top.foxball.setup

/**
 * 引导流程里可以直接展示给操作者的错误。
 *
 * 这类错误几乎都是「填错了」或「连不上」，消息本身就是操作者接下来要做的动作，必须原样送到页面上。
 * 若混在通用异常里被兜底成「服务器内部错误」，实施人员就只能去看日志，而他要的信息全在消息里。
 */
class SetupException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
