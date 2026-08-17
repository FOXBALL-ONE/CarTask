package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.AccessRecord
import top.foxball.cartask.repository.AccessRecordRepository
import top.foxball.cartask.service.AccessRecordService

@Service
class AccessRecordServiceImpl(repository: AccessRecordRepository) : JpaCrudService<AccessRecord>(repository), AccessRecordService
