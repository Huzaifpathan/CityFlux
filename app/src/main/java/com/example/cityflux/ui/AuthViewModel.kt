package com.example.cityflux.ui.login

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth

class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    fun login(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            Log.e("CityFlux", "Email or password empty")
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                Log.d("CityFlux", "Login successful")
                // NEXT STEP: Navigate to Role Screen
            }
            .addOnFailureListener {
                Log.e("CityFlux", it.message ?: "Login failed")
            }
    }
}
