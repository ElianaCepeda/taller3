package com.example.taller3

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.taller3.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        irCrearCuenta()
        irLogin()
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