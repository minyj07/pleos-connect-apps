package com.example.drivecoach.share

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ShareClientTest {

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val base = "http://127.0.0.1:${server.address.port}"
    private val seen = mutableListOf<String>()

    @AfterTest
    fun stop() = server.stop(0)

    private fun answer(path: String, status: Int, body: String = "") {
        server.createContext(path) { ex ->
            val received = ex.requestBody.readBytes()
            seen += "${ex.requestMethod} ${ex.requestURI} auth=${ex.requestHeaders.getFirst("Authorization")} type=${ex.requestHeaders.getFirst("Content-Type")} bytes=${received.size}"
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) }
            ex.close()
        }
    }

    @Test
    fun createUploadsOctetStreamAndParsesTheAnswer() = runBlocking<Unit> {
        answer("/v1/shares", 201, """{"id":"AAAAAAAAAAAAAAAAAAAAAA","deleteToken":"t","ttlMs":604800000,"viewBaseUrl":"https://demo.example.com","extra":1}""")
        val r = HttpShareApi(base).create(ByteArray(40) { 1 })
        val created = assertIs<ShareResult.Ok<CreatedShare>>(r).value
        assertEquals("AAAAAAAAAAAAAAAAAAAAAA", created.id)
        assertEquals(604_800_000L, created.ttlMs)
        assertEquals("https://demo.example.com", created.viewBaseUrl)
        assertEquals(listOf("POST /v1/shares auth=null type=application/octet-stream bytes=40"), seen)
    }

    @Test
    fun deleteSendsIdAndTokenOnlyInTheHeader() = runBlocking<Unit> {
        answer("/v1/share", 204)
        assertIs<ShareResult.Ok<Unit>>(HttpShareApi("$base/").delete("ID", "TOKEN"))
        assertEquals(listOf("DELETE /v1/share auth=Bearer ID.TOKEN type=null bytes=0"), seen)
    }

    @Test
    fun statusCodesMapToErrorKinds() = runBlocking<Unit> {
        for ((status, kind) in listOf(429 to ShareErrorKind.RATE_LIMITED, 413 to ShareErrorKind.TOO_LARGE, 507 to ShareErrorKind.SERVER)) {
            val path = "/s$status"
            answer("$path/v1/shares", status, """{"error":{"code":"X","message":"m"}}""")
            assertEquals(kind, assertIs<ShareResult.Failed>(HttpShareApi(base + path).create(ByteArray(40))).kind, "HTTP $status")
        }
        answer("/v1/share", 200, "not json")
        assertIs<ShareResult.Ok<Unit>>(HttpShareApi(base).delete("a", "b"), "a delete answer has no body to parse")
    }

    @Test
    fun unexpectedAnswerIsAServerError() = runBlocking<Unit> {
        answer("/v1/shares", 201, "<html>proxy login</html>")
        assertEquals(ShareErrorKind.SERVER, assertIs<ShareResult.Failed>(HttpShareApi(base).create(ByteArray(40))).kind)
    }

    @Test
    fun unreachableServerIsOfflineAndBlankUrlIsNotConfigured() = runBlocking<Unit> {
        server.stop(0)
        assertEquals(ShareErrorKind.OFFLINE, assertIs<ShareResult.Failed>(HttpShareApi(base).create(ByteArray(40))).kind)
        assertEquals(ShareErrorKind.NOT_CONFIGURED, assertIs<ShareResult.Failed>(HttpShareApi(" ").create(ByteArray(40))).kind)
        assertContentEquals(emptyList(), seen)
    }
}
