package top.foxball.cartask.config

/**
 * SpringDataWebConfig 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.context.annotation.Configuration
import org.springframework.data.web.config.EnableSpringDataWebSupport


@Configuration(proxyBeanMethods = false)
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
/**
 * SpringDataWebConfig 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class SpringDataWebConfig


