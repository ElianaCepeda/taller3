package com.example.taller3

import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.taller3.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class LoginActivity : AppCompatActivity() {
    lateinit var binding: ActivityLoginBinding

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        irCrearCuenta()
        irLogin()

        binding.buttonLogin.setOnClickListener {
            val email = binding.editTextUser.text.toString()
            val password = binding.editTextContrasena.text.toString()
            if (validateForm()) {
                signIn(email, password)
            }
        }
    }

    private fun validateForm(): Boolean {
        var valid = true
        val email = binding.editTextUser.text.toString()
        if (TextUtils.isEmpty(email)) {
            binding.editTextUser.error = "Required."
            valid = false
        } else {
            binding.editTextUser.error = null
        }
        val password = binding.editTextContrasena.text.toString()
        if (TextUtils.isEmpty(password)) {
            binding.editTextContrasena.error = "Required."
            valid = false
        } else {
            binding.editTextContrasena.error = null
        }
        return valid
    }


    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        updateUI(currentUser)
    }

    private fun updateUI(currentUser: FirebaseUser?) {
        if (currentUser != null) {
            val intent = Intent(this, MapaPrincipalActivity::class.java)
            intent.putExtra("user", currentUser.uid)
            startActivity(intent)
        } else {
            binding.editTextUser.setText("")
            binding.editTextContrasena.setText("")
        }
    }

    private fun signIn(email: String, password: String) {
        Log.d(TAG, "signIn")
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                Log.d(TAG, "signInWithEmail:onComplete:" + task.isSuccessful)
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmail:success")
                    val user = auth.currentUser
                    updateUI(user)
                    navigateToMain(user!!)
                } else {
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    Toast.makeText(
                        this, "Fallo en la autenticación.",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.editTextUser.setText("")
                    binding.editTextContrasena.setText("")
                }
            }
    }

    // Función para navegar a la pantalla principal
    private fun navigateToMain(user: FirebaseUser) {
        val intent = Intent(this, MapaPrincipalActivity::class.java).apply {
            // Añade flags para limpiar la pila de actividades anteriores (Login, Bienvenida)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("USER_UID", user.uid) // Pasa el UID como extra
            // Puedes pasar más datos si los necesitas: user.email, user.displayName etc.
        }
        startActivity(intent)
        finish() // Finaliza LoginActivity para que no quede en la pila
    }

    fun irCrearCuenta() {
        binding.textCrearCuenta.setOnClickListener {
            val intent = Intent(this, CrearCuentaActivity::class.java)
            startActivity(intent)
        }
    }

    fun irLogin() {
        binding.buttonLogin.setOnClickListener {
            val intent = Intent(this, MapaPrincipalActivity::class.java)
            startActivity(intent)
        }
    }
}