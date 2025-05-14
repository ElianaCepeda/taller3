package com.example.taller3

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.taller3.model.Usuario // Asegúrate que la importación de tu modelo Usuario sea correcta
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener // Necesario para cargar el nombre del usuario actual para el menú

open class BaseActivity : AppCompatActivity() {

    protected lateinit var auth: FirebaseAuth
    private lateinit var usersRef: DatabaseReference
    private lateinit var globalUsersAvailabilityListener: ChildEventListener
    private val otherUsersAvailabilityMap = mutableMapOf<String, String>() // Para detectar el CAMBIO a Disponible

    private val TAG_BASE_ACTIVITY = "BaseActivity" // Tag para logs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance() // Inicializa auth aquí

        if (auth.currentUser != null) { // Solo configurar listeners si hay un usuario logueado
            usersRef = FirebaseDatabase.getInstance().getReference(MIscelanius.PATH_USERS)
            setupGlobalUsersAvailabilityListener()
        } else {
            // Si no hay usuario, checkAuthentication() en onResume se encargará de redirigir
            Log.w(TAG_BASE_ACTIVITY, "Usuario no logueado en onCreate, no se configuran listeners globales.")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuLogOut -> {

                auth.signOut()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                return true
            }
            R.id.menuDisponible_NoDisponible -> {
                toggleUserAvailability(item)
                return true
            }
            // ... tus otros casos del menú ...
            R.id.menuUsuarios -> {
                if (this !is UsuariosDisponiblesActivity) {
                    val intent = Intent(this, UsuariosDisponiblesActivity::class.java)
                    startActivity(intent)
                }
                return true
            }
            R.id.menuMapaPrincipal -> {
                if (this !is MapaPrincipalActivity) {
                    val intent = Intent(this, MapaPrincipalActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Ya estás en el Mapa Principal", Toast.LENGTH_SHORT).show()
                }
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    protected fun toggleUserAvailability(menuItem: MenuItem) {
        val newStatus: String
        val currentMenuTitle = menuItem.title.toString()

        if (currentMenuTitle.equals("Disponible", ignoreCase = true)) {
            newStatus = "No Disponible"
        } else {
            newStatus = "Disponible"
        }
        menuItem.title = newStatus
        // Opcional: Cambiar íconos aquí también

        val userId = getCurrentUserId()
        if (userId != null) {
            val userAvailabilityRef = FirebaseDatabase.getInstance()
                .getReference(MIscelanius.PATH_USERS)
                .child(userId)
                .child("disponibilidad")

            userAvailabilityRef.setValue(newStatus)
                .addOnSuccessListener {
                    Toast.makeText(this, "Tu estado es ahora '$newStatus'", Toast.LENGTH_SHORT).show()
                    Log.d(TAG_BASE_ACTIVITY, "Disponibilidad propia actualizada a: $newStatus")
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error al actualizar tu estado: ${e.message}", Toast.LENGTH_LONG).show()
                    menuItem.title = currentMenuTitle // Revertir
                }
        } else {
            Toast.makeText(this, "Error: Usuario no autenticado.", Toast.LENGTH_LONG).show()
            menuItem.title = currentMenuTitle // Revertir
        }
    }

    private fun setupGlobalUsersAvailabilityListener() {
        globalUsersAvailabilityListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                // Cuando un listener se añade, onChildAdded se llama para cada hijo existente.
                // Almacenamos su estado actual para futuras comparaciones en onChildChanged.
                val userId = snapshot.key
                val user = snapshot.getValue(Usuario::class.java)
                if (userId != null && user != null && userId != getCurrentUserId()) {
                    otherUsersAvailabilityMap[userId] = user.disponibilidad ?: "No Disponible" // Asumir "No Disponible" si es nulo
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val changedUserId = snapshot.key
                val currentUserUid = getCurrentUserId()

                // No mostrar Toast para los cambios propios
                if (changedUserId == null || changedUserId == currentUserUid) {
                    // Actualizar el mapa para el usuario actual si es un cambio propio,
                    // por si el valor era nulo antes y ahora se establece.
                    if (changedUserId != null) {
                        val user = snapshot.getValue(Usuario::class.java)
                        otherUsersAvailabilityMap[changedUserId] = user?.disponibilidad ?: "No Disponible"
                    }
                    return
                }

                val changedUser = snapshot.getValue(Usuario::class.java)
                if (changedUser != null) {
                    val newAvailability = changedUser.disponibilidad
                    val oldAvailability = otherUsersAvailabilityMap[changedUserId]

                    Log.d(TAG_BASE_ACTIVITY, "Cambio detectado para $changedUserId: $oldAvailability -> $newAvailability")

                    // Notificar solo si el estado CAMBIÓ a "Disponible" desde otro estado
                    if (newAvailability == "Disponible" && oldAvailability != "Disponible") {
                        val userName = "${changedUser.nombre ?: ""} ${changedUser.apellido ?: ""}".trim()
                        val displayName = if (userName.isNotEmpty()) userName else "Alguien"

                        // Asegurarse que el Toast se muestra en el hilo UI
                        runOnUiThread {
                            Toast.makeText(
                                applicationContext, // Usar applicationContext para Toasts "globales"
                                "$displayName ahora está disponible.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    // Actualizar el estado conocido para este usuario
                    otherUsersAvailabilityMap[changedUserId] = newAvailability ?: "No Disponible"
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                // Si un usuario es eliminado, lo quitamos del mapa de seguimiento
                snapshot.key?.let { otherUsersAvailabilityMap.remove(it) }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // No es relevante para este caso de uso
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG_BASE_ACTIVITY, "Listener global de disponibilidad cancelado.", error.toException())
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (auth.currentUser != null && ::usersRef.isInitialized && ::globalUsersAvailabilityListener.isInitialized) {
            Log.d(TAG_BASE_ACTIVITY, "Añadiendo listener global de disponibilidad.")
            usersRef.addChildEventListener(globalUsersAvailabilityListener)
            // Cargar estados iniciales de otros usuarios para el mapa de seguimiento.
            // onChildAdded del listener ya hace esto la primera vez.
        }
    }

    override fun onStop() {
        super.onStop()
        if (::usersRef.isInitialized && ::globalUsersAvailabilityListener.isInitialized) {
            Log.d(TAG_BASE_ACTIVITY, "Quitando listener global de disponibilidad.")
            usersRef.removeEventListener(globalUsersAvailabilityListener)
            otherUsersAvailabilityMap.clear() // Limpiar el mapa al detener
        }
    }

    override fun onResume() {
        super.onResume()
        checkAuthentication() // Muy importante para la seguridad
    }

    protected fun checkAuthentication() {
        if (!::auth.isInitialized) {
            auth = FirebaseAuth.getInstance()
        }
        if (auth.currentUser == null) {
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    protected fun getCurrentUserId(): String? {
        if (!::auth.isInitialized) {
            auth = FirebaseAuth.getInstance()
        }
        return auth.currentUser?.uid
    }
}