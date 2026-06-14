package com.example.geocaching

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var tvDistance: TextView
    private lateinit var tvDirection: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvArrow: TextView

    // Клад — Триал спорт, Уфа
    private val treasureLat = 54.7388
    private val treasureLng = 55.9721

    private var myPlacemark: com.yandex.mapkit.map.PlacemarkMapObject? = null
    private var currentArrowAngle = 0f
    private var treasureFound = false

    companion object {
        private const val YANDEX_API_KEY = "367e028e-cca1-4f39-b832-d308a70ca7c5"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        MapKitFactory.setApiKey(YANDEX_API_KEY)
        MapKitFactory.initialize(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapView   = findViewById(R.id.mapView)
        tvDistance = findViewById(R.id.tvDistance)
        tvDirection = findViewById(R.id.tvDirection)
        tvStatus   = findViewById(R.id.tvStatus)
        tvArrow    = findViewById(R.id.tvArrow)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupMap()
        requestLocationPermission()
    }

    private fun setupMap() {
        mapView.map.move(
            CameraPosition(Point(treasureLat, treasureLng), 14.0f, 0.0f, 0.0f)
        )
        // Метка клада
        mapView.map.mapObjects.addPlacemark().apply {
            geometry = Point(treasureLat, treasureLng)
            setIcon(ImageProvider.fromResource(this@MainActivity, android.R.drawable.star_big_on))
            setText("🏆 Клад")
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { updateMyPosition(it) }
            }
        }

        fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
    }

    private fun updateMyPosition(location: Location) {
        if (treasureFound) return  // уже нашли — не обновляем

        val myPoint = Point(location.latitude, location.longitude)

        // Метка "Я здесь"
        myPlacemark?.let { mapView.map.mapObjects.remove(it) }
        myPlacemark = mapView.map.mapObjects.addPlacemark().apply {
            geometry = myPoint
            setIcon(ImageProvider.fromResource(
                this@MainActivity, android.R.drawable.ic_menu_mylocation))
            setText("Я")
        }

        // Расстояние и азимут
        val results = FloatArray(2)
        Location.distanceBetween(
            location.latitude, location.longitude,
            treasureLat, treasureLng, results
        )
        val distanceMeters = results[0]
        val bearing = results[1]   // угол от севера по часовой стрелке

        // Анимация поворота стрелки
        rotateArrow(bearing)

        val distanceText = if (distanceMeters < 1000)
            "%.0f м".format(distanceMeters)
        else
            "%.2f км".format(distanceMeters / 1000)

        tvDistance.text  = "📍 Расстояние: $distanceText"
        tvDirection.text = "🧭 ${bearingToDirection(bearing)} (${bearing.toInt()}°)"
        tvStatus.text    = "🏆 Клад: Триал спорт, Уфа"

        // Клад найден (ближе 30 метров)
        if (distanceMeters < 30) {
            treasureFound = true
            showFoundDialog(distanceText)
        }
    }

    /** Плавно поворачивает стрелку к кладу */
    private fun rotateArrow(newBearing: Float) {
        val anim = RotateAnimation(
            currentArrowAngle, newBearing,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 500
            fillAfter = true
        }
        tvArrow.startAnimation(anim)
        currentArrowAngle = newBearing
    }

    /** Всплывающее окно при нахождении клада */
    private fun showFoundDialog(distanceText: String) {
        tvDistance.text  = "🎉 Ты в $distanceText от клада!"
        tvDirection.text = "✅ Клад совсем рядом!"
        tvStatus.text    = "🏆 Поздравляем!"
        tvArrow.text     = "🏆"

        AlertDialog.Builder(this)
            .setTitle("🎉 Клад найден!")
            .setMessage(
                "Поздравляем!\n\n" +
                        "Ты нашёл сокровище в Триал Спорт, Уфа!\n\n" +
                        "📍 Ты находишься в $distanceText от метки клада.\n\n" +
                        "Лабораторная работа выполнена ✅"
            )
            .setPositiveButton("🏆 Отлично!") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    private fun bearingToDirection(bearing: Float): String {
        val n = (bearing + 360) % 360
        return when {
            n < 22.5 || n >= 337.5 -> "Север ⬆️"
            n < 67.5  -> "Северо-Восток ↗️"
            n < 112.5 -> "Восток ➡️"
            n < 157.5 -> "Юго-Восток ↘️"
            n < 202.5 -> "Юг ⬇️"
            n < 247.5 -> "Юго-Запад ↙️"
            n < 292.5 -> "Запад ⬅️"
            else      -> "Северо-Запад ↖️"
        }
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
        } else {
            startLocationUpdates()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        mapView.onStart()
    }

    override fun onStop() {
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        if (::locationCallback.isInitialized)
            fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onResume() {
        super.onResume()
        if (::fusedLocationClient.isInitialized) startLocationUpdates()
    }
}