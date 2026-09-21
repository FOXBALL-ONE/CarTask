package top.itneko.keytop

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface CarCardRepository : JpaRepository<CarCard, Long> {
    fun findByCardId(cardId: Long): CarCard?
    @Query("SELECT c FROM CarCard c JOIN c.plateNoInfo p WHERE p.plateNo = :plateNo")
    fun findByPlateNo(plateNo: String): CarCard?
}

@Repository
interface CarLotRepository : JpaRepository<CarLot, Long> {
    fun findByCardId(cardId: Long): List<CarLot>
}

@Repository
interface PlateInfoRepository : JpaRepository<PlateInfo, Long> {
    fun findByCardId(cardId: Long): List<PlateInfo>
    fun findByPlateNo(plateNo: String): PlateInfo?
}

@Repository
interface BlacklistItemRepository : JpaRepository<BlacklistItem, Long> {
    fun findByPlateNo(plateNo: String): BlacklistItem?
    fun findAllByPlateNo(plateNo: String?): List<BlacklistItem>
    fun findAllByPlateNoContains(plateNo: String): List<BlacklistItem>
}
