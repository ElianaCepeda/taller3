package com.example.taller3

import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.taller3.databinding.ActivityCrearCuentaBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest

class CrearCuentaActivity : AppCompatActivity() {
    lateinit var binding: ActivityCrearCuentaBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        binding = ActivityCrearCuentaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        irMapaPrincipal()

        binding.buttonCrearCuenta.setOnClickListener {
            crearCuenta()
        }
    }

    private fun crearCuenta(){
        if(validar()){
            val email= binding.editTextEmail.text.toString()
            val password = binding.editTextPassword.text.toString()
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "createUserWithEmail:onComplete:" + task.isSuccessful)
                        val user = auth.currentUser
                        if (user != null) {
                            // Update user info
                            val upcrb = UserProfileChangeRequest.Builder()
                            upcrb.displayName = binding.editTextEmail.text.toString()
                            user.updateProfile(upcrb.build())
                            //guardarUsuarioMem()
                            signIn(email, password)
                        }
                    }
                }

        }
    }

    private fun updateUI(currentUser: FirebaseUser?) {
        if (currentUser != null) {
            val intent = Intent(this, MapaPrincipalActivity::class.java)
            intent.putExtra("user", currentUser.uid)
            startActivity(intent)
        }
    }

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
                    binding.editTextNombre.setText("")
                    binding.editTextPassword.setText("")
                }
            }
    }

    private fun validar(): Boolean {
        var valid = true

        val username=binding.editTextNombre.text.toString()
        if(username.isEmpty()){
            valid=false
            binding.editTextNombre.error="Campo requerido"
        }
        val apellido=binding.editTextApellido.text.toString()
        if(apellido.isEmpty()){
            valid=false
            binding.editTextApellido.error="Campo requerido"
        }
        val email = binding.editTextEmail.text.toString()
        if(!isEmailValid(email)){
            valid=false
            binding.editTextEmail.error="Email no valido"
        }
        if(email.isEmpty()){
            valid= false
            binding.editTextEmail.error = "Campo Requerido"
        }
        val password=binding.editTextPassword.text.toString()
        if(password.isEmpty()){
            valid=false
            binding.editTextPassword.error="Campo requerido"
        }

        val identificacion=binding.editTextNumeroIdentificacion.text.toString()
        if(identificacion.isEmpty()){
            valid=false
            binding.editTextNumeroIdentificacion.error="Campo requerido"
        }
        return valid

    }

    private fun isEmailValid(email: String): Boolean {
        if (!email.contains("@") ||
            !email.contains(".") ||
            email.length < 5)
            return false
        return true
    }

    fun irMapaPrincipal() {
        binding.buttonCrearCuenta.setOnClickListener {
            val intent = Intent(this, MapaPrincipalActivity::class.java)
            startActivity(intent)
        }
    }
}