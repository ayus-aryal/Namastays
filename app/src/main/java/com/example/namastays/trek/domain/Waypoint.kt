package com.example.namastays.trek.domain

data class Waypoint(
    val id: String,
    val name: String,
    val type: WaypointType,
    val lat: Double,
    val lng: Double,
    val elevation: Int,
    val description: String,
    val amenities: List<String>
)

enum class WaypointType {
    TRAILHEAD,
    TEAHOUSE,
    CAMPSITE,
    WATER,
    VIEWPOINT,
    CHECKPOINT,
    VILLAGE,
    EMERGENCY,
    PASS,
    SUSPENSION_BRIDGE;

    companion object {
        fun fromString(value: String): WaypointType {
            return when (value.lowercase()) {
                "trailhead"          -> TRAILHEAD
                "teahouse"           -> TEAHOUSE
                "campsite"           -> CAMPSITE
                "water"              -> WATER
                "viewpoint"          -> VIEWPOINT
                "checkpoint"         -> CHECKPOINT
                "village"            -> VILLAGE
                "emergency"          -> EMERGENCY
                "pass"               -> PASS
                "suspension_bridge"  -> SUSPENSION_BRIDGE
                else                 -> TEAHOUSE
            }
        }
    }
}