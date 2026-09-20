package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import top.foxball.cartask.entity.Announcement

interface AnnouncementRepository : JpaRepository<Announcement, Long>, JpaSpecificationExecutor<Announcement>
