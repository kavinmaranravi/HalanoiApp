package com.halanoi.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halanoi.app.ui.theme.HalanoiTheme

class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HalanoiTheme {
                // We use a dark background to make it look serious
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212) // Dark Grey/Black
                ) {
                    BlockScreen {
                        kickToHomeScreen()
                    }
                }
            }
        }
    }

    // This function throws the user all the way out of Chrome and back to the phone's main screen
    private fun kickToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish() // Closes this block screen so it doesn't linger in the background
    }
}

@Composable
fun BlockScreen(onLeaveClicked: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🛑 BLOCKED",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF5252) // A strong red color
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Halanoi detected a distraction.\nStay focused on your goals.",
            fontSize = 20.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onLeaveClicked,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
        ) {
            Text(
                text = "Get Back to Work", 
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
