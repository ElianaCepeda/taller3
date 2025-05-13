package com.example.taller3

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.taller3.MIscelanius.Companion.PATH_USERS
import com.example.taller3.MIscelanius.Companion.PERMISSION_CAMERA
import com.example.taller3.databinding.ActivityCrearCuentaBinding
import com.example.taller3.model.Usuario
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrearCuentaActivity : AppCompatActivity() {
    lateinit var binding: ActivityCrearCuentaBinding
    private lateinit var auth: FirebaseAuth
    private val database = FirebaseDatabase.getInstance()
    private lateinit var myRef: DatabaseReference

    private lateinit var storageRef: StorageReference
    private lateinit var currentPhotoUri: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        storageRef = FirebaseStorage.getInstance().reference

        binding = ActivityCrearCuentaBinding.inflate(layoutInflater)
        setContentView(binding.root)


        binding.buttonCrearCuenta.setOnClickListener {
            crearCuenta()
        }
        binding.imageView2.setOnClickListener {
            permisosCamara()
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
                            Log.d(TAG, "2. Intentando subir imagen a Storage...")
                            guardarImagenPerfilEnStorage(currentPhotoUri) { success, imageUrl ->
                                if (success && imageUrl != null) {
                                    Log.d(TAG, "3. Imagen subida a Storage con ÉXITO. URL: $imageUrl")

                                    // 4. Guardar datos del usuario (incluyendo URL) en Realtime Database
                                    Log.d(TAG, "4. Intentando guardar datos completos en DB...")
                                    auth.signInWithEmailAndPassword(email, password)
                                    guardarUsuarioCompletoEnDB(imageUrl) { dbSuccess ->
                                        if (dbSuccess) {
                                            Log.d(TAG, "5. Datos guardados en DB con ÉXITO.")
                                            // 6. Todos los datos guardados, ahora iniciar sesión para navegar
                                            Log.d(TAG, "6. Intentando iniciar sesión...")
                                            // Ocultar indicador de progreso
                                            // binding.progressBar.visibility = View.GONE
                                            updateUI(auth.currentUser)
                                        } else {
                                            Log.e(TAG, "5. Error al guardar datos en DB.")
                                            // binding.progressBar.visibility = View.GONE
                                            // binding.buttonCrearCuenta.isEnabled = true
                                            Toast.makeText(this, "Error al guardar datos del usuario.", Toast.LENGTH_LONG).show()
                                            // Aquí podrías considerar eliminar el usuario de Auth o dejarlo así
                                        }
                                    }
                                } else {
                                    Log.e(TAG, "3. Error al subir imagen a Storage.")
                                    // binding.progressBar.visibility = View.GONE
                                    // binding.buttonCrearCuenta.isEnabled = true
                                    Toast.makeText(this, "Cuenta creada, pero error al guardar imagen de perfil.", Toast.LENGTH_LONG).show()
                                    // ¿Qué hacer? ¿Dejar al usuario sin imagen? ¿Borrar la cuenta Auth?
                                    // Por ahora, podríamos intentar iniciar sesión de todas formas,
                                    // pero la app podría fallar después al intentar cargar la imagen.
                                    // Considera la mejor experiencia de usuario para tu caso.
                                    // signIn(email, password) // Opcional: Iniciar sesión incluso si falla la imagen
                                }
                            }
                        }
                    }
                }

        }
    }

    private fun guardarUsuarioCompletoEnDB(imageUrl: String, onResult: (success: Boolean) -> Unit) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onResult(false)
            return
        }

        // Crea el objeto Usuario con todos los datos
        val usuario = Usuario().apply {
            nombre = binding.editTextNombre.text.toString()
            apellido = binding.editTextApellido.text.toString()
            numeroIdentificacion = binding.editTextNumeroIdentificacion.text.toString()
            this.imageUrl = imageUrl // Asigna la URL de la imagen obtenida
            // Añade latitud y longitud si los tienes en este punto, si no, se añadirán después
            // latitud = valorLatitud
            // longitud = valorLongitud
        }

        // Obtiene la referencia y guarda el objeto completo
        myRef = database.getReference("$PATH_USERS/$userId")
        myRef.setValue(usuario)
            .addOnSuccessListener {
                Log.d(TAG, "Datos completos del usuario guardados en DB.")
                onResult(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al guardar datos completos en DB.", e)
                onResult(false)
            }
    }

    private fun updateUI(currentUser: FirebaseUser?) {
        if (currentUser != null) {
            val intent = Intent(this, MapaPrincipalActivity::class.java)
            intent.putExtra("user", currentUser.uid)
            startActivity(intent)
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

        if (!::currentPhotoUri.isInitialized) {
            valid = false
            // Informa al usuario que falta la imagen
            Toast.makeText(this, "Por favor, añade una imagen de perfil", Toast.LENGTH_LONG).show()
        }
        return valid

    }

    private fun isEmailValid(email: String): Boolean {
        return !(!email.contains("@") ||
                !email.contains(".") ||
                email.length < 5)
    }


    //-------------------OnRequestPermissionsResult---------------------------------
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_CAMERA -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Lo que tengas que hacer X2
                    abrirCamara()
                } else {
                    Toast.makeText(this, "Permiso para acceder a camara denegado, reduciendo funcionalidades", Toast.LENGTH_SHORT).show()
                }
                return
            }

            else -> {
            }
        }
    }

//-------------------Fin OnRequestPermissionsResult---------------------------------



    //--------------------Manejo de permisos de camara, tomar foto y guardar en galeria--------------------------------
    private fun permisosCamara() {
        when {
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                //Lo que tenga que hacer
                abrirCamara()

            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this, android.Manifest.permission.CAMERA
            ) -> {

                requestPermissions(
                    arrayOf(android.Manifest.permission.CAMERA),
                    PERMISSION_CAMERA
                )
            }

            else -> {
                requestPermissions(
                    arrayOf(android.Manifest.permission.CAMERA),
                    PERMISSION_CAMERA
                )
            }
        }

    }



    // Lanzador para manejar el resultado de la cámara
    private val tomarFotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // La imagen se guardó en 'currentPhotoUri', ya NO viene en 'result.data'
            if (::currentPhotoUri.isInitialized) {
                // Carga la imagen desde la Uri en el ImageView
                binding.imageView2.setImageURI(currentPhotoUri)


            } else {
                Toast.makeText(this, "Error: Uri de foto no encontrada.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Captura cancelada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun guardarImagenPerfilEnStorage(fileUri: Uri, onResult: (success: Boolean, imageUrl: String?) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            Log.w(TAG, "Usuario no autenticado para subir imagen.")
            onResult(false, null)
            return
        }

        // Define la ruta en Storage: profileImages/userId.jpg
        // Usar el UID asegura que cada usuario tenga una imagen única y la sobreescriba si sube una nueva.
        val profileImageRef = storageRef.child("profileImages/${user.uid}.jpg")

        // Otra opción: usar un nombre aleatorio si no quieres sobreescribir
        // val randomName = UUID.randomUUID().toString()
        // val profileImageRef = storageRef.child("profileImages/$randomName.jpg")

        // Sube el archivo
        profileImageRef.putFile(fileUri)
            .addOnSuccessListener { taskSnapshot ->
                // Imagen subida con éxito, ahora obtenemos la URL de descarga
                profileImageRef.downloadUrl
                    .addOnSuccessListener { uri ->
                        val imageUrl = uri.toString()
                        Log.d(TAG, "Imagen subida con éxito. URL: $imageUrl")
                        onResult(true, imageUrl)
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Error al obtener URL de descarga.", exception)
                        onResult(false, null)
                    }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al subir imagen a Storage.", exception)
                onResult(false, null)
            }
            .addOnProgressListener { taskSnapshot ->
                // Opcional: Mostrar progreso de subida
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                Log.d(TAG, "Subiendo imagen: $progress%")
                // Actualizar UI de progreso si es necesario
            }
    }



    // Metodo para abrir la cámara
    private fun abrirCamara() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        // Crear el archivo y obtener la Uri usando FileProvider
        try {
            val photoFile: File = createImageFile()
            // Guardar la Uri para usarla en el resultado del launcher
            currentPhotoUri = FileProvider.getUriForFile(
                this,
                "com.example.taller3.provider", // DEBE COINCIDIR con android:authorities en Manifest
                photoFile
            )
            // Añadir la Uri al Intent como EXTRA_OUTPUT
            intent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)

            // Lanzar el intent
            tomarFotoLauncher.launch(intent)

        } catch (ex: IOException) {
            // Error creando el archivo
            Log.e("CameraActivity", "Error al crear archivo de imagen", ex)
            Toast.makeText(this, "Error al preparar archivo para la foto.", Toast.LENGTH_SHORT).show()
        } catch (ex: ActivityNotFoundException) {
            // No se encontró app de cámara
            Toast.makeText(this, "No se encontró una aplicación de cámara.", Toast.LENGTH_SHORT).show()
        } catch (ex: Exception) {
            // Otro error inesperado
            Log.e("CameraActivity", "Error inesperado al abrir cámara", ex)
            Toast.makeText(this, "Error inesperado al abrir cámara.", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Metodo para crear el archivo de imagen ---
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Crear un nombre de archivo único basado en la fecha/hora
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES) // Directorio específico de la app
        if (storageDir == null) {
            throw IOException("No se pudo obtener el directorio de almacenamiento externo.")
        }
        if (!storageDir.exists()) {
            storageDir.mkdirs() // Crea el directorio si no existe
        }
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefijo */
            ".jpg", /* sufijo */
            storageDir /* directorio */
        ).apply {
            // Opcional: Podrías guardar la ruta absoluta si la necesitaras más tarde
            // currentPhotoPath = absolutePath
        }
    }

}