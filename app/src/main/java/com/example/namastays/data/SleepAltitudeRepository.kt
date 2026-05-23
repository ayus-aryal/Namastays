package com.example.namastays.data

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class SleepAltitudeRepository(private val dao: SleepAltitudeDao) {

    val allRecords: Flow<List<SleepAltitudeRecord>> = dao.getAllFlow()

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getToday(): SleepAltitudeRecord? =
        dao.getByDate(LocalDate.now().toString())


    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun save(altitudeMeters: Double){
        dao.upsert(
            SleepAltitudeRecord(
                date = LocalDate.now().toString(),
                altitudeMeters = altitudeMeters
            )
        )
    }
}