package com.research.detectmind.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.research.detectmind.BuildConfig
import com.research.detectmind.data.remote.api.SupabaseApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        // PostgREST requires all objects in a batch to have identical keys.
        // encodeDefaults ensures nullable fields with default=null are included;
        // explicitNulls ensures they serialize as `null` rather than being omitted.
        encodeDefaults = true
        explicitNulls = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                // Use header() not addHeader() — replaces rather than appending duplicates
                val builder = original.newBuilder()
                    .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                // Don't set Content-Type here — Retrofit's converter factory sets it on the body
                // Only add Prefer if the request hasn't set one via @Headers
                if (original.header("Prefer") == null) {
                    builder.header("Prefer", "return=minimal")
                }
                chain.proceed(builder.build())
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.SUPABASE_URL + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideSupabaseApi(retrofit: Retrofit): SupabaseApi =
        retrofit.create(SupabaseApi::class.java)
}
