package com.claudemonitor.data.api

import android.util.Base64
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val username: String,
    private val password: String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val credentials = "$username:$password"
        val basic = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)

        val request = chain.request().newBuilder()
            .header("Authorization", "Basic $basic")
            .build()

        return chain.proceed(request)
    }
}
