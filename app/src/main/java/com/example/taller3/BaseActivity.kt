package com.example.taller3

import android.content.Intent
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

// Declarar BaseActivity como open para que otras clases puedan heredar de ella
open class BaseActivity : AppCompatActivity() {

    protected lateinit var auth: FirebaseAuth

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuLogOut -> {

                singOut()

                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish() // Cierra la actividad actual
                return true
            }
            R.id.menuDisponible_NoDisponible -> {
                val newStatus: String
                val currentMenuTitle = item.title.toString()

                // Determina el nuevo estado y actualiza el título del menú
                if (currentMenuTitle.equals("Disponible", ignoreCase = true)) {
                    newStatus = "No Disponible"
                    // Opcional: Cambiar el ícono si tienes uno para "No Disponible"
                    // menuItem.setIcon(R.drawable.ic_no_disponible) // Ejemplo
                } else {
                    newStatus = "Disponible"
                    // Opcional: Cambiar el ícono de vuelta a "Disponible"
                    // menuItem.setIcon(R.drawable.disponible)
                }
                // Actualiza el título del menuItem ANTES de la operación de base de datos para feedback inmediato,
                // pero prepárate para revertirlo si falla.
                item.title = newStatus

                val userId = getCurrentUserId()
                if (userId != null) {
                    val userAvailabilityRef = FirebaseDatabase.getInstance()
                        .getReference(MIscelanius.PATH_USERS) // Usa tu constante para "usuarios"
                        .child(userId)
                        .child("disponibilidad") // Campo en Firebase

                    userAvailabilityRef.setValue(newStatus)
                        .addOnSuccessListener {
                            Toast.makeText(
                                this,
                                "Estado actualizado a '$newStatus'",
                                Toast.LENGTH_SHORT
                            ).show()
                            Log.d(
                                "BaseActivity",
                                "Disponibilidad del usuario $userId actualizada a: $newStatus"
                            )
                        }
                }

                return true
            }
            R.id.menuUsuarios -> {
                // Solo navega si no estamos ya en UsuariosDisponiblesActivity
                if (this !is UsuariosDisponiblesActivity) {
                    val intent = Intent(this, UsuariosDisponiblesActivity::class.java)
                    startActivity(intent)
                }
                return true
            }

            R.id.menuMapaPrincipal -> {
                if (this !is MapaPrincipalActivity) { // Solo navegar si no estamos ya en MapaPrincipalActivity
                    val intent = Intent(this, MapaPrincipalActivity::class.java)
                    // Estos flags ayudan a traer MapaPrincipalActivity al frente si ya existe en la pila,
                    // y limpian las actividades que estén por encima.
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    // Opcional: podrías querer cerrar la actividad actual si no es MapaPrincipalActivity
                    // if (this !is MapaPrincipalActivity) finish()
                } else {
                    Toast.makeText(this, "Ya estás en el Mapa Principal", Toast.LENGTH_SHORT).show()
                }
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        // Verifica si el usuario está logueado cada vez que la actividad pasa a primer plano
        checkAuthentication()
    }

    protected fun checkAuthentication() {
        if (auth.currentUser == null) {
            // Si el usuario no está logueado, redirigir a LoginActivity
            val intent = Intent(this, LoginActivity::class.java).apply {
                // Flags para limpiar la pila y que Login sea la nueva tarea raíz
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish() // Cierra la actividad actual protegida
        }
        // Si está logueado, no hace nada y la actividad continúa.
    }

    private fun singOut() {
        auth.signOut() // Cierra la sesión en Firebase (auth viene de BaseNavbarActivity)
        // Redirige a LoginActivity limpiando la pila
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish() // Cierra ConfiguracionPrincipalActivity
    }

    protected fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }
}