package top.foxball.cartask.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.web.config.EnableSpringDataWebSupport

/** 配置 Spring Data Web 支持，使分页和排序参数能够按项目约定绑定到控制器。 */
@Configuration(proxyBeanMethods = false)
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
class SpringDataWebConfig
