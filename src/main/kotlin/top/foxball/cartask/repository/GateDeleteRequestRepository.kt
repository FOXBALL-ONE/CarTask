package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.GateDeleteRequest

/** 门禁删除申请的数据访问接口，提供申请记录的标准增删改查能力。 */
interface GateDeleteRequestRepository : JpaRepository<GateDeleteRequest, Long>
