package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.Department

interface DepartmentRepository : JpaRepository<Department, Long>
