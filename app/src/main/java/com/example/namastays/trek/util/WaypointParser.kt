package com.example.namastays.trek.util

import android.content.Context
import android.util.Log
import com.example.namastays.trek.domain.Waypoint
import com.example.namastays.trek.domain.WaypointType
import org.json.JSONObject
import java.io.File

object WaypointParser {

    fun parse(context: Context, trekId: String): List<Waypoint> {
        val file = File(context.filesDir, "$trekId.json")

        if (!file.exists()) {
            Log.e("WaypointParser", "Waypoints file not found: ${file.absolutePath}")
            return emptyList()
        }

        return try {
            val json = JSONObject(file.readText())
            val waypointsArray = json.getJSONArray("waypoints")
            val waypoints = mutableListOf<Waypoint>()

            for (i in 0 until waypointsArray.length()) {
                val obj = waypointsArray.getJSONObject(i)

                val amenities = mutableListOf<String>()
                val amenitiesArray = obj.optJSONArray("amenities")
                if (amenitiesArray != null) {
                    for (j in 0 until amenitiesArray.length()) {
                        amenities.add(amenitiesArray.getString(j))
                    }
                }

                waypoints.add(
                    Waypoint(
                        id          = obj.getString("id"),
                        name        = obj.getString("name"),
                        type        = WaypointType.fromString(obj.getString("type")),
                        lat         = obj.getDouble("lat"),
                        lng         = obj.getDouble("lng"),
                        elevation   = obj.getInt("elevation"),
                        description = obj.optString("description", ""),
                        amenities   = amenities
                    )
                )
            }

            Log.d("WaypointParser", "Parsed ${waypoints.size} waypoints")
            waypoints

        } catch (e: Exception) {
            Log.e("WaypointParser", "Parse error: ${e.message}")
            emptyList()
        }
    }
}