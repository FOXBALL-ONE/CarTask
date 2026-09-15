package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import top.foxball.cartask.entity.AccessRecord
import java.time.LocalDateTime

interface AccessRecordRepository : JpaRepository<AccessRecord, Long>, JpaSpecificationExecutor<AccessRecord> {
    /**
     * findTopByOrderByInAndOutTimeDescIdDesc：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun findTopByOrderByInAndOutTimeDescIdDesc(): AccessRecord?

    /** 查询指定时间之后有进出记录的去重车牌号。 */
    @Query(
        """
        select distinct record.carNumber from AccessRecord record
        where record.inAndOutTime >= :startTime
          and record.carNumber is not null
          and record.carNumber <> ''
        """,
    )
            /**
             * findDistinctCarNumbersSince：查询或读取相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param startTime 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun findDistinctCarNumbersSince(@Param("startTime") startTime: LocalDateTime): List<String>

    /**
     * findBySourceRecordId：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param sourceRecordId 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun findBySourceRecordId(sourceRecordId: String): AccessRecord?

    /**
     * findTop100ByPhotoSyncStatusOrderByIdAsc：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param photoSyncStatus 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun findTop100ByPhotoSyncStatusOrderByIdAsc(
        photoSyncStatus: AccessRecord.PhotoSyncStatus,
    ): List<AccessRecord>

    /**
     * countByPhotoSyncStatus：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param photoSyncStatus 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun countByPhotoSyncStatus(photoSyncStatus: AccessRecord.PhotoSyncStatus): Long

    /** 未回填归一化车牌的记录数；数据范围回填接口的统计口径。 */
    @Query("select count(record) from AccessRecord record where record.carNumberNormalized is null")
    fun countByCarNumberNormalizedIsNull(): Long

    @Query(
        """
        select record from AccessRecord record
        where record.carNumber = :carNumber
          and record.inAndOut = :inAndOut
        order by record.inAndOutTime desc, record.id desc
        """,
    )
            /**
             * findFirstByCarNumberAndInAndOutOrderByInAndOutTimeDesc：查询或读取相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param carNumber 参与本次处理的输入参数。
             * @param inAndOut 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun findFirstByCarNumberAndInAndOutOrderByInAndOutTimeDesc(
        @Param("carNumber") carNumber: String,
        @Param("inAndOut") inAndOut: AccessRecord.InAndOut,
    ): AccessRecord?


    @Query(
        """
        select record from AccessRecord record
        where ((:carNumber is null and record.carNumber is null) or record.carNumber = :carNumber)
          and record.inAndOut = :inAndOut
          and record.inAndOutTime = :inAndOutTime
        """,
    )
            /**
             * findByIdentity：查询或读取相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param carNumber 参与本次处理的输入参数。
             * @param inAndOut 参与本次处理的输入参数。
             * @param inAndOutTime 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun findByIdentity(
        @Param("carNumber") carNumber: String?,
        @Param("inAndOut") inAndOut: AccessRecord.InAndOut,
        @Param("inAndOutTime") inAndOutTime: LocalDateTime,
    ): AccessRecord?

}
