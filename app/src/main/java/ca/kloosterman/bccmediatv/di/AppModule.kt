package ca.kloosterman.bccmediatv.di

import android.content.Context
import android.content.SharedPreferences
import ca.kloosterman.bccmediatv.auth.AuthRepository
import ca.kloosterman.bccmediatv.data.LanguageRepository
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val sessionId = System.currentTimeMillis().toString()

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("bccmedia_prefs", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    fun provideApolloClient(
        authRepository: AuthRepository,
        languageRepository: LanguageRepository,
        okHttpClient: OkHttpClient
    ): ApolloClient {
        val authedClient = okHttpClient.newBuilder()
            .addInterceptor(Interceptor { chain ->
                val token = runBlocking { authRepository.getValidAccessToken() }
                val lang = languageRepository.getLanguage()
                val request = chain.request().newBuilder()
                    .apply { if (token != null) header("Authorization", "Bearer $token") }
                    .header("X-Application", "bccm-android")
                    .header("X-Session-Id", sessionId)
                    .header("Accept-Language", lang)
                    .build()
                chain.proceed(request)
            })
            .build()

        return ApolloClient.Builder()
            .serverUrl("https://api.brunstad.tv/query")
            .okHttpClient(authedClient)
            .build()
    }
}
