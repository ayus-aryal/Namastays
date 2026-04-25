package com.example.namastays.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController

val BlueColor = Color(0xFF2563EB)
val LightBlueBackground = Color(0xFFEFF6FF)

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun ExploreScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // Globe icon inside a light blue circle
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(color = LightBlueBackground, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = "Globe Icon",
                tint = BlueColor,
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Title
        Text(
            text = "Explore places by city",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center,
            fontFamily = interFontFamily
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Subtitle
        Text(
            text = "Discover hidden gems, local favorites,\n and must-see spots",
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            fontFamily = interFontFamily
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Select a City Button
        Button(
            onClick = { /* navigate or show city picker */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BlueColor,
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Select a City",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = interFontFamily
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
@Preview(showBackground = true)
fun ExploreScreenPreview() {
    ExploreScreen(navController = rememberNavController())
}