package com.halanoi.app

import android.app.ActivityManager
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halanoi.app.ui.theme.HalanoiTheme

class BlockScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val blockReason = intent.getStringExtra("BLOCK_REASON") 
            ?: "You are preparing for GATE, so please don't waste time on the internet or apps. These distractions will always be there, but your GATE attempt only happens now. If you miss this chance, no one can recover that time for you. Use this moment to conquer your future. While you browse, others are profiting from your data; instead, you should be investing in yourself. Save your future, take care of your goals, and prepare one last time for GATE. That is what I want to tell you as an AI."

        setContent {
            HalanoiTheme {
                val context = LocalContext.current
                
                val goToHomeScreen = {
                    val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                    am.killBackgroundProcesses("com.google.android.youtube")
                    am.killBackgroundProcesses("com.twitter.android")

                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(homeIntent)
                    finish() 
                }

                BackHandler(enabled = true) {
                    goToHomeScreen()
                }
                
                BlockScreenUI(
                    reason = blockReason, 
                    onBackToWork = {
                        goToHomeScreen()
                    },
                    onOpenNotes = {
                        val notesIntent = Intent(this, NotesTimelineActivity::class.java)
                        startActivity(notesIntent)
                    },
                    onCopyReason = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Block Reason", blockReason)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Reason copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
fun BlockScreenUI(reason: String, onBackToWork: () -> Unit, onOpenNotes: () -> Unit, onCopyReason: () -> Unit) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .verticalScroll(scrollState) // 🔥 Added scrolling here
            .padding(vertical = 48.dp), 
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top // Changed to Top to work better with scrolling
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        Text(text = "🛑", fontSize = 80.sp)
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Blocked by Halanoi", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        // Large text will now push the buttons down, and you can scroll to reach them
        Text(
            text = reason, 
            color = Color.Gray, 
            fontSize = 14.sp, 
            textAlign = TextAlign.Center, 
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onCopyReason) {
            Text("Copy Block Reason 📋", color = Color(0xFF3B82F6), fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onBackToWork,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
            modifier = Modifier.fillMaxWidth(0.7f).height(56.dp)
        ) {
            Text("Go Back To Work", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onOpenNotes,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            modifier = Modifier.fillMaxWidth(0.7f).height(56.dp)
        ) {
            Text("Open Focus Notes 📝", fontSize = 16.sp)
        }
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}
