package xiaochao.com.data.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val loggingInterceptor = HttpLoggingInterceptor { msg ->
        android.util.Log.d("OKHTTP", msg)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor())
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(ApiConstants.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
        .build()

    val appApiService: AppApiService = retrofit.create(AppApiService::class.java)
    val authApiService: AppAuthApiService = retrofit.create(AppAuthApiService::class.java)
}
