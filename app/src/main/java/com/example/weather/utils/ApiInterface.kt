package com.example.weather.utils

import com.example.weather.pojo.ModelClass
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiInterface {

    @GET("weather")
    fun getCurrentWeatherData(
        @Query("lat") latitude:String,
        @Query("lon") longitude:String,
        @Query("appid") apikey: String
    ):retrofit2.Call<ModelClass>

    @GET("weather")
    fun getCityWeatherData(
        @Query("q") city_name:String,
        @Query("appid") api_key: String
    ):retrofit2.Call<ModelClass>

}