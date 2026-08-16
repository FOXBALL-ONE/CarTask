package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.CarMasterInfo

interface CarMasterInfoRepository : JpaRepository<CarMasterInfo, Long>
