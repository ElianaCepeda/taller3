package com.example.taller3

import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

// Declarar BaseActivity como open para que otras clases puedan heredar de ella
open class BaseActivity : AppCompatActivity() {

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuLogOut -> {
                // Lógica de Logout (ej. limpiar sesión, Firebase signOut)
                // FirebaseAuth.getInstance().signOut() // Ejemplo si usas Firebase Auth
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish() // Cierra la actividad actual
                return true
            }
            R.id.menuDisponible_NoDisponible -> {
                if(item.title == "Disponible"){
                    item.title = "No Disponible"
                    Toast.makeText(this, "Estado actualizado a no disponible", Toast.LENGTH_SHORT).show()
                }else{
                    item.title = "Disponible"
                    Toast.makeText(this, "Estado actualizado a disponible", Toast.LENGTH_SHORT).show()
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

    // Podrías añadir métodos helper aquí que las clases hijas puedan usar
    // o sobrescribir si necesitan un comportamiento ligeramente diferente
    // para alguna opción del menú.
}