package com.example.taller3

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.taller3.databinding.ActivityMapaPrincipalBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth // auth se inicializa en BaseActivity
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import java.io.IOException
import java.io.InputStreamReader


// Data classes para parsear el JSON de puntos de interés
data class PointOfInterest(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

data class LocationsData(
    val locationsArray: List<PointOfInterest>
)

class MapaPrincipalActivity : BaseActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapaPrincipalBinding
    private var userUid: String? = null // UID del usuario logueado, se obtiene de BaseActivity

    // Variables para la ubicación del usuario
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastKnownLocation: Location? = null // Última ubicación conocida del usuario
    private lateinit var locationRequest: LocationRequest
    private lateinit var mLocationCallback: LocationCallback
    private var currentLocationMarker: Marker? = null // Marcador para la ubicación actual del usuario
    private var requestingLocationUpdates = false // Flag para controlar actualizaciones de ubicación

    private val TAG = "MapaPrincipalActivity" // Tag para logs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // Llama a BaseActivity.onCreate, que inicializa 'auth'
        binding = ActivityMapaPrincipalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Configura la Toolbar (asegúrate que el ID 'toolbarMapaPrincipal' exista en tu layout)
        setSupportActionBar(binding.toolbarMapaPrincipal)
        // El título del ActionBar se puede establecer en BaseActivity o aquí si es específico
        // supportActionBar?.title = getString(R.string.title_activity_mapa)

        // Inicialización de servicios de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = createLocationRequest()
        setupLocationCallback() // Configura el callback para actualizaciones de ubicación

        // Obtener UID del usuario
        userUid = getCurrentUserId() // Metodo heredado de BaseActivity
        if (userUid == null) {
            // Si no hay UID, checkAuthentication (heredado) debería redirigir a Login
            // Esta verificación es una doble seguridad.
            Toast.makeText(this, "Error: Usuario no autenticado.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "UID de usuario es nulo en onCreate, checkAuthentication debería manejarlo.")
            checkAuthentication() // Redirige a LoginActivity si no está autenticado
            return // Salir para evitar más ejecuciones si no hay usuario
        }
        Log.d(TAG, "Usuario UID: $userUid")

        // Configuración del fragmento del mapa
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun createLocationRequest(): LocationRequest =
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).apply { // 10 segundos intervalo deseado
            setMinUpdateIntervalMillis(5000) // 5 segundos intervalo mínimo
        }.build()

    private fun setupLocationCallback() {
        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    Log.i(TAG, "Actualización de ubicación en callback: ${location.latitude}, ${location.longitude}")
                    lastKnownLocation = location // Actualiza la última ubicación conocida
                    updateUserMarkerAndCamera(location) // Mueve el marcador del usuario
                    if (requestingLocationUpdates) { // Solo actualiza DB si las actualizaciones están activas
                        updateUserLocationInDatabase(location)
                    }
                }
            }
        }
    }

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineLocationGranted || coarseLocationGranted) {
            Toast.makeText(this, "Permiso de ubicación concedido.", Toast.LENGTH_SHORT).show()
            if (::mMap.isInitialized) {
                enableMyLocationOnMap()
                fetchLastKnownUserLocation() // Intentar obtener la ubicación inicial
                startLocationUpdates()      // Iniciar actualizaciones continuas
            }
        } else {
            Toast.makeText(this, "Permiso de ubicación denegado.", Toast.LENGTH_LONG).show()
            // Considera mostrar los POIs incluso si el permiso de ubicación del usuario es denegado
            if (::mMap.isInitialized) {
                displayPointsOfInterest() // Mostrar POIs independientemente de la ubicación del usuario
            }
        }
    }

    private fun checkLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        requestLocationPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    private fun enableMyLocationOnMap() {
        if (!::mMap.isInitialized) {
            Log.w(TAG, "Mapa no inicializado al intentar activar MyLocation layer.")
            return
        }
        try {
            if (checkLocationPermissions()) {
                mMap.isMyLocationEnabled = true
                mMap.uiSettings.isMyLocationButtonEnabled = true // Botón para centrar en el usuario
            } else {
                Log.w(TAG, "Permisos de ubicación no concedidos, no se puede activar MyLocation layer.")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException al activar MyLocation layer.", e)
        }
    }

    private fun fetchLastKnownUserLocation() {
        if (!checkLocationPermissions()) {
            Log.w(TAG, "No se pueden obtener la última ubicación conocida: permisos denegados.")
            // Si los permisos no están, requestLocationPermissionLauncher ya fue llamado o lo será.
            // Intentar mostrar POIs si el mapa está listo.
            if (::mMap.isInitialized) displayPointsOfInterest()
            return
        }
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        Log.d(TAG, "Última ubicación conocida obtenida: ${location.latitude}, ${location.longitude}")
                        lastKnownLocation = location
                        updateUserMarkerAndCamera(location) // Muestra marcador y mueve cámara
                        updateUserLocationInDatabase(location) // Actualiza DB con esta ubicación inicial
                    } else {
                        Log.d(TAG, "Última ubicación conocida es nula. Esperando primera actualización de FusedLocationProvider.")
                        // Si la ubicación del usuario es nula al inicio,
                        // la cámara podría enfocarse en los POIs o una ubicación por defecto (ej. Bogotá)
                        // displayPointsOfInterest() se encargará de ajustar la cámara si hay POIs.
                        // Si no hay POIs y no hay ubicación de usuario, el mapa podría quedar en una vista por defecto.
                        if (::mMap.isInitialized && !arePoisDisplayed) { // Evitar doble ajuste si POIs ya ajustaron
                            val bogota = LatLng(4.60971, -74.08175) // Centro aproximado de Bogotá
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(bogota, 11f))
                        }
                    }
                    // Siempre intentar mostrar POIs después de intentar obtener la ubicación del usuario
                    if (::mMap.isInitialized && !arePoisDisplayed) displayPointsOfInterest()

                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error al obtener la última ubicación conocida.", e)
                    if (::mMap.isInitialized && !arePoisDisplayed) displayPointsOfInterest() // Intentar mostrar POIs incluso si falla la ubicación del usuario
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException al llamar a fusedLocationClient.lastLocation.", e)
            if (::mMap.isInitialized && !arePoisDisplayed) displayPointsOfInterest()
        }
    }

    private fun updateUserMarkerAndCamera(location: Location) {
        if (!::mMap.isInitialized) return

        val newLatLng = LatLng(location.latitude, location.longitude)
        currentLocationMarker?.remove() // Elimina el marcador anterior del usuario
        currentLocationMarker = mMap.addMarker(
            MarkerOptions()
                .position(newLatLng)
                .title("Mi Ubicación Actual")
            // Podrías usar un ícono diferente para el usuario si quieres
            // .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )

        // La cámara se ajustará globalmente después de añadir los POIs
        // Pero si solo tenemos la ubicación del usuario (aún no POIs o fallo), centramos en el usuario.
        if (!arePoisDisplayed || poiListCache.isNullOrEmpty()) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newLatLng, 15f))
        }
        // Si los POIs ya se mostraron, displayPointsOfInterest se habrá encargado del zoom/bounds.
    }


    private fun updateUserLocationInDatabase(location: Location) {
        if (userUid.isNullOrEmpty()) {
            Log.e(TAG, "userUid es nulo, no se puede actualizar la base de datos.")
            return
        }
        val locationUpdates = hashMapOf<String, Any>(
            "latitud" to location.latitude,
            "longitud" to location.longitude
        )
        // Usa la constante MIscelanius.PATH_USERS
        FirebaseDatabase.getInstance().getReference(MIscelanius.PATH_USERS).child(userUid!!)
            .updateChildren(locationUpdates)
            .addOnSuccessListener {
                Log.d(TAG, "Ubicación del usuario ($userUid) actualizada en Firebase: Lat ${location.latitude}, Lon ${location.longitude}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al actualizar la ubicación del usuario ($userUid) en Firebase.", e)
            }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        Log.d(TAG, "GoogleMap está listo.")
        mMap.uiSettings.isZoomControlsEnabled = true // Habilita controles de zoom

        if (checkLocationPermissions()) {
            Log.d(TAG, "Permisos de ubicación ya concedidos al estar listo el mapa.")
            enableMyLocationOnMap()
            fetchLastKnownUserLocation() // Obtiene la última ubicación conocida y la muestra
            // startLocationUpdates() se llamará desde onResume si los permisos están OK
        } else {
            Log.d(TAG, "Permisos de ubicación NO concedidos al estar listo el mapa. Solicitando...")
            requestLocationPermissions() // Solicita permisos
        }
        // Los POIs se cargarán después de que la ubicación del usuario se intente obtener (en fetchLastKnownUserLocation)
        // o si los permisos son denegados (en el callback de requestLocationPermissionLauncher).
    }

    // --- Funciones para Puntos de Interés (JSON) ---
    private var poiListCache: List<PointOfInterest>? = null
    private var arePoisDisplayed = false

    private fun loadLocationsFromJson(): List<PointOfInterest>? {
        if (poiListCache != null) return poiListCache // Devuelve caché si ya se cargó

        val jsonString: String
        try {
            val inputStream = assets.open("locations.json") // Archivo en app/src/main/assets/
            jsonString = InputStreamReader(inputStream, "UTF-8").use { it.readText() }
        } catch (ioException: IOException) {
            Log.e(TAG, "Error al leer locations.json desde assets.", ioException)
            runOnUiThread { // Asegura que el Toast se muestre en el hilo UI
                Toast.makeText(this, "No se pudieron cargar los puntos de interés.", Toast.LENGTH_LONG).show()
            }
            return null
        }

        return try {
            val gson = Gson()
            val locationsData = gson.fromJson(jsonString, LocationsData::class.java)
            poiListCache = locationsData?.locationsArray
            poiListCache
        } catch (e: Exception) {
            Log.e(TAG, "Error al parsear locations.json.", e)
            runOnUiThread {
                Toast.makeText(this, "Error en el formato de los puntos de interés.", Toast.LENGTH_LONG).show()
            }
            null
        }
    }

    private fun displayPointsOfInterest() {
        if (!::mMap.isInitialized) {
            Log.w(TAG, "El mapa no está listo para mostrar puntos de interés.")
            return
        }
        if (arePoisDisplayed) { // No volver a dibujar si ya están
            Log.d(TAG, "Los puntos de interés ya fueron mostrados.")
            return
        }

        val pointsOfInterest = loadLocationsFromJson() // Carga (o usa caché)

        if (!pointsOfInterest.isNullOrEmpty()) {
            val boundsBuilder = LatLngBounds.Builder()

            // Añadir marcador de usuario actual a los bounds si existe y el mapa está listo
            lastKnownLocation?.let { // Usa lastKnownLocation que se actualiza continuamente
                if(::mMap.isInitialized) { // Doble chequeo por si acaso
                    boundsBuilder.include(LatLng(it.latitude, it.longitude))
                }
            }

            for (point in pointsOfInterest) {
                val latLng = LatLng(point.latitude, point.longitude)
                mMap.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title(point.name)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)) // POIs en azul
                )
                boundsBuilder.include(latLng)
                Log.d(TAG, "Marcador POI añadido para: ${point.name}")
            }
            arePoisDisplayed = true

            try {
                val bounds = boundsBuilder.build()
                // Si solo hay un punto en bounds (ej. solo el usuario sin POIs, o viceversa), newLatLngBounds puede fallar.
                // La cámara se moverá si hay al menos un punto, o dos para crear un bound real.
                if (pointsOfInterest.size > 0 || lastKnownLocation != null) { // Solo ajustar si hay algo que mostrar
                    val padding = 150 // Espacio en píxeles desde los bordes del mapa
                    val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)
                    mMap.animateCamera(cameraUpdate)
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Error al construir LatLngBounds (posiblemente un solo punto): ${e.message}")
                // Fallback: si falla el bounds (ej. solo 1 punto), centrar en ese punto o en el usuario
                if (pointsOfInterest.isNotEmpty() && lastKnownLocation == null) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(pointsOfInterest[0].latitude, pointsOfInterest[0].longitude), 12f))
                } else if (lastKnownLocation != null) { // Si hay ubicación de usuario, priorizarla
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lastKnownLocation!!.latitude, lastKnownLocation!!.longitude), 15f))
                }
            }
        } else {
            Log.w(TAG, "La lista de puntos de interés está vacía o es nula después de cargar.")
            // Si no hay POIs, y hay ubicación de usuario, la cámara ya debería estar centrada en el usuario.
            // Si no hay ni POIs ni ubicación de usuario, podría centrarse en Bogotá o una vista por defecto.
            if (lastKnownLocation == null && ::mMap.isInitialized) {
                val bogota = LatLng(4.60971, -74.08175)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(bogota, 11f))
            }
        }
    }

    // --- Ciclo de vida para actualizaciones de ubicación del usuario ---
    override fun onResume() {
        super.onResume() // Llama a BaseActivity.onResume que hace checkAuthentication
        Log.d(TAG, "onResume llamado.")
        if (checkLocationPermissions()) {
            if (!requestingLocationUpdates) { // Solo iniciar si no estaban ya activas
                startLocationUpdates()
            }
        } else {
            // Si los permisos no están, onMapReady (si ya se llamó) o una acción del usuario debería solicitarlos.
            // O solicitarlos aquí directamente si es la primera vez que se llega a onResume sin ellos.
            // requestLocationPermissions() // Podría ser redundante si onMapReady ya lo hizo.
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause llamado.")
        stopLocationUpdates() // Detener actualizaciones al pausar para ahorrar batería
    }

    private fun startLocationUpdates() {
        if (!checkLocationPermissions()) {
            Log.w(TAG, "Intento de iniciar actualizaciones de ubicación sin permisos.")
            return
        }
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, mLocationCallback, mainLooper /* o null */)
            requestingLocationUpdates = true
            Log.i(TAG, "Actualizaciones de ubicación INICIADAS.")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException al solicitar actualizaciones de ubicación.", e)
            Toast.makeText(this, "Error de seguridad al iniciar ubicación.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopLocationUpdates() {
        if (requestingLocationUpdates) {
            if (::fusedLocationClient.isInitialized && ::mLocationCallback.isInitialized) {
                fusedLocationClient.removeLocationUpdates(mLocationCallback)
                requestingLocationUpdates = false
                Log.i(TAG, "Actualizaciones de ubicación DETENIDAS.")
            }
        }
    }
}