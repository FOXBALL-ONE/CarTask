package top.foxball.cartask.keytop

/**
 * KeytopService 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import java.time.LocalDateTime


/**
 * KeytopService 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
interface KeytopService {
    
    
    /**
     * getCarCardList 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun getCarCardList(pageIndex: Int = 1, pageSize: Int = 100): KeytopResponse
    
    
    /**
     * addCarCardNo 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun addCarCardNo(
        userId: Long,
        userName: String,
        cardInfo: KeytopCardInfo,
        carLotList: List<KeytopCarLot>,
        plateNoInfo: List<KeytopPlateNo>,
    ): KeytopResponse
    
    
    /**
     * addCarCardNo 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun addCarCardNo(
        userId: Long,
        userName: String,
        cardInfo: String,
        carLotList: String,
        plateNoInfo: String,
    ): KeytopResponse
    
    
    /**
     * getCarCardInfo 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun getCarCardInfo(cardId: Long): KeytopResponse
    
    
    /**
     * getCarCardInfo 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun getCarCardInfo(plateNo: String): KeytopResponse
    
    
    /**
     * modifyCarCardNo 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun modifyCarCardNo(
        userId: Long,
        userName: String,
        cardInfo: KeytopCardInfo,
        carLotList: List<KeytopCarLot>,
        plateNoInfo: List<KeytopPlateNo>,
    ): KeytopResponse
    
    
    /**
     * modifyCarCardNo 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun modifyCarCardNo(
        userId: Long,
        userName: String,
        cardInfo: String,
        carLotList: String,
        plateNoInfo: String,
    ): KeytopResponse
    
    
    /**
     * delCarCardInfo 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun delCarCardInfo(cardId: Long): KeytopResponse
    
    
    /**
     * payCarCardFee 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun payCarCardFee(request: KeytopPayCarCardFeeRequest): KeytopResponse
    
    
    /**
     * payCarCardFee 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun payCarCardFee(
        userId: Long,
        userName: String,
        cardId: Long,
        carType: Int,
        validFrom: LocalDateTime,
        validTo: LocalDateTime,
        createTime: LocalDateTime = LocalDateTime.now(),
    ): KeytopResponse
    
    
    /**
     * refundCarCardFee 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun refundCarCardFee(request: KeytopRefundCarCardFeeRequest): KeytopResponse
    
    
    /**
     * refundCarCardFee 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun refundCarCardFee(
        userId: Long,
        userName: String,
        cardId: Long,
        carType: Int,
        validFrom: LocalDateTime,
        validTo: LocalDateTime,
        createTime: LocalDateTime = LocalDateTime.now(),
        remark: String = "有效期缩短",
    ): KeytopResponse
    
    
    /**
     * getCardInfoByUser 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun getCardInfoByUser(plateNo: String): KeytopResponse
    
    
    /**
     * getCarInoutInfo 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun getCarInoutInfo(
        pageIndex: Int = 1,
        pageSize: Int = 1000,
        plateNo: String? = null,
        startTime: LocalDateTime? = null,
        endTime: LocalDateTime? = null,
    ): KeytopResponse
    
    
    /**
     * getParkingPlaceArea 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun getParkingPlaceArea(): KeytopResponse
    
    
    /**
     * getDictList 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun getDictList(dictType: String? = null): KeytopResponse
    
    
    /**
     * queryCarBlackInfo 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun queryCarBlackInfo(pageIndex: Int = 1, pageSize: Int = 100, plateNo: String? = null): KeytopResponse
    
    
    /**
     * addCarBlackInfo 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun addCarBlackInfo(plateNo: String, reason: String, remark: String = ""): KeytopResponse
    
    
    /**
     * modifyCarBlackInfo 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun modifyCarBlackInfo(id: Long, plateNo: String, reason: String, remark: String = ""): KeytopResponse
    
    
    /**
     * delCarBlackInfo 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun delCarBlackInfo(id: Long? = null, plateNo: String? = null): KeytopResponse
}


