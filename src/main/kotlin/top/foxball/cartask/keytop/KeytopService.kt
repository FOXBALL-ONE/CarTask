package top.foxball.cartask.keytop

import java.time.LocalDateTime

/** 科拓开放平台业务接口。所有方法均同步执行一次平台 POST 请求。 */
interface KeytopService {
    /**
     * getCarCardList：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param pageIndex 参与本次处理的输入参数。
     * @param pageSize 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun getCarCardList(pageIndex: Int = 1, pageSize: Int = 100): KeytopResponse

    /**
     * addCarCardNo：创建、保存或初始化相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param userId 参与本次处理的输入参数。
     * @param userName 参与本次处理的输入参数。
     * @param cardInfo 参与本次处理的输入参数。
     * @param carLotList 参与本次处理的输入参数。
     * @param plateNoInfo 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun addCarCardNo(
        userId: Long,
        userName: String,
        cardInfo: KeytopCardInfo,
        carLotList: List<KeytopCarLot>,
        plateNoInfo: List<KeytopPlateNo>,
    ): KeytopResponse

    /** 直接传入平台要求的三个 JSON 字符串，适合已有外部请求模型的调用方。 */
    fun addCarCardNo(
        userId: Long,
        userName: String,
        cardInfo: String,
        carLotList: String,
        plateNoInfo: String,
    ): KeytopResponse

    /**
     * getCarCardInfo：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param cardId 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun getCarCardInfo(cardId: Long): KeytopResponse

    /**
     * getCarCardInfo：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param plateNo 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun getCarCardInfo(plateNo: String): KeytopResponse

    /**
     * modifyCarCardNo：更新业务状态或修改相关配置。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param userId 参与本次处理的输入参数。
     * @param userName 参与本次处理的输入参数。
     * @param cardInfo 参与本次处理的输入参数。
     * @param carLotList 参与本次处理的输入参数。
     * @param plateNoInfo 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun modifyCarCardNo(
        userId: Long,
        userName: String,
        cardInfo: KeytopCardInfo,
        carLotList: List<KeytopCarLot>,
        plateNoInfo: List<KeytopPlateNo>,
    ): KeytopResponse

    /**
     * modifyCarCardNo：更新业务状态或修改相关配置。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param userId 参与本次处理的输入参数。
     * @param userName 参与本次处理的输入参数。
     * @param cardInfo 参与本次处理的输入参数。
     * @param carLotList 参与本次处理的输入参数。
     * @param plateNoInfo 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun modifyCarCardNo(
        userId: Long,
        userName: String,
        cardInfo: String,
        carLotList: String,
        plateNoInfo: String,
    ): KeytopResponse

    /**
     * delCarCardInfo：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param cardId 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun delCarCardInfo(cardId: Long): KeytopResponse

    /**
     * payCarCardFee：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param request 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun payCarCardFee(request: KeytopPayCarCardFeeRequest): KeytopResponse

    /**
     * payCarCardFee：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param userId 参与本次处理的输入参数。
     * @param userName 参与本次处理的输入参数。
     * @param cardId 参与本次处理的输入参数。
     * @param carType 参与本次处理的输入参数。
     * @param validFrom 参与本次处理的输入参数。
     * @param validTo 参与本次处理的输入参数。
     * @param createTime 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
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
     * refundCarCardFee：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param request 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun refundCarCardFee(request: KeytopRefundCarCardFeeRequest): KeytopResponse

    /**
     * refundCarCardFee：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param userId 参与本次处理的输入参数。
     * @param userName 参与本次处理的输入参数。
     * @param cardId 参与本次处理的输入参数。
     * @param carType 参与本次处理的输入参数。
     * @param validFrom 参与本次处理的输入参数。
     * @param validTo 参与本次处理的输入参数。
     * @param createTime 参与本次处理的输入参数。
     * @param remark 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
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
     * getCardInfoByUser：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param plateNo 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun getCardInfoByUser(plateNo: String): KeytopResponse

    /**
     * getCarInoutInfo：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param pageIndex 参与本次处理的输入参数。
     * @param pageSize 参与本次处理的输入参数。
     * @param plateNo 参与本次处理的输入参数。
     * @param startTime 参与本次处理的输入参数。
     * @param endTime 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun getCarInoutInfo(
        pageIndex: Int = 1,
        pageSize: Int = 1000,
        plateNo: String? = null,
        startTime: LocalDateTime? = null,
        endTime: LocalDateTime? = null,
    ): KeytopResponse

    /**
     * getParkingPlaceArea：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun getParkingPlaceArea(): KeytopResponse

    /**
     * getDictList：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param dictType 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun getDictList(dictType: String? = null): KeytopResponse

    /**
     * queryCarBlackInfo：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param pageIndex 参与本次处理的输入参数。
     * @param pageSize 参与本次处理的输入参数。
     * @param plateNo 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun queryCarBlackInfo(pageIndex: Int = 1, pageSize: Int = 100, plateNo: String? = null): KeytopResponse

    /**
     * addCarBlackInfo：创建、保存或初始化相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param plateNo 参与本次处理的输入参数。
     * @param reason 参与本次处理的输入参数。
     * @param remark 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun addCarBlackInfo(plateNo: String, reason: String, remark: String = ""): KeytopResponse

    /**
     * modifyCarBlackInfo：更新业务状态或修改相关配置。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @param plateNo 参与本次处理的输入参数。
     * @param reason 参与本次处理的输入参数。
     * @param remark 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun modifyCarBlackInfo(id: Long, plateNo: String, reason: String, remark: String = ""): KeytopResponse

    /**
     * delCarBlackInfo：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @param plateNo 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun delCarBlackInfo(id: Long? = null, plateNo: String? = null): KeytopResponse
}
