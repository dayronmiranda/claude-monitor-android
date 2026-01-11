package com.claudemonitor.core.error

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ResourceTest {

    @Test
    fun `success resource returns data`() {
        val resource = Resource.success("test data")

        assertTrue(resource.isSuccess)
        assertFalse(resource.isLoading)
        assertFalse(resource.isError)
        assertEquals("test data", resource.getOrNull())
    }

    @Test
    fun `error resource returns error`() {
        val error = AppError.Network(message = "No connection")
        val resource = Resource.error<String>(error)

        assertTrue(resource.isError)
        assertFalse(resource.isSuccess)
        assertFalse(resource.isLoading)
        assertEquals(error, resource.errorOrNull())
        assertNull(resource.getOrNull())
    }

    @Test
    fun `loading resource indicates loading`() {
        val resource = Resource.loading<String>()

        assertTrue(resource.isLoading)
        assertFalse(resource.isSuccess)
        assertFalse(resource.isError)
        assertNull(resource.getOrNull())
    }

    @Test
    fun `map transforms success data`() {
        val resource = Resource.success(10)
        val mapped = resource.map { it * 2 }

        assertTrue(mapped.isSuccess)
        assertEquals(20, mapped.getOrNull())
    }

    @Test
    fun `map preserves error`() {
        val error = AppError.Server(message = "Server error")
        val resource = Resource.error<Int>(error)
        val mapped = resource.map { it * 2 }

        assertTrue(mapped.isError)
        assertEquals(error, mapped.errorOrNull())
    }

    @Test
    fun `getOrDefault returns default for error`() {
        val resource = Resource.error<String>(AppError.Unknown())

        assertEquals("default", resource.getOrDefault("default"))
    }

    @Test
    fun `getOrDefault returns data for success`() {
        val resource = Resource.success("actual")

        assertEquals("actual", resource.getOrDefault("default"))
    }

    @Test
    fun `onSuccess executes for success`() {
        var called = false
        val resource = Resource.success("data")

        resource.onSuccess { called = true }

        assertTrue(called)
    }

    @Test
    fun `onSuccess does not execute for error`() {
        var called = false
        val resource = Resource.error<String>(AppError.Unknown())

        resource.onSuccess { called = true }

        assertFalse(called)
    }

    @Test
    fun `onError executes for error`() {
        var called = false
        val resource = Resource.error<String>(AppError.Unknown())

        resource.onError { called = true }

        assertTrue(called)
    }

    @Test
    fun `fold handles all states`() {
        val loading = Resource.loading<Int>()
        val success = Resource.success(42)
        val error = Resource.error<Int>(AppError.Unknown())

        assertEquals("loading", loading.fold(
            onLoading = { "loading" },
            onSuccess = { "success: $it" },
            onError = { "error" }
        ))

        assertEquals("success: 42", success.fold(
            onLoading = { "loading" },
            onSuccess = { "success: $it" },
            onError = { "error" }
        ))

        assertEquals("error", error.fold(
            onLoading = { "loading" },
            onSuccess = { "success: $it" },
            onError = { "error" }
        ))
    }

    @Test
    fun `asResource converts flow to resource flow`() = runTest {
        val flow = flowOf(1, 2, 3)
        val resources = flow.asResource().toList()

        // First emission is Loading
        assertTrue(resources.first().isLoading)

        // Subsequent emissions are Success
        assertEquals(1, (resources[1] as Resource.Success).data)
        assertEquals(2, (resources[2] as Resource.Success).data)
        assertEquals(3, (resources[3] as Resource.Success).data)
    }

    @Test
    fun `combineResources combines two success resources`() {
        val r1 = Resource.success(10)
        val r2 = Resource.success(20)

        val combined = combineResources(r1, r2) { a, b -> a + b }

        assertTrue(combined.isSuccess)
        assertEquals(30, combined.getOrNull())
    }

    @Test
    fun `combineResources returns first error`() {
        val error = AppError.Network(message = "Error")
        val r1 = Resource.error<Int>(error)
        val r2 = Resource.success(20)

        val combined = combineResources(r1, r2) { a, b -> a + b }

        assertTrue(combined.isError)
        assertEquals(error, combined.errorOrNull())
    }
}
