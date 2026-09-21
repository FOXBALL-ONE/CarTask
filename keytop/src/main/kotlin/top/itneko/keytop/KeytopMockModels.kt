package top.itneko.keytop

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "keytop_car_card")
data class CarCard(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "card_id", nullable = false, unique = true)
    val cardId: Long = 0,
    @Column(name = "card_name")
    val cardName: String? = null,
    @Column(name = "update_user")
    val updateUser: String = "mock",
    @Column(name = "user_name")
    val userName: String? = null,
    @Column(name = "use_name")
    val useName: String? = null,
    @Column(name = "tel")
    val tel: String? = null,
    @Column(name = "email")
    val email: String = "",
    @Column(name = "room_id")
    val roomId: String? = null,
    @Column(name = "remak")
    val remak: String? = null,
    @Column(name = "contact")
    val contact: String? = null,
    @Column(name = "assist")
    val assist: String? = null,
    @Column(name = "valid_count")
    val validCount: String = "1",
    @Column(name = "full_car_no_str")
    val fullCarNoStr: String = "",
    @Column(name = "merchant_id")
    val merchantId: String = "",
    @Column(name = "image_url")
    val imageUrl: String = "",
    @Column(name = "lot_count")
    val lotCount: Int = 1,
    @Column(name = "card_state")
    val cardState: Int = 1,
    @Column(name = "state")
    val state: Int = 1,
    @Column(name = "last_update_time")
    val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
    @Column(name = "effective_time")
    val effectiveTime: String? = null,
    @Column(name = "valid_from")
    val validFrom: String? = null,
    @Column(name = "valid_to")
    val validTo: String? = null,
    @OneToMany(mappedBy = "card", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    val carLotList: List<CarLot> = emptyList(),
    @OneToMany(mappedBy = "card", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    val plateNoInfo: List<PlateInfo> = emptyList(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CarCard
        if (cardId != other.cardId) return false
        return true
    }

    override fun hashCode(): Int = cardId.hashCode()
}

@Entity
@Table(name = "keytop_car_lot")
data class CarLot(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "card_id", nullable = false)
    val cardId: Long = 0,
    @Column(name = "lot_name")
    val lotName: String = "",
    @Column(name = "car_type")
    val carType: Int = 1,
    @Column(name = "sequence")
    val sequence: Int = 1,
    @Column(name = "area_name")
    val areaName: String = "",
    @Column(name = "area_id", columnDefinition = "TEXT")
    val areaIdStr: String = "[]",
    @Column(name = "lot_count")
    val lotCount: Int = 1,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", insertable = false, updatable = false)
    val card: CarCard? = null,
) {
    fun areaId(): List<Int> = try {
        jacksonObjectMapper().readValue(areaIdStr, object : TypeReference<List<Int>>() {})
    } catch (e: Exception) {
        emptyList()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CarLot
        if (id != other.id) return false
        return true
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}

@Entity
@Table(name = "keytop_plate_info")
data class PlateInfo(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "card_id", nullable = false)
    val cardId: Long = 0,
    @Column(name = "plate_no", nullable = false)
    val plateNo: String = "",
    @Column(name = "etc_no")
    val etcNo: String = "",
    @Column(name = "remark")
    val remark: String? = null,
    @Column(name = "plate_state")
    val plateState: Int = 1,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", insertable = false, updatable = false)
    val card: CarCard? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PlateInfo
        if (id != other.id) return false
        return true
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}

@Entity
@Table(name = "keytop_blacklist_item")
data class BlacklistItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "plate_no", nullable = false, unique = true)
    val plateNo: String = "",
    @Column(name = "reason")
    val reason: String = "",
    @Column(name = "remark")
    val remark: String = "",
    @Column(name = "create_time")
    val createTime: LocalDateTime = LocalDateTime.now(),
)

data class CarInoutRecord(
    val plateNo: String,
    val capFlag: String,
    val capTime: String,
    val imgName: String,
    val imgType: Int,
    val imgInfo: String,
    val capPlace: String,
    val carType: Int = 1,
    val carColor: String = "黑",
    val carStyle: String = "0",
    val carBrand: String = "模拟车辆",
    val cardNo: String = "",
    val passType: Int = 0,
    val passRemark: String = "",
    val trafficId: String,
    val nodeId: String,
    val carSerial: String,
    val carOwnerName: String = "",
    val operator: String = "0",
    val operName: String = "",
    val serialType: String = "0",
)
