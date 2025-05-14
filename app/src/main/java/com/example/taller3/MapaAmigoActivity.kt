package com.example.taller3

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.taller3.databinding.ActivityMapaAmigoBinding
import com.example.taller3.model.Usuario // Asegúrate que la ruta a tu modelo Usuario sea correcta
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.DecimalFormat

class MapaAmigoActivity : BaseActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapaAmigoBinding

    private var selectedFriendUid: String? = null
    private var selectedFriendName: String? = null
    private var initialFriendLat: Double = 0.0
    private var initialFriendLng: Double = 0.0

    private lateinit var friendDataRef: DatabaseReference
    private lateinit var friendLocationListener: ValueEventListener
    private var friendMarker: Marker? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentUserLocation: Location? = null

    private val PERMISSION_REQUEST_LOCATION = 101
    private val TAG = "MapaAmigoActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapaAmigoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Configura la Toolbar
        // El ID en activity_mapa_amigo.xml es toolbar_mapa_amigo,
        // ViewBinding lo convierte a toolbarMapaAmigo.
        setSupportActionBar(binding.toolbarMapaAmigo)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Para el botón de atrás

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Obtener datos del amigo desde el Intent
        selectedFriendUid = intent.getStringExtra("USER_ID_SELECCIONADO")
        selectedFriendName = intent.getStringExtra("USER_NOMBRE_SELECCIONADO")
        initialFriendLat = intent.getDoubleExtra("USER_LATITUD_SELECCIONADO", 0.0)
        initialFriendLng = intent.getDoubleExtra("USER_LONGITUD_SELECCIONADO", 0.0)

        if (selectedFriendUid == null) {
            Toast.makeText(this, "Error: No se especificó el amigo.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        supportActionBar?.title = "Ubicación de ${selectedFriendName ?: "Amigo"}"

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_container_amigo) as SupportMapFragment // ID corregido en el paso anterior
        mapFragment.getMapAsync(this)

        // Referencia al nodo del amigo en Firebase
        friendDataRef = FirebaseDatabase.getInstance().getReference("usuarios").child(selectedFriendUid!!)
        setupFriendLocationListener()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun checkAndRequestLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                PERMISSION_REQUEST_LOCATION)
        } else {
            // Permisos ya concedidos, obtener ubicación actual
            fetchCurrentUserLocationAndUpdateDistance()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d(TAG, "Permiso de ubicación concedido.")
                fetchCurrentUserLocationAndUpdateDistance()
            } else {
                Toast.makeText(this, "Permiso de ubicación denegado. No se puede calcular la distancia.", Toast.LENGTH_LONG).show()
                binding.Distancia.text = "Distancia: Permiso denegado"
            }
        }
    }

    private fun fetchCurrentUserLocationAndUpdateDistance(friendPositionForDistanceCalc: LatLng? = null) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Intento de obtener ubicación sin permisos.")
            binding.Distancia.text = "Distancia: Sin permiso"
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    currentUserLocation = location
                    Log.d(TAG, "Ubicación actual del usuario: ${location.latitude}, ${location.longitude}")
                    // Si tenemos la posición del amigo (ya sea inicial o actualizada), calculamos la distancia
                    val friendPosToUse = friendPositionForDistanceCalc ?: friendMarker?.position
                    friendPosToUse?.let {
                        updateDistanceText(it)
                    } ?: run {
                        binding.Distancia.text = "Distancia: Esperando ubic. amigo"
                    }
                } else {
                    Log.w(TAG, "La última ubicación conocida del usuario es nula.")
                    binding.Distancia.text = "Distancia: Ubic. actual no disponible"
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al obtener la ubicación actual del usuario.", e)
                binding.Distancia.text = "Distancia: Error ubic. actual"
            }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true // Muestra el botón de "mi ubicación"

        // Intentar activar la capa de "mi ubicación" en el mapa si hay permisos
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mMap.isMyLocationEnabled = true
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de seguridad al activar MyLocation layer", e)
        }


        // Posición inicial del amigo
        if (initialFriendLat != 0.0 || initialFriendLng != 0.0) {
            val friendInitialPosition = LatLng(initialFriendLat, initialFriendLng)
            updateFriendMarkerOnMap(friendInitialPosition, selectedFriendName ?: "Amigo")
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(friendInitialPosition, 15f))
            // Calcular distancia inicial si es posible
            checkAndRequestLocationPermissions() // Esto llamará a fetchCurrentUserLocationAndUpdateDistance
            // y usará friendInitialPosition a través de friendMarker.position
        } else {
            Toast.makeText(this, "Ubicación inicial del amigo no disponible.", Toast.LENGTH_SHORT).show()
            binding.Distancia.text = "Distancia: Ubic. amigo no disp."
            // Aún así, intentar obtener la ubicación del usuario actual para futuras actualizaciones
            checkAndRequestLocationPermissions()
        }
    }

    private fun setupFriendLocationListener() {
        friendLocationListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val amigo = snapshot.getValue(Usuario::class.java)
                if (amigo != null && amigo.latitud != 0.0 && amigo.longitud != 0.0) {
                    val friendCurrentPosition = LatLng(amigo.latitud, amigo.longitud)
                    Log.d(TAG, "Ubicación del amigo actualizada: ${friendCurrentPosition.latitude}, ${friendCurrentPosition.longitude}")
                    updateFriendMarkerOnMap(friendCurrentPosition, selectedFriendName ?: "Amigo")

                    // Actualizar la distancia usando la ubicación más reciente del usuario logueado
                    // No es necesario volver a llamar a fusedLocationClient.lastLocation aquí
                    // si ya se está gestionando o se obtuvo en onMapReady/onResume.
                    // Pero para asegurar que se usa una ubicación lo más fresca posible del usuario actual
                    // al momento que el amigo se mueve, podemos re-obtenerla o usar la última conocida.
                    // Por simplicidad, si currentUserLocation ya está seteado, lo usamos.
                    // Si no, intentamos obtenerla.
                    if (currentUserLocation != null) {
                        updateDistanceText(friendCurrentPosition)
                    } else {
                        // Intentar obtener la ubicación actual si aún no la tenemos
                        fetchCurrentUserLocationAndUpdateDistance(friendCurrentPosition)
                    }

                } else {
                    Log.w(TAG, "Datos de ubicación del amigo incompletos desde Firebase.")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error al leer la ubicación del amigo desde Firebase.", error.toException())
                Toast.makeText(this@MapaAmigoActivity, "Error al obtener ubicación del amigo.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateFriendMarkerOnMap(position: LatLng, title: String) {
        if (!::mMap.isInitialized) return // Asegurarse que el mapa está listo

        if (friendMarker == null) {
            friendMarker = mMap.addMarker(MarkerOptions().position(position).title(title))
        } else {
            friendMarker?.position = position
        }

        // Mover la cámara para centrarla en la nueva posición del amigo con animación
        // Usamos un nivel de zoom de 15f, que es el que se usó inicialmente.
        // Puedes ajustar este valor si prefieres un zoom diferente.
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
        Log.d(TAG, "Cámara movida a la posición del amigo: ${position.latitude}, ${position.longitude}")
    }

    private fun updateDistanceText(friendPosition: LatLng) {
        currentUserLocation?.let { currentLoc ->
            val friendLoc = Location("friendLocationProvider").apply {
                latitude = friendPosition.latitude
                longitude = friendPosition.longitude
            }
            val distanceInMeters = currentLoc.distanceTo(friendLoc)
            val df = DecimalFormat("#.##") // Formato para dos decimales

            val distanceText = if (distanceInMeters >= 1000) {
                val distanceInKm = distanceInMeters / 1000
                "Distancia: ${df.format(distanceInKm)} km"
            } else {
                "Distancia: ${df.format(distanceInMeters)} m"
            }
            binding.Distancia.text = distanceText
            Log.d(TAG, "Distancia calculada: $distanceText")
        } ?: run {
            binding.Distancia.text = "Distancia: Ubic. actual no disponible"
            Log.w(TAG, "No se pudo calcular la distancia, ubicación actual del usuario es nula.")
        }
    }

    override fun onStart() {
        super.onStart()
        // Añadir el listener de Firebase
        if (::friendDataRef.isInitialized && ::friendLocationListener.isInitialized) {
            friendDataRef.addValueEventListener(friendLocationListener)
            Log.d(TAG, "Listener de ubicación del amigo AÑADIDO.")
        }
        // Solicitar ubicación actual al iniciar/reanudar si no la tenemos y hay permisos
        if (currentUserLocation == null) {
            checkAndRequestLocationPermissions()
        }
    }

    override fun onStop() {
        super.onStop()
        // Remover el listener de Firebase para evitar fugas de memoria y actualizaciones innecesarias
        if (::friendDataRef.isInitialized && ::friendLocationListener.isInitialized) {
            friendDataRef.removeEventListener(friendLocationListener)
            Log.d(TAG, "Listener de ubicación del amigo REMOVIDO.")
        }
    }
}