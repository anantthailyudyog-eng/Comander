package com.example

import com.example.data.HudMode
import com.example.data.TaskPriority
import com.example.data.WriterDocType
import org.junit.Assert.assertEquals
import org.junit.Test

class JarvisLogicUnitTest {

    @Test
    fun `HudMode maps exact names, display labels, and voice synonyms`() {
        assertEquals(HudMode.STANDARD, HudMode.fromNameOrLabel("Standard Command"))
        assertEquals(HudMode.CODING, HudMode.fromNameOrLabel("Coding/Dev Mode"))
        assertEquals(HudMode.CODING, HudMode.fromNameOrLabel("switch to dev"))
        assertEquals(HudMode.FOCUS, HudMode.fromNameOrLabel("Focus/Stealth Mode"))
        assertEquals(HudMode.FOCUS, HudMode.fromNameOrLabel("stealth"))
        assertEquals(HudMode.DIAGNOSTIC, HudMode.fromNameOrLabel("Diagnostic Mode"))
        assertEquals(HudMode.DIAGNOSTIC, HudMode.fromNameOrLabel("run diag"))
        assertEquals(HudMode.STANDARD, HudMode.fromNameOrLabel("unknown_mode"))
    }

    @Test
    fun `TaskPriority and WriterDocType safely fall back on unexpected inputs`() {
        assertEquals(TaskPriority.CRITICAL, TaskPriority.safeFrom("critical"))
        assertEquals(TaskPriority.HIGH, TaskPriority.safeFrom("HIGH"))
        assertEquals(TaskPriority.MEDIUM, TaskPriority.safeFrom("invalid"))

        assertEquals(WriterDocType.CODE, WriterDocType.safeFrom("code"))
        assertEquals(WriterDocType.EMAIL, WriterDocType.safeFrom("EMAIL"))
        assertEquals(WriterDocType.NOTE, WriterDocType.safeFrom("unknown"))
    }
}
