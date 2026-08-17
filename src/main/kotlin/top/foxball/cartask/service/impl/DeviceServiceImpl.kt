package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.Device
import top.foxball.cartask.repository.DeviceRepository
import top.foxball.cartask.service.DeviceService

@Service
/** 基于 JPA 的接入设备服务。 */
class DeviceServiceImpl(repository: DeviceRepository) : JpaCrudService<Device>(repository), DeviceService
