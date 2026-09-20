package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import top.foxball.cartask.entity.CommuteRoute

interface CommuteRouteRepository : JpaRepository<CommuteRoute, Long>, JpaSpecificationExecutor<CommuteRoute>
