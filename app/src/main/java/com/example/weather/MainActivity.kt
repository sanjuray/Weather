package com.example.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import com.example.weather.databinding.ActivityMainBinding
import com.example.weather.pojo.ModelClass
import com.example.weather.utils.ApiUtilities
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale


class MainActivity : AppCompatActivity() {

    companion object{
        private const val REQUEST_LOCATION_ACCESS = 100
        private const val REQUEST_LOCATION_TURN_ON = 101
    }

    public final fun onClick(view: View){
        activityMainBinding.getCity.setText("")
    }

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var activityMainBinding: ActivityMainBinding
    private var address: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        activityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        activityMainBinding.mainLayout.visibility = View.GONE

        getCurrentLocation()

        activityMainBinding.getCity.setOnEditorActionListener{v,actionId,keyEvent ->
            if(actionId == EditorInfo.IME_ACTION_SEARCH){
                getCityWeather(activityMainBinding.getCity.text.toString().trim())
                val view = this.currentFocus
                if(view != null){
                    val imm:InputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view.windowToken,0)
                    activityMainBinding.getCity.clearFocus()
                }
                true
            }else false
        }
    }

    private fun getCityWeather(city: String) {
        activityMainBinding.progressLoading.visibility = View.VISIBLE
        ApiUtilities.getApiInterface()?.getCityWeatherData(city,resources.getString(R.string.api_key))
            ?.enqueue(object : Callback<ModelClass>{
                @RequiresApi(Build.VERSION_CODES.O)
                override fun onResponse(call: Call<ModelClass>, response: Response<ModelClass>) {
                    address = city.capitalize()
                    setDataViews(response.body())
                }

                override fun onFailure(call: Call<ModelClass>, t: Throwable) {
                    Toast.makeText(applicationContext,"Not a Valid City Name",Toast.LENGTH_SHORT).show()
                }
            })
    }


    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        if(checkPermissions()){
            if(isLocationEnabled()){
                fusedLocationProviderClient.lastLocation.addOnCompleteListener(this){ task->
                    val location: Location?=task.result
                    if(location != null){
                        Toast.makeText(this,"Success: Location is perceived", Toast.LENGTH_SHORT).show()
                        //Fetch weather here
                        fetchCurrentLocationWeather(location.latitude.toString(), location.longitude.toString())

                    }else{
//                        Toast.makeText(this,"Location can't be located",Toast.LENGTH_SHORT).show()
                        if(checkPermissions() && isLocationEnabled()) getCurrentLocation()
                    }
                }
            }else{
                Toast.makeText(applicationContext,"Turn on Location", Toast.LENGTH_SHORT).show()
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivityForResult(intent, REQUEST_LOCATION_TURN_ON)
            }
        }else{
            //request for permission
            requestPermission()
        }
    }

    //For Weather data fetch
    private fun fetchCurrentLocationWeather(lat: String, long: String) {
        activityMainBinding.progressLoading.visibility = View.VISIBLE
        ApiUtilities.getApiInterface()?.getCurrentWeatherData(lat,long,resources.getString(R.string.api_key))
            ?.enqueue(object: retrofit2.Callback<ModelClass>{
                override fun onResponse(call:retrofit2.Call<ModelClass>,response:Response<ModelClass>){
                    if(response.isSuccessful){getAddress(lat.toDouble(),long.toDouble())
                        setDataViews(response.body())}
                }

                override fun onFailure(call: retrofit2.Call<ModelClass>, t: Throwable) {
                    Toast.makeText(applicationContext,"Error",Toast.LENGTH_SHORT).show()
                    Log.v("impossible",t.toString())
                }
            })
    }

    private fun setDataViews(body: ModelClass?) {
        val df = SimpleDateFormat("dd/MM/yyyy hh:mm")
        val currentDate = df.format(Date())
        activityMainBinding.dateTime.text = currentDate
        activityMainBinding.dayTemp.text = ("Day "+kelvinToCelsius(body!!.main.temp_max)+"°C")
        activityMainBinding.nightTemp.text = ("Night "+kelvinToCelsius(body!!.main.temp_min)+"°C")
        activityMainBinding.mainTemp.text = (""+kelvinToCelsius(body!!.main.temp))
        activityMainBinding.feelsLike.text = ("Feels Like "+kelvinToCelsius(body!!.main.feels_like)+"°C")
        activityMainBinding.weatherType.text = body!!.weather[0].main
        activityMainBinding.pressureValue.text = (""+body!!.main.pressure.toString())
        activityMainBinding.humidityValue.text = (""+body!!.main.humidity+"%")
        activityMainBinding.windSpeedValue.text = (""+body!!.wind.speed.toString()+"m/s")
        activityMainBinding.sunriseValue.text = timeStampToLocalData(body!!.sys.sunrise.toLong())
        activityMainBinding.sunsetValue.text = timeStampToLocalData(body!!.sys.sunset.toLong())
        activityMainBinding.temperatureValue.text = (""+kelvinToCelsius(body!!.main.temp))
        activityMainBinding.getCity.setText(address)

        updateUI(body.weather[0].id)
    }

    private fun updateUI(id: Int) {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        if(id in 200..232){
            window.statusBarColor = resources.getColor(R.color.thunderstorm)
            activityMainBinding.theToolbar.setBackgroundColor(resources.getColor(R.color.thunderstorm))
            activityMainBinding.actualLayout.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.thunderstorm_bg
            )
            activityMainBinding.layoutDetailAbove.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.thunderstorm_bg
            )
            activityMainBinding.layoutDetailBelow.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.thunderstorm_bg
            )
            activityMainBinding.weatherBack.setImageResource(R.drawable.thunderstorm_bg)
            activityMainBinding.weatherIcon.setImageResource(R.drawable.thunderstorm)

        }
        else if(id in 300..321){
            window.statusBarColor = resources.getColor(R.color.drizzle)
            activityMainBinding.theToolbar.setBackgroundColor(resources.getColor(R.color.drizzle))
            activityMainBinding.actualLayout.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.drizzle_bg
            )
            activityMainBinding.layoutDetailAbove.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.drizzle_bg
            )
            activityMainBinding.layoutDetailBelow.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.drizzle_bg
            )
            activityMainBinding.weatherBack.setImageResource(R.drawable.drizzle_bg)
            activityMainBinding.weatherIcon.setImageResource(R.drawable.drizzle)
        }
        else if(id in 500..531){
            window.statusBarColor = resources.getColor(R.color.rain)
            activityMainBinding.theToolbar.setBackgroundColor(resources.getColor(R.color.rain))
            activityMainBinding.actualLayout.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.rainy_bg
            )
            activityMainBinding.layoutDetailAbove.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.rainy_bg
            )
            activityMainBinding.layoutDetailBelow.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.rainy_bg
            )
            activityMainBinding.weatherBack.setImageResource(R.drawable.rainy_bg)
            activityMainBinding.weatherIcon.setImageResource(R.drawable.rain)
        }
        else if(id in 600..620) {
            window.statusBarColor = resources.getColor(R.color.snow)
            activityMainBinding.theToolbar.setBackgroundColor(resources.getColor(R.color.snow))
            activityMainBinding.actualLayout.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.snow_bg
            )
            activityMainBinding.layoutDetailAbove.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.snow_bg
            )
            activityMainBinding.layoutDetailBelow.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.snow_bg
            )
            activityMainBinding.weatherBack.setImageResource(R.drawable.snow_bg)
            activityMainBinding.weatherIcon.setImageResource(R.drawable.snow)
        }
        else if(id in 701..781) {
            window.statusBarColor = resources.getColor(R.color.atmosphere)
            activityMainBinding.theToolbar.setBackgroundColor(resources.getColor(R.color.atmosphere))
            activityMainBinding.actualLayout.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.mist_bg
            )
            activityMainBinding.layoutDetailAbove.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.mist_bg
            )
            activityMainBinding.layoutDetailBelow.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.mist_bg
            )
            activityMainBinding.weatherBack.setImageResource(R.drawable.mist_bg)
            activityMainBinding.weatherIcon.setImageResource(R.drawable.mist)
        }
        else if(id == 800){
            window.statusBarColor = resources.getColor(R.color.clear)
            activityMainBinding.theToolbar.setBackgroundColor(resources.getColor(R.color.clear))
            activityMainBinding.actualLayout.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.clear_bg
            )
            activityMainBinding.layoutDetailAbove.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.clear_bg
            )
            activityMainBinding.layoutDetailBelow.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.clear_bg
            )
            activityMainBinding.weatherBack.setImageResource(R.drawable.clear_bg)
            activityMainBinding.weatherIcon.setImageResource(R.drawable.clear)
        }
        else{
            window.statusBarColor = resources.getColor(R.color.clouds)
            activityMainBinding.theToolbar.setBackgroundColor(resources.getColor(R.color.clouds))
            activityMainBinding.actualLayout.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.mist_bg
            )
            activityMainBinding.layoutDetailAbove.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.mist_bg
            )
            activityMainBinding.layoutDetailBelow.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.mist_bg
            )
            activityMainBinding.weatherBack.setImageResource(R.drawable.mist_bg)
            activityMainBinding.weatherIcon.setImageResource(R.drawable.clouds)
        }
        activityMainBinding.progressLoading.visibility = View.GONE
        activityMainBinding.mainLayout.visibility = View.VISIBLE
    }

    private fun kelvinToCelsius(tempMax: Double): Double {
        return (tempMax - 273.15).toBigDecimal().setScale(1, RoundingMode.UP).toDouble()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun timeStampToLocalData(timeStamp: Long):String{
        val localTime = timeStamp.let{
            Instant.ofEpochSecond(it)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
        }
        return localTime.toString()
    }


    // To get the location co-ords
    private fun checkPermissions(): Boolean{
        return ((ActivityCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                && (ActivityCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED))
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER)
    }

    private fun requestPermission(){
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_LOCATION_ACCESS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == REQUEST_LOCATION_ACCESS){
            if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                Toast.makeText(applicationContext,"Granted",Toast.LENGTH_SHORT).show()
                getCurrentLocation()
            }
            else{
                Toast.makeText(applicationContext,"Denied",Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == REQUEST_LOCATION_TURN_ON){
            if(isLocationEnabled()) getCurrentLocation()
            else Toast.makeText(this,"Location is not Turned on",Toast.LENGTH_SHORT).show()
        }
    }

    fun getAddress(lat: Double, lng: Double) {
        val geocoder = Geocoder(applicationContext, Locale.getDefault())
        try {
            val addresses: List<Address>? = geocoder.getFromLocation(lat, lng, 1)
            val obj: Address = addresses!![0]
            var add: String = ""
            add = """
            $add
            """.trimIndent()
            add = """
            $add
            ${obj.getAdminArea()}
            """.trimIndent()

            add = "${obj.getLocality()}".trimIndent()+","+ "${obj.getAdminArea()}"+","+"${obj.getCountryName()}"
//            add = """
//            $add
//            ${obj.getCountryCode()}
//            """.trimIndent()
//            add = """
//            $add
//            ${obj.getPostalCode()}
//            """.trimIndent()
//            add = """
//            $add
//            ${obj.getSubAdminArea()}
//            """.trimIndent()

//            add = """
//            $add
//            ${obj.getSubThoroughfare()}
//            """.trimIndent()
            Log.v("IGA", "Address$add")
            this.address = add
//            Toast.makeText(this,""+address,Toast.LENGTH_SHORT).show()
//             Toast.makeText(this, "Address=>" + add+" && "+address,
//             Toast.LENGTH_SHORT).show();

            // TennisAppActivity.showDialog(add);
        } catch (e: IOException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }
}