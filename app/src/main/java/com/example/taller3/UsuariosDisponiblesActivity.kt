package com.example.taller3

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.example.taller3.databinding.ActivityUsuariosDisponiblesBinding
import com.google.firebase.auth.FirebaseAuth

class UsuariosDisponiblesActivity : BaseActivity() {
    lateinit var binding: ActivityUsuariosDisponiblesBinding
    private var userUid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        binding = ActivityUsuariosDisponiblesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarUsuariosDisponibles)

        userUid = getCurrentUserId()
        if (userUid == null) {
            // Manejar el caso donde el UID no se pasó correctamente (esto no debería ocurrir si la lógica es correcta)
            Toast.makeText(this, "Error: UID de usuario no encontrado.", Toast.LENGTH_LONG).show()
            // Opcionalmente, forzar logout o redirigir a login
            checkAuthentication() // Llama a la verificación de la clase base
            return // Salir de onCreate si el UID es nulo
        }
        Log.d("PantallaPrincipal", "Usuario UID: $userUid")
    }
}