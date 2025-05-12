package com.example.taller3

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.taller3.databinding.ActivityCrearCuentaBinding

class CrearCuentaActivity : AppCompatActivity() {
    lateinit var binding: ActivityCrearCuentaBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrearCuentaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        irMapaPrincipal()
    }

    fun irMapaPrincipal() {
        binding.buttonCrearCuenta.setOnClickListener {
            val intent = Intent(this, MapaPrincipalActivity::class.java)
            startActivity(intent)
        }
    }
}