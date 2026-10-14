package com.samuschat

import com.samuschat.ui.home.*
import org.junit.Assert.*
import org.junit.Test

class HomeRulesTest {
    @Test fun friendSearchIgnoresCaseAndSurroundingWhitespace() {
        assertTrue(matchesFriend("Alex", "  aLe  "))
        assertTrue(matchesFriend("Alex", ""))
        assertFalse(matchesFriend("Alex", "Maria"))
    }

    @Test fun demoMessageRejectsBlankAndOversizedInput() {
        for (draft in listOf("", "   ", "\n\t", "a".repeat(2001))) assertFalse(canSendDemoMessage(draft))
        assertTrue(canSendDemoMessage("Olá, Alex!"))
        assertTrue(canSendDemoMessage("a".repeat(2000)))
    }

    @Test fun nameRequiresLoadedProfileAndActualChange() {
        assertFalse(canSaveProfileName("Ana", null))
        assertFalse(canSaveProfileName(" Ana ", "Ana"))
        assertFalse(canSaveProfileName(" \n ", "Ana"))
        assertTrue(canSaveProfileName(" Ana Silva ", "Ana"))
    }

    @Test fun profileNameLimitAppliesToTrimmedValue() {
        assertTrue(canSaveProfileName(" " + "a".repeat(50) + " ", "Ana"))
        assertFalse(canSaveProfileName("a".repeat(51), "Ana"))
    }
    @Test fun profileNameRejectsTagsAndControlCharactersButAcceptsAccents() {
        assertTrue(validProfileName(" João Silva "))
        for (name in listOf("<b>Ana</b>", "Ana\nSilva", "Ana\tSilva", "a".repeat(51), "")) {
            assertFalse(validProfileName(name))
            assertFalse(canSaveProfileName(name, "Ana"))
        }
    }
}
