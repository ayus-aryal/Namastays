package com.example.namastays.screens

import androidx.compose.ui.tooling.preview.Preview


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AltitudeZone(
    val label: String,
    val color: Color
) {
    NORMAL("Normal", Color(0xFF2E7D32)),
    ACCLIMATIZATION("Acclimatization", Color(0xFFF9A825)),
    HIGH_RISK("High Risk", Color(0xFFEF6C00)),
    EXTREME("Extreme", Color(0xFFC62828))
}

@Composable
fun TrekModeScreen() {

    var trekModeEnabled by remember { mutableStateOf(true) }

    /*
    Replace all these with real sensor/device data later:
    - GPS altitude
    - barometer fusion
    - activity recognition
    - battery state
    */

    val currentAltitude = 4132
    val gainToday = 620
    val lossToday = 180
    val movingTime = "5h 42m"
    val distance = "11.4 km"
    val verticalSpeed = "+320 m/hr"
    val lastSleepAltitude = 3560

    val altitudeZone = when {
        currentAltitude < 2500 -> AltitudeZone.NORMAL
        currentAltitude < 3500 -> AltitudeZone.ACCLIMATIZATION
        currentAltitude < 5000 -> AltitudeZone.HIGH_RISK
        else -> AltitudeZone.EXTREME
    }

    Scaffold(
        containerColor = Color(0xFF0F1115)
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            item {
                Spacer(modifier = Modifier.height(10.dp))
            }

            // TOP BAR

            item {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Column {
                        Text(
                            text = "Trek Mode",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Live expedition tracking",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }

                    Switch(
                        checked = trekModeEnabled,
                        onCheckedChange = {
                            trekModeEnabled = it
                        }
                    )
                }
            }

            // HERO ALTITUDE CARD

            item {

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF171B22)
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {

                        Text(
                            text = "Current Altitude",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "$currentAltitude m",
                            color = Color.White,
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(altitudeZone.color)
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {

                                Text(
                                    text = altitudeZone.label,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {

                            TrekMetric(
                                title = "Gain",
                                value = "+${gainToday}m"
                            )

                            TrekMetric(
                                title = "Loss",
                                value = "-${lossToday}m"
                            )

                            TrekMetric(
                                title = "Speed",
                                value = verticalSpeed
                            )
                        }
                    }
                }
            }

            // LIVE METRICS

            item {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {

                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Distance",
                        value = distance
                    )

                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Moving Time",
                        value = movingTime
                    )
                }
            }

            // SLEEP ALTITUDE CARD

            item {

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF171B22)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {

                    Column(
                        modifier = Modifier.padding(22.dp)
                    ) {

                        Text(
                            text = "Sleeping Altitude",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Last logged at ${lastSleepAltitude}m",
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = { },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E7D32)
                            )
                        ) {

                            Text(
                                text = "Log Current Sleeping Altitude",
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        ) {

                            Text(
                                text = "Adjust Manually"
                            )
                        }
                    }
                }
            }

            // ALTITUDE SCALE

            item {

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF171B22)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {

                    Column(
                        modifier = Modifier.padding(22.dp)
                    ) {

                        Text(
                            text = "Altitude Levels",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        AltitudeLevelItem(
                            color = Color(0xFF2E7D32),
                            label = "Normal",
                            range = "< 2500m"
                        )

                        AltitudeLevelItem(
                            color = Color(0xFFF9A825),
                            label = "Acclimatization",
                            range = "2500m - 3500m"
                        )

                        AltitudeLevelItem(
                            color = Color(0xFFEF6C00),
                            label = "High Risk",
                            range = "3500m - 5000m"
                        )

                        AltitudeLevelItem(
                            color = Color(0xFFC62828),
                            label = "Extreme",
                            range = "> 5000m"
                        )
                    }
                }
            }

            // INSIGHTS CARD

            item {

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1C2430)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {

                    Column(
                        modifier = Modifier.padding(22.dp)
                    ) {

                        Text(
                            text = "Acclimatization Insight",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Rapid altitude increase detected since last sleeping altitude. Consider slower ascent tomorrow.",
                            color = Color(0xFFD5D9E0),
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // TRACKING STATUS

            item {

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF171B22)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {

                    Column(
                        modifier = Modifier.padding(22.dp)
                    ) {

                        Text(
                            text = "Tracking Status",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        StatusItem(
                            icon = {
                                Icon(
                                    Icons.Outlined.GpsFixed,
                                    contentDescription = null
                                )
                            },
                            title = "GPS Active"
                        )

                        StatusItem(
                            icon = {
                                Icon(
                                    Icons.Outlined.CloudOff,
                                    contentDescription = null
                                )
                            },
                            title = "Offline Mode Enabled"
                        )

                        StatusItem(
                            icon = {
                                Icon(
                                    Icons.Outlined.BatterySaver,
                                    contentDescription = null
                                )
                            },
                            title = "Battery Optimized Tracking"
                        )

                        StatusItem(
                            icon = {
                                Icon(
                                    Icons.Outlined.Hiking,
                                    contentDescription = null
                                )
                            },
                            title = "Foreground Tracking Running"
                        )
                    }
                }
            }

            // ANALYTICS BUTTON

            item {

                Button(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 30.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF263238)
                    )
                ) {

                    Icon(
                        Icons.Default.ShowChart,
                        contentDescription = null
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "View Altitude Analytics",
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TrekMetric(
    title: String,
    value: String
) {

    Column {

        Text(
            text = title,
            color = Color.Gray,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = value,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String
) {

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF171B22)
        ),
        shape = RoundedCornerShape(22.dp)
    ) {

        Column(
            modifier = Modifier.padding(20.dp)
        ) {

            Text(
                text = title,
                color = Color.Gray,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = value,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
    }
}

@Composable
fun AltitudeLevelItem(
    color: Color,
    label: String,
    range: String
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(color)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = label,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = range,
            color = Color.Gray
        )
    }
}

@Composable
fun StatusItem(
    icon: @Composable () -> Unit,
    title: String
) {

    Row(
        modifier = Modifier.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        CompositionLocalProvider(
            LocalContentColor provides Color(0xFFB0BEC5)
        ) {
            icon()
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            color = Color(0xFFD5D9E0)
        )
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    device = "id:pixel_7_pro"
)
@Composable
fun TrekModeScreenPreview() {

    MaterialTheme {
        TrekModeScreen()
    }
}