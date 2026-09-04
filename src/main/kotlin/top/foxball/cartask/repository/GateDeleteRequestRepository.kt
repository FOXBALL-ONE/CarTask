package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.GateDeleteRequest

interface GateDeleteRequestRepository : JpaRepository<GateDeleteRequest, Long>
