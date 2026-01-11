package com.claudemonitor.data.api

import com.claudemonitor.data.model.*
import retrofit2.http.*

interface ApiService {
    @GET("api/health")
    suspend fun getHealth(): HealthResponse

    @GET("api/host")
    suspend fun getHost(): ApiResponse<HostInfo>

    @GET("api/projects")
    suspend fun getProjects(): ApiResponse<List<Project>>

    @GET("api/projects/{path}")
    suspend fun getProject(@Path("path", encoded = true) path: String): ApiResponse<Project>

    @GET("api/projects/{path}/sessions")
    suspend fun getSessions(@Path("path", encoded = true) path: String): ApiResponse<List<Session>>

    @PUT("api/projects/{path}/sessions/{id}/rename")
    suspend fun renameSession(
        @Path("path", encoded = true) path: String,
        @Path("id") sessionId: String,
        @Body body: RenameRequest
    ): ApiResponse<Session>

    @DELETE("api/projects/{path}/sessions/{id}")
    suspend fun deleteSession(
        @Path("path", encoded = true) path: String,
        @Path("id") sessionId: String
    ): ApiResponse<Unit>

    @GET("api/terminals")
    suspend fun getTerminals(): ApiResponse<List<Terminal>>

    @GET("api/terminals/{id}")
    suspend fun getTerminal(@Path("id") terminalId: String): ApiResponse<Terminal>

    @POST("api/terminals")
    suspend fun createTerminal(@Body config: TerminalConfig): ApiResponse<Terminal>

    @POST("api/terminals/{id}/kill")
    suspend fun killTerminal(@Path("id") terminalId: String): ApiResponse<Unit>

    @POST("api/terminals/{id}/resume")
    suspend fun resumeTerminal(@Path("id") terminalId: String): ApiResponse<Terminal>

    @POST("api/terminals/{id}/resize")
    suspend fun resizeTerminal(
        @Path("id") terminalId: String,
        @Body body: ResizeRequest
    ): ApiResponse<Unit>

    @DELETE("api/terminals/{id}")
    suspend fun deleteTerminal(@Path("id") terminalId: String): ApiResponse<Unit>

    @GET("api/filesystem/dir")
    suspend fun listDirectory(@Query("path") path: String): ApiResponse<DirectoryListing>
}
