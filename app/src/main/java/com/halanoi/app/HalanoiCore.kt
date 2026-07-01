package com.halanoi.app

import android.util.Log

object HalanoiCore {
    init {
        try {
            System.loadLibrary("halanoi_engine")
            Log.d("HalanoiCore", "✅ C++ Sovereign Engine Loaded Successfully!")
        } catch (e: Throwable) {
            Log.e("HalanoiCore", "❌ FAILED TO LOAD C++ ENGINE: ${e.message}", e)
        }
    }

    external fun initializeSovereignEngine(): String
    
    // NEW: The bridge now accepts the extracted text string!
    external fun analyzeText(screenText: String): Boolean
}
