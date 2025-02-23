package at.crowdware.mapapp

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.maplibre.compose.MapView
import com.maplibre.compose.camera.MapViewCamera
import com.maplibre.compose.rememberSaveableMapViewCamera
import com.maplibre.compose.symbols.Symbol
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog


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
                    var showDialog by remember { mutableStateOf(false) }
                    var selectedLocation by remember { mutableStateOf<LocationItem?>(null) }

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
                        Box(modifier = Modifier.fillMaxSize()) {
                            MapView(
                                modifier = Modifier.padding(innerPadding),
                                styleUrl = "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json",
                                camera = mapViewCamera
                            ) {
                                Symbol(
                                    center = userLocation!!,
                                    size = 2f,
                                    imageId = R.drawable.home,
                                    onTap = {
                                        println("Eigener Standort")
                                        selectedLocation = LocationItem("","Eigener Standort", "", null)
                                        showDialog = true
                                    }
                                )

                                locations.forEach { location ->
                                    location.position?.coordinates?.let { coords ->
                                        Symbol(
                                            center = LatLng(
                                                coords[0],
                                                coords[1]
                                            ), // Latitude, Longitude
                                            size = 2f,
                                            imageId = R.drawable.marker,
                                            onTap = {
                                                selectedLocation = location
                                                showDialog = true
                                            }
                                        )
                                    }
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

                    if (showDialog && selectedLocation != null) {
                        LocationDialog(showDialog, selectedLocation, onDismiss = {showDialog = false})
                    }
                }
            }
        }
    }
}

@Composable
fun LocationDialog(
    showDialog: Boolean,
    selectedLocation: LocationItem?,
    onDismiss: () -> Unit
) {
    if (showDialog && selectedLocation != null) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                elevation = androidx.compose.material3.CardDefaults.cardElevation(
                    defaultElevation = 8.dp
                )
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth() // Füllt die Breite, aber nicht die Höhe
                            .padding(end = 40.dp) // Platz für den IconButton reservieren
                    ) {
                        selectedLocation.name?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                        selectedLocation.text?.let { text ->
                            val scrollState = rememberScrollState()
                            Box {
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .heightIn(max = 200.dp)
                                        .verticalScroll(scrollState)
                                )
                                // Sichtbare Scrollbar, wenn scrollbar
                                if (scrollState.maxValue > 0) { // Nur anzeigen, wenn scrollbar
                                    VerticalScrollbar(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .height(200.dp),
                                        scrollState = scrollState
                                    )
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd) // Oben rechts im Box
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen")
                    }
                }
            }
        }
    }
}

@Composable
fun VerticalScrollbar(
    modifier: Modifier = Modifier,
    scrollState: ScrollState
) {
    val scrollbarHeight = with(LocalDensity.current) {
        val contentHeight = scrollState.maxValue + 200.dp.toPx() // Gesamthöhe des scrollbaren Inhalts
        val visibleHeight = 200.dp.toPx() // Sichtbare Höhe
        (visibleHeight * visibleHeight / contentHeight).toDp() // Proportionale Scrollbar-Höhe
    }
    val scrollbarOffset = with(LocalDensity.current) {
        val scrollFraction = scrollState.value.toFloat() / scrollState.maxValue
        val maxOffset = 200.dp.toPx() - scrollbarHeight.toPx()
        (scrollFraction * maxOffset).toDp()
    }

    Box(
        modifier = modifier
            .width(8.dp)
            .background(Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .offset(y = scrollbarOffset)
                .width(8.dp)
                .height(scrollbarHeight)
                .background(Color.Gray.copy(alpha = 0.5f))
        )
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
    val text: String?,
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
                                    text = location.text ?: "",
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