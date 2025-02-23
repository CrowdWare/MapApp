package at.crowdware.mapapp

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import at.crowdware.mapapp.ui.theme.MapAppTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.maplibre.compose.MapView
import com.maplibre.compose.camera.MapViewCamera
import com.maplibre.compose.rememberSaveableMapViewCamera
import com.maplibre.compose.symbols.Symbol
import com.maplibre.compose.symbols.models.SymbolOffset
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MapAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RequestLocationPermission()

                    val context = LocalContext.current
                    val viewModel: LocationViewModel = ViewModelProvider(this)[LocationViewModel::class.java]
                    val locations by viewModel.locations.observeAsState(emptyList())
                    var userLocation by remember { mutableStateOf<LatLng?>(null) }
                    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

                    LaunchedEffect(Unit) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                                location?.let {
                                    userLocation = LatLng(it.latitude, it.longitude)
                                }
                            }
                        }
                    }

                    // 🌍 Warte, bis der Standort verfügbar ist, bevor die Karte angezeigt wird
                    if (userLocation != null) {
                        val mapViewCamera = rememberSaveableMapViewCamera(
                            initialCamera = MapViewCamera.Centered(
                                latitude = userLocation!!.latitude,
                                longitude = userLocation!!.longitude,
                                zoom = 15.0
                            )
                        )

                        MapView(modifier = Modifier.padding(innerPadding),
                            styleUrl = "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json",
                            camera = mapViewCamera,
                            onTapGestureCallback = { println("SymbolExample Tapped at $it") }
                        ) {
                            Symbol(
                                center = userLocation!!,
                                size = 2f,
                                imageId = R.drawable.home,
                                onTap = { println("Eigener Standort") }
                            )

                            locations.forEach { location ->
                                location.position?.coordinates?.let { coords ->
                                    Symbol(
                                        center = LatLng(coords[0], coords[1]), // Latitude, Longitude
                                        size = 2f,
                                        imageId = R.drawable.marker,
                                        onTap = { println("Location: ${location.name}") }
                                    )
                                }
                            }
                        }
                    } else {
                        // Ladeanzeige oder Platzhalter anzeigen, während die Standortsuche läuft
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequestLocationPermission() {
    val locationPermissionState = rememberPermissionState(
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

    LaunchedEffect(Unit) {
        if (!locationPermissionState.status.isGranted) {
            locationPermissionState.launchPermissionRequest()
        }
    }
}

data class LocationItem(
    val id: String,
    val name: String?,
    val position: Position?
)

data class Position(
    val type: String,
    val coordinates: List<Double>
)

interface ApiService {
    @GET("items/items")
    suspend fun getLocationItems(): Response<ApiResponse>
}

data class ApiResponse(
    val data: List<LocationItem>
)

object RetrofitClient {
    private const val BASE_URL = "https://api.utopia-lab.org/"

    val apiService: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}

class LocationViewModel : ViewModel() {
    private val _locations = MutableLiveData<List<LocationItem>>()
    val locations: LiveData<List<LocationItem>> = _locations

    init {
        fetchLocations()
    }

    private fun fetchLocations() {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getLocationItems()
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    _locations.value = apiResponse?.data?.mapNotNull { location ->
                        location.position?.coordinates?.let { coords ->
                            if (coords.size == 2) {
                                LocationItem(
                                    id = location.id,
                                    name = location.name ?: "Unbekannter Ort",
                                    position = Position("Point", listOf(coords[1], coords[0])) // Latitude, Longitude
                                )
                            } else null
                        }
                    } ?: emptyList()
                    println("✅ API erfolgreich: ${_locations.value?.size} Locations geladen")
                } else {
                    println("❌ API Fehler: ${response.code()} - ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                println("❌ Netzwerkfehler: ${e.localizedMessage}")
            }
        }
    }
}


// https://api.utopia-lab.org/items/items?fields=*,to.*,relations.*,user_created.*,offers.*,needs.*,gallery.*.*