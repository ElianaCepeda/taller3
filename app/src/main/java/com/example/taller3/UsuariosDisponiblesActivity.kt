package com.example.taller3

import android.os.Bundle
import android.util.Log
import android.widget.ListView
import android.widget.Toast
import com.example.taller3.databinding.ActivityUsuariosDisponiblesBinding
import com.example.taller3.model.Usuario
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class UsuariosDisponiblesActivity : BaseActivity() {
    private lateinit var binding: ActivityUsuariosDisponiblesBinding
    private var currentUserId: String? = null // UID del usuario logueado

    private lateinit var databaseReference: DatabaseReference
    private lateinit var usuariosDisponiblesListener: ValueEventListener
    private lateinit var listViewUsuarios: ListView
    private lateinit var usuariosList: ArrayList<Usuario>
    private lateinit var adapter: UsuarioAdapter

    private val TAG = "UsuariosDisponibles"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsuariosDisponiblesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarUsuariosDisponibles)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Usuarios Disponibles"

        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid

        if (currentUserId == null) {
            Toast.makeText(this, "Error: Usuario no autenticado.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        listViewUsuarios = binding.listViewContactos
        usuariosList = ArrayList()
        adapter = UsuarioAdapter(this, R.layout.item_usuario, usuariosList)
        listViewUsuarios.adapter = adapter

        databaseReference = FirebaseDatabase.getInstance().getReference("usuarios")
        setupUsuariosDisponiblesListener()
    }

    private fun setupUsuariosDisponiblesListener() {
        usuariosDisponiblesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                usuariosList.clear()
                if (snapshot.exists()) {
                    for (userSnapshot in snapshot.children) {
                        try {
                            val usuario = userSnapshot.getValue(Usuario::class.java)
                            if (usuario != null) {
                                // Aquí está la clave: Asignamos la key del snapshot (que es el UID)
                                // al campo uidTransient de nuestro objeto Usuario.
                                usuario.uidTransient = userSnapshot.key

                                // Añadir a la lista solo si está "Disponible" y NO es el usuario actual
                                if (usuario.disponibilidad == "Disponible" && userSnapshot.key != currentUserId) {
                                    usuariosList.add(usuario)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error al convertir usuario: ${e.message} para snapshot: ${userSnapshot.key}", e)
                        }
                    }
                    adapter.notifyDataSetChanged()
                    if (usuariosList.isEmpty()) {
                        Toast.makeText(this@UsuariosDisponiblesActivity, "No hay usuarios disponibles.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.d(TAG, "No hay usuarios en la base de datos.")
                    Toast.makeText(this@UsuariosDisponiblesActivity, "No hay usuarios registrados.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Failed to read value.", error.toException())
            }
        }
    }

    override fun onStart() {
        super.onStart()
        databaseReference.addValueEventListener(usuariosDisponiblesListener)
        Log.d(TAG, "Listener de usuarios añadido.")
    }

    override fun onStop() {
        super.onStop()
        databaseReference.removeEventListener(usuariosDisponiblesListener)
        Log.d(TAG, "Listener de usuarios removido.")
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}