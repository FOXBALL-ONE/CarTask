package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.PersonAccessRecord

/** 人员通行记录数据访问接口，负责通行记录的持久化操作。 */
interface PersonAccessRecordRepository : JpaRepository<PersonAccessRecord, Long>
