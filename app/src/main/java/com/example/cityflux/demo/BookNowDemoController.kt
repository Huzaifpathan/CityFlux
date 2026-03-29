package com.example.cityflux.demo

import android.content.Context
import android.util.Log
import com.example.cityflux.data.RealtimeParkingSetup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Demo Controller for BookNow Dialog
 * Manages real-time parking simulation and data setup
 */
class BookNowDemoController {
    
    private val realtimeSetup = RealtimeParkingSetup()
    private var simulationJob: kotlinx.coroutines.Job? = null
    
    companion object {
        private const val TAG = "BookNowDemo"
        @Volatile
        private var INSTANCE: BookNowDemoController? = null
        
        fun getInstance(): BookNowDemoController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BookNowDemoController().also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Initialize demo data and start real-time simulation
     */
    fun startDemo(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Starting BookNow dialog demo...")
                
                // Initialize real-time parking data
                realtimeSetup.initializeParkingLiveData()
                
                // Start slot simulation
                startSlotSimulation()
                
                Log.d(TAG, "BookNow demo started successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting demo", e)
            }
        }
    }
    
    /**
     * Start real-time slot availability simulation
     */
    private fun startSlotSimulation() {
        simulationJob?.cancel()
        
        simulationJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    // Simulate slot changes every 10-30 seconds
                    delay((10000..30000).random().toLong())
                    realtimeSetup.simulateSlotChanges()
                    Log.d(TAG, "Simulated slot availability changes")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in slot simulation", e)
                    delay(30000) // Wait 30 seconds before retrying
                }
            }
        }
    }
    
    /**
     * Stop demo and cleanup
     */
    fun stopDemo() {
        simulationJob?.cancel()
        simulationJob = null
        Log.d(TAG, "BookNow demo stopped")
    }
    
    /**
     * Manually trigger slot availability update for testing
     */
    fun triggerSlotUpdate() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                realtimeSetup.simulateSlotChanges()
                Log.d(TAG, "Manual slot update triggered")
            } catch (e: Exception) {
                Log.e(TAG, "Error in manual slot update", e)
            }
        }
    }
    
    /**
     * Get demo statistics
     */
    fun getDemoStats(): Map<String, Any> {
        return mapOf(
            "simulationActive" to (simulationJob?.isActive == true),
            "demoStartTime" to System.currentTimeMillis(),
            "version" to "1.0.0"
        )
    }
}