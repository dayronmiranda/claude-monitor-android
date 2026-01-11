package com.claudemonitor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.claudemonitor.data.model.Driver

@Entity(tableName = "drivers")
data class DriverEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val url: String,
    val username: String,
    val password: String,
    val createdAt: Long
) {
    fun toDriver(): Driver = Driver(
        id = id,
        name = name,
        url = url,
        username = username,
        password = password,
        createdAt = createdAt
    )

    companion object {
        fun fromDriver(driver: Driver): DriverEntity = DriverEntity(
            id = driver.id,
            name = driver.name,
            url = driver.url,
            username = driver.username,
            password = driver.password,
            createdAt = driver.createdAt
        )
    }
}
