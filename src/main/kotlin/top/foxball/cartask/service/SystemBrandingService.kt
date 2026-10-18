package top.foxball.cartask.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.foxball.cartask.entity.SystemBranding
import top.foxball.cartask.repository.SystemBrandingRepository
import java.util.*

/** 系统品牌（图标 + 标题）的读写。logo 为 null 表示使用前端自带的默认图标。 */
data class BrandingData(
    val title: String,
    val logo: String?,
)


@Service
class SystemBrandingService(
    private val repository: SystemBrandingRepository,
) {
    
    
    /** read：没有配置过时返回默认标题，不往库里写。 */
    @Transactional(readOnly = true)
    fun read(): BrandingData = repository.findFirstByOrderByIdAsc()
        ?.let { BrandingData(title = it.title, logo = it.logo) }
        ?: BrandingData(title = DEFAULT_TITLE, logo = null)
    
    
    /**
     * update：logo 传 null 或空串表示恢复默认图标。
     *
     * 只接受 base64 编码的图片 data URL，并限制解码后的大小——这一行会被每个访客
     * 在登录页匿名拉取，放一张大图等于给公开接口加了一份很重的响应。
     */
    @Transactional
    fun update(title: String?, logo: String?): BrandingData {
        val normalizedTitle = title?.trim().orEmpty()
        require(normalizedTitle.isNotEmpty()) { "系统标题不能为空" }
        require(normalizedTitle.length <= TITLE_MAX_LENGTH) { "系统标题不能超过 $TITLE_MAX_LENGTH 个字符" }
        require(normalizedTitle.none { it == '\n' || it == '\r' }) { "系统标题不能包含换行" }
        val normalizedLogo = logo?.trim()?.takeIf { it.isNotEmpty() }?.also(::validateLogo)
        
        val entity = repository.findFirstByOrderByIdAsc() ?: SystemBranding()
        entity.title = normalizedTitle
        entity.logo = normalizedLogo
        val saved = repository.save(entity)
        return BrandingData(title = saved.title, logo = saved.logo)
    }
    
    
    private fun validateLogo(logo: String) {
        val match = DATA_URL.matchEntire(logo)
        require(match != null) { "图标必须是 PNG、JPEG、GIF、WEBP、SVG 或 ICO 图片" }
        val bytes = try {
            Base64.getDecoder().decode(match.groupValues[2])
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("图标数据无法解析")
        }
        require(bytes.isNotEmpty()) { "图标不能为空文件" }
        require(bytes.size <= LOGO_MAX_BYTES) { "图标不能超过 ${LOGO_MAX_BYTES / 1024}KB" }
    }
    
    
    companion object {
        const val DEFAULT_TITLE = "福清市机关事务服务中心车务管理系统"
        const val TITLE_MAX_LENGTH = 64
        const val LOGO_MAX_BYTES = 256 * 1024
        
        private val DATA_URL = Regex(
            """^data:(image/(?:png|jpeg|gif|webp|svg\+xml|x-icon|vnd\.microsoft\.icon));base64,([A-Za-z0-9+/=]+)$""",
        )
    }
}
