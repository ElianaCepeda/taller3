package com.example.taller3

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.taller3.databinding.ActivityUsuariosDisponiblesBinding

class UsuariosDisponiblesActivity : BaseActivity() {
    lateinit var binding: ActivityUsuariosDisponiblesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsuariosDisponiblesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarUsuariosDisponibles)
    }
}