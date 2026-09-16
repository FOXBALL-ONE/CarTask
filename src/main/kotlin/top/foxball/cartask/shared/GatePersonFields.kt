package top.foxball.cartask.shared

/**
 * GatePersonFields 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */


/**
 * GatePersonFields 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
object GatePersonFields {
    
    const val CODE_MAX = 64
    const val DEPT_MAX = 128
    const val NAME_MAX = 128
    const val PHONE_MAX = 32
    const val ID_CARD_MAX = 32
    
    
    const val CODE_EXISTS_MESSAGE = "人员编号已存在"
    const val ID_CARD_EXISTS_MESSAGE = "身份证号已存在"
    
    
    private val PHONE_PATTERN = Regex("^\\d{6,20}$")
    
    
    private val ID_CARD_PATTERN = Regex("^\\d{17}[0-9Xx]$")
    
    
    /**
     * requireCode 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun requireCode(value: String?, linePrefix: String = ""): String =
        requireNotBlank(value, "${linePrefix}人员编号", CODE_MAX)
    
    
    /**
     * requireDept 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun requireDept(value: String?, linePrefix: String = ""): String =
        requireNotBlank(value, "${linePrefix}部门", DEPT_MAX)
    
    
    /**
     * requireName 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun requireName(value: String?, linePrefix: String = ""): String =
        requireNotBlank(value, "${linePrefix}姓名", NAME_MAX)
    
    
    /**
     * requirePhone 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun requirePhone(value: String?, linePrefix: String = ""): String =
        requireNotBlank(value, "${linePrefix}手机号", PHONE_MAX)
            .also { require(it.matches(PHONE_PATTERN)) { "${linePrefix}手机号格式不正确，只能填写数字" } }
    
    
    /**
     * requireIdCard 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun requireIdCard(value: String?, linePrefix: String = ""): String =
        requireNotBlank(value, "${linePrefix}身份证号", ID_CARD_MAX)
            .also { require(it.matches(ID_CARD_PATTERN)) { "${linePrefix}身份证号必须为 18 位" } }
    
    
    private fun requireNotBlank(value: String?, label: String, maxLength: Int): String {
        val trimmed = requireNotNull(value?.trim()?.takeIf(String::isNotEmpty)) { "${label}不能为空" }
        require(trimmed.length <= maxLength) { "${label}长度不能超过 $maxLength 个字符" }
        return trimmed
    }
}


