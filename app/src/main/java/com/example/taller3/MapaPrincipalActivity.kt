package com.example.taller3

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.taller3.MIscelanius.Companion.PATH_USERS

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.taller3.databinding.ActivityMapaPrincipalBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.Marker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MapaPrincipalActivity : BaseActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapaPrincipalBinding
    private var userUid: String? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastKnownLocation: Location? = null

    private lateinit var locationRequest: LocationRequest
    private lateinit var mLocationCallback: LocationCallback
    private var currentLocationMarker: Marker? = null

    private val database = FirebaseDatabase.getInstance()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = createLocationRequest()

        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                Log.i("LOCATION", "Location update in the callback: $location")
                if (location != null) {
                    updateMapUI(location)
                    updateDatabase(location)
                }
            }
        }

        binding = ActivityMapaPrincipalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarMapaPrincipal)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        userUid = getCurrentUserId()
        if (userUid == null) {
            // Manejar el caso donde el UID no se pasó correctamente (esto no debería ocurrir si la lógica es correcta)
            Toast.makeText(this, "Error: UID de usuario no encontrado.", Toast.LENGTH_LONG).show()
            // Opcionalmente, forzar logout o redirigir a login
            checkAuthentication() // Llama a la verificación de la clase base
            return // Salir de onCreate si el UID es nulo
        }
        Log.d("PantallaMapaPrincipal", "Usuario UID: $userUid")
    }

    private fun createLocationRequest(): LocationRequest =
// New builder
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,10000).apply {
            setMinUpdateIntervalMillis(5000)
        }.build()



    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineLocationGranted || coarseLocationGranted) {
            Toast.makeText(this, "Permiso de ubicación concedido.", Toast.LENGTH_SHORT).show()
            if (::mMap.isInitialized) {
                try {
                    mMap.isMyLocationEnabled = true
                    mMap.uiSettings.isMyLocationButtonEnabled = true
                } catch (e: SecurityException) {
                    Log.e("Location", "SecurityException enabling My Location layer after permission granted", e)
                    Toast.makeText(this, "Error de seguridad (capa ubicación).", Toast.LENGTH_SHORT).show()
                }
                setupLocationServicesAndStartUpdates()
            } else {
                Log.d("Permissions", "Permiso concedido, esperando a onMapReady para configurar ubicación.")
            }

        } else {
            Toast.makeText(this, "Permiso de ubicación denegado. El mapa podría no mostrar tu posición actual.", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupLocationServicesAndStartUpdates() {
        if (!checkLocationPermissions()) {
            Log.w("Location", "Location permissions not granted. Cannot setup services.")
            Toast.makeText(this, "Permisos de ubicación no concedidos.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    Log.d("Location", "Last known location obtained: ${location.latitude}, ${location.longitude}")
                    lastKnownLocation = location

                    updateMapUI(location)
                    updateDatabase(location)

                    val initialLatLng = LatLng(location.latitude, location.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(initialLatLng, 15f))

                } else {
                    Log.d("Location", "Last known location is null. Will wait for first update.")

                }


                startLocationUpdates()

            }.addOnFailureListener { e ->
                Log.e("Location", "Error getting last known location: ${e.message}", e)

                startLocationUpdates()
            }
        } catch (e: SecurityException) {
            Log.e("Location", "SecurityException when calling fusedLocationClient.lastLocation: ${e.message}", e)
            Toast.makeText(this, "Error de seguridad al obtener última ubicación.", Toast.LENGTH_SHORT).show()

            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        if (!checkLocationPermissions()) {
            Log.w("Location", "Cannot start location updates: permissions not granted.")
            Toast.makeText(this, "Permisos de ubicación no concedidos para iniciar actualizaciones.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!::fusedLocationClient.isInitialized || !::mLocationCallback.isInitialized || !::locationRequest.isInitialized) {
            Log.w("Location", "Location clients/requests not initialized. Cannot start updates.")
            return
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, mLocationCallback, null /* Looper */)
            Log.d("Location", "Location updates started.")
        } catch (e: SecurityException) {
            Log.e("Location", "SecurityException when calling requestLocationUpdates: ${e.message}", e)
            Toast.makeText(this, "Error de seguridad al iniciar actualizaciones de ubicación.", Toast.LENGTH_SHORT).show()
        }
    }



    private fun updateMapUI(location: Location) {
        if (!::mMap.isInitialized) {
            Log.e("MapUI", "Map is not initialized, cannot update UI.")
            return
        }

        val newLatLng = LatLng(location.latitude, location.longitude)

        currentLocationMarker?.remove()

        currentLocationMarker = mMap.addMarker(MarkerOptions().position(newLatLng).title("Mi Ubicación Actual"))


        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newLatLng, 15f))

        Log.d("MapUI", "Mapa actualizado a: ${location.latitude}, ${location.longitude}")
    }

    private fun updateDatabase(location: Location) {
        if (userUid == null) {
            Log.e("UpdateDB", "userUid es null, no se puede actualizar la base de datos.")
            // Opcional: Podrías mostrar un Toast o intentar obtener el UID de nuevo.
            Toast.makeText(this, "Error de usuario, no se pudo actualizar ubicación en DB.", Toast.LENGTH_SHORT).show()
            // userUid = getCurrentUserId() // Intenta obtenerlo de nuevo
            // if (userUid == null) return // Si sigue siendo null, no continúes
            return
        }


        val locationUpdates = hashMapOf<String, Any>(
            "latitud" to location.latitude,
            "longitud" to location.longitude
            // Opcional: puedes añadir una marca de tiempo para la última actualización
            // "ultimaActualizacionUbicacion" to System.currentTimeMillis() // O ServerValue.TIMESTAMP
        )

        // Obtén la referencia al nodo del usuario específico
        val userLocationRef = database.getReference(PATH_USERS).child(userUid!!)
        // userUid!! es seguro aquí debido a la comprobación de null anterior.
        // Usa updateChildren para actualizar solo estos campos sin borrar otros datos del usuario
        userLocationRef.updateChildren(locationUpdates)
            .addOnSuccessListener {
                Log.d("UpdateDB", "Ubicación del usuario actualizada en Firebase: Lat ${location.latitude}, Lon ${location.longitude}")
                // Opcional: Mostrar un Toast de éxito muy discreto o ninguno si es frecuente
                // Toast.makeText(this, "Ubicación actualizada en DB", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("UpdateDB", "Error al actualizar la ubicación del usuario en Firebase.", e)
                // Opcional: Mostrar un Toast de error si es importante para el usuario
                // Toast.makeText(this, "Error al guardar ubicación en DB.", Toast.LENGTH_SHORT).show()
            }
    }




    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (checkLocationPermissions()) {
            setupLocationServicesAndStartUpdates()
            try {
                mMap.isMyLocationEnabled = true
                mMap.uiSettings.isMyLocationButtonEnabled = true
            } catch (e: SecurityException) {
                Log.e("MapReady", "SecurityException enabling My Location layer in onMapReady", e)
                Toast.makeText(this, "Error de seguridad (capa ubicación).", Toast.LENGTH_SHORT).show()
            }
        } else {
            requestLocationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

    }


}