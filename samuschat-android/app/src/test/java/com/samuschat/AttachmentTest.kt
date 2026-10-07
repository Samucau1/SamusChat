package com.samuschat

import com.samuschat.data.repository.AttachmentPolicy
import com.samuschat.util.attachmentUrl
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class AttachmentTest {
    @Test fun supportsBackendTypesAndUnknownSize() {
        AttachmentPolicy.types.forEach { AttachmentPolicy.validate(it, null) }
        AttachmentPolicy.validate("application/pdf", AttachmentPolicy.MAX_BYTES)
    }
    @Test fun rejectsUnsupportedEmptyAndOversizedFiles() {
        listOf(null to 1L, "application/zip" to 1L, "image/png" to 0L,
            "text/plain" to AttachmentPolicy.MAX_BYTES + 1).forEach { (type, size) ->
            assertThrows(IllegalArgumentException::class.java) { AttachmentPolicy.validate(type, size) }
        }
    }
    @Test fun preservesActualBytesAndRejectsEmptyStreams() {
        val data = byteArrayOf(0, 1, -1, 42)
        val result = ByteArrayOutputStream()
        assertEquals(4L, AttachmentPolicy.copy(ByteArrayInputStream(data), result))
        assertArrayEquals(data, result.toByteArray())
        assertThrows(IllegalArgumentException::class.java) {
            AttachmentPolicy.copy(ByteArrayInputStream(byteArrayOf()), result)
        }
    }
    @Test fun boundsActualBytesEvenWithoutProviderSize() {
        val discard = object : OutputStream() {
            override fun write(b: Int) {}
            override fun write(b: ByteArray, off: Int, len: Int) {}
        }
        val bytes = ByteArray(AttachmentPolicy.MAX_BYTES.toInt() + 1)
        assertEquals(AttachmentPolicy.MAX_BYTES,
            AttachmentPolicy.copy(ByteArrayInputStream(bytes, 0, bytes.size - 1), discard))
        assertThrows(IllegalArgumentException::class.java) {
            AttachmentPolicy.copy(ByteArrayInputStream(bytes), discard)
        }
    }
    @Test fun resolvesRelativeUrlsAndEmulatorDevelopmentUploads() {
        val base = "http://10.0.2.2:8080/"
        assertEquals(base + "uploads/test.png", attachmentUrl(base, "/uploads/test.png"))
        assertEquals(base + "uploads/test.png", attachmentUrl(base, "http://localhost:8080/uploads/test.png"))
        assertEquals(base + "uploads/test.png", attachmentUrl(base, "http://127.0.0.1:8080/uploads/test.png"))
        assertEquals("https://cdn.example.com/test.png", attachmentUrl(base, "https://cdn.example.com/test.png"))
        assertEquals("http://localhost:9000/uploads/test.png", attachmentUrl(base, "http://localhost:9000/uploads/test.png"))
        assertEquals("http://localhost:8080/uploads/test.png", attachmentUrl("https://api.example.com/", "http://localhost:8080/uploads/test.png"))
        assertNull(attachmentUrl(base, "javascript:alert(1)"))
        assertNull(attachmentUrl(base, "file:///test.png"))
    }
}
