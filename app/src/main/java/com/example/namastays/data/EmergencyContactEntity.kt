package com.example.namastays.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emergency_contacts")
data class EmergencyContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name")     val name: String,
    @ColumnInfo(name = "phone")    val phone: String,
    @ColumnInfo(name = "relation") val relation: String
)