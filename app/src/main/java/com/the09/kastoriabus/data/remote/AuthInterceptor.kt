package com.the09.kastoriabus.data.remote

import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/** Adds "Authorization: Bearer <token>" (and the JSON Accept header) to every API call. */
class AuthInterceptor(private val tokens: TokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer ${tokens.getToken()}")
            .header("Accept", "application/json")
            .build()
        return chain.proceed(request)
    }
}

/**
 * OkHttp calls this automatically when the server answers 401 Unauthorized.
 * We get a fresh token and retry the same request once.
 */
class TokenAuthenticator(private val tokens: TokenProvider) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // Already retried once and still 401: give up instead of looping forever.
        if (response.priorResponse != null) return null

        val rejected = response.request.header("Authorization")?.removePrefix("Bearer ")
        val fresh = tokens.refreshToken(rejected)
        return response.request.newBuilder()
            .header("Authorization", "Bearer $fresh")
            .build()
    }
}
