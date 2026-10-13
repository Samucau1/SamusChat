package com.samuschat
import com.samuschat.util.isProtectedAttachment
import org.junit.Assert.*
import org.junit.Test
class ProtectedAttachmentTest {
    @Test fun authorizationCanOnlyBeSentToExactApiOriginAndAttachmentPath() {
        val base = "https://api.example.test/"
        val name = "00000000-0000-0000-0000-000000000000.txt"
        assertTrue(isProtectedAttachment(base, "${base}uploads/$name"))
        assertFalse(isProtectedAttachment(base, "https://evil.example.test/uploads/$name"))
        assertFalse(isProtectedAttachment(base, "http://api.example.test/uploads/$name"))
        assertFalse(isProtectedAttachment(base, "https://user@api.example.test/uploads/$name"))
        assertFalse(isProtectedAttachment(base, "${base}uploads/$name?token=anything"))
        assertFalse(isProtectedAttachment(base, "${base}uploads/../../config"))
    }
}
