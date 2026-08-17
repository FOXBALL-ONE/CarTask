package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.Device
import top.foxball.cartask.repository.DeviceRepository
import top.foxball.cartask.service.DeviceService

@Service
class DeviceServiceImpl(repository: DeviceRepository) : JpaCrudService<Device>(repository), DeviceService
