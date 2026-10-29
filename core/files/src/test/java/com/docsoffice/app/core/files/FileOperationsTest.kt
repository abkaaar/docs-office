package com.docsoffice.app.core.files

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileOperationsTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun copyVerifyThenMove() {
        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns temp.newFolder("files")
        every { context.packageName } returns "com.docsoffice.app.debug"
        val ops = FileOperations(context)
        val source = temp.newFile("a.txt")
        source.writeText("hello")
        val dest = File(temp.newFolder("out"), "a.txt")
        assertTrue(ops.copyVerify(source, dest))
        assertEquals(5, dest.length())
        val moved = File(temp.newFolder("moved"), "a.txt")
        assertTrue(ops.moveCopyVerifyDelete(source, moved))
        assertTrue(!source.exists())
        assertTrue(moved.exists())
    }

    @Test
    fun renameCreatesNewFile() {
        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns temp.newFolder("files2")
        every { context.packageName } returns "com.docsoffice.app.debug"
        val ops = FileOperations(context)
        val source = temp.newFile("old.txt")
        source.writeText("x")
        val renamed = ops.rename(source, "new.txt")
        assertTrue(renamed != null)
        assertEquals("new.txt", renamed!!.name)
        assertTrue(renamed.exists())
    }
}
