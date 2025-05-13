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
import com.google.firebase.database.DatabaseReference
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
    // myRef no parece estarse usando globalmente para la referencia del usuario, se obtiene localmente.

    // Variable para rastrear si las actualizaciones de ubicación están activas
    private var requestingLocationUpdates = false

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
                    // Solo actualizar DB si las actualizaciones están activas y la app en primer plano
                    if (requestingLocationUpdates) {
                        updateDatabase(location)
                    }
                }
            }
        }

        binding = ActivityMapaPrincipalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarMapaPrincipal)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        userUid = getCurrentUserId()
        if (userUid == null) {
            Toast.makeText(this, "Error: UID de usuario no encontrado.", Toast.LENGTH_LONG).show()
            checkAuthentication()
            return
        }
        Log.d("PantallaMapaPrincipal", "Usuario UID: $userUid")
    }

    private fun createLocationRequest(): LocationRequest =
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).apply {
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
                enableMapLocationFeatures()
                // setupLocationServicesAndStartUpdates() se llamará desde onMapReady o después de conceder permisos
            }
        } else {
            Toast.makeText(this, "Permiso de ubicación denegado.", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun enableMapLocationFeatures() {
        if (!::mMap.isInitialized) return
        try {
            mMap.isMyLocationEnabled = true
            mMap.uiSettings.isMyLocationButtonEnabled = true
        } catch (e: SecurityException) {
            Log.e("Location", "SecurityException enabling My Location layer", e)
            Toast.makeText(this, "Error de seguridad (capa ubicación).", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupLocationServicesAndGetLastLocation() {
        if (!checkLocationPermissions()) {
            Log.w("Location", "Location permissions not granted. Cannot setup services.")
            // requestLocationPermissionLauncher.launch(...) se llamará desde onMapReady si es necesario
            return
        }
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    Log.d("Location", "Last known location obtained: ${location.latitude}, ${location.longitude}")
                    lastKnownLocation = location
                    updateMapUI(location)
                    updateDatabase(location) // Actualizar con la última conocida al inicio
                    val initialLatLng = LatLng(location.latitude, location.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(initialLatLng, 15f))
                } else {
                    Log.d("Location", "Last known location is null. Will wait for first update if updates are started.")
                }
                // No iniciar actualizaciones aquí directamente, esperar a onResume/onMapReady
            }.addOnFailureListener { e ->
                Log.e("Location", "Error getting last known location: ${e.message}", e)
            }
        } catch (e: SecurityException) {
            Log.e("Location", "SecurityException: fusedLocationClient.lastLocation: ${e.message}", e)
        }
    }

    private fun startLocationUpdates() {
        if (!checkLocationPermissions()) {
            Log.w("Location", "Cannot start location updates: permissions not granted.")
            return
        }
        if (!::fusedLocationClient.isInitialized || !::mLocationCallback.isInitialized || !::locationRequest.isInitialized) {
            Log.w("Location", "Location clients/requests not initialized. Cannot start updates.")
            return
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, mLocationCallback, null /* Looper */)
            requestingLocationUpdates = true // Marcar que estamos solicitando activamente
            Log.d("Location", "Location updates started.")
        } catch (e: SecurityException) {
            Log.e("Location", "SecurityException when calling requestLocationUpdates: ${e.message}", e)
            Toast.makeText(this, "Error de seguridad al iniciar actualizaciones de ubicación.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopLocationUpdates() {
        if (::fusedLocationClient.isInitialized && ::mLocationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(mLocationCallback)
            requestingLocationUpdates = false // Marcar que ya no estamos solicitando
            Log.d("Location", "Location updates stopped.")
        } else {
            Log.w("Location", "FusedLocationClient o LocationCallback no inicializados, no se pueden detener las actualizaciones.")
        }
    }

    override fun onResume() {
        super.onResume()
        // Iniciar actualizaciones solo si el mapa está listo y los permisos concedidos
        if (::mMap.isInitialized && checkLocationPermissions()) {
            Log.d("MapActivityLifecycle", "onResume: Iniciando actualizaciones de ubicación.")
            startLocationUpdates()
        } else {
            Log.d("MapActivityLifecycle", "onResume: Mapa no listo o permisos no concedidos, no se inician actualizaciones aún.")
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("MapActivityLifecycle", "onPause: Deteniendo actualizaciones de ubicación.")
        stopLocationUpdates()
    }

    // onStop también es una opción si quieres que las actualizaciones continúen
    // mientras la actividad está pausada pero no detenida (ej. en multi-ventana)
    // pero para la mayoría de los casos, onPause es mejor para detenerlas.
    // override fun onStop() {
    //     super.onStop()
    //     Log.d("MapActivityLifecycle", "onStop: Deteniendo actualizaciones de ubicación.")
    //     stopLocationUpdates()
    // }


    private fun updateMapUI(location: Location) {
        if (!::mMap.isInitialized) {
            Log.e("MapUI", "Map is not initialized, cannot update UI.")
            return
        }
        val newLatLng = LatLng(location.latitude, location.longitude)
        currentLocationMarker?.remove()
        currentLocationMarker = mMap.addMarker(MarkerOptions().position(newLatLng).title("Mi Ubicación Actual"))
        if (!mMap.cameraPosition.target.equals(newLatLng) || mMap.cameraPosition.zoom < 15f) { // Evitar animaciones innecesarias
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newLatLng, 15f))
        }
        Log.d("MapUI", "Mapa actualizado a: ${location.latitude}, ${location.longitude}")
    }

    private fun updateDatabase(location: Location) {
        if (userUid == null) {
            Log.e("UpdateDB", "userUid es null, no se puede actualizar la base de datos.")
            return
        }
        val locationUpdates = hashMapOf<String, Any>(
            "latitud" to location.latitude,
            "longitud" to location.longitude
        )
        val userLocationRef = database.getReference(MIscelanius.PATH_USERS).child(userUid!!)
        userLocationRef.updateChildren(locationUpdates)
            .addOnSuccessListener {
                Log.d("UpdateDB", "Ubicación del usuario actualizada en Firebase: Lat ${location.latitude}, Lon ${location.longitude}")
            }
            .addOnFailureListener { e ->
                Log.e("UpdateDB", "Error al actualizar la ubicación del usuario en Firebase.", e)
            }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        Log.d("MapReady", "GoogleMap está listo.")

        if (checkLocationPermissions()) {
            Log.d("MapReady", "Permisos de ubicación ya concedidos.")
            enableMapLocationFeatures()
            setupLocationServicesAndGetLastLocation() // Obtener última ubicación conocida
            // startLocationUpdates() se llamará desde onResume si está bien
        } else {
            Log.d("MapReady", "Permisos de ubicación NO concedidos. Solicitando...")
            requestLocationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
}