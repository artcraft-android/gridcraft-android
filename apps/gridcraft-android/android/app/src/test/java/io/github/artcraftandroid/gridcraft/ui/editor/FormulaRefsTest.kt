package io.github.artcraftandroid.gridcraft.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormulaRefsTest {
    @Test
    fun finds_cells_and_blocks() {
        val refs = formulaRefs("=SUM(A1:B3)+\$C\$4")
        assertEquals(2, refs.size)
        val (a, c) = refs
        assertEquals(listOf(0, 0, 2, 1), listOf(a.firstRow, a.firstCol, a.lastRow, a.lastCol))
        assertEquals("A1:B3", "=SUM(A1:B3)+\$C\$4".substring(a.start, a.end))
        assertEquals(listOf(3, 2, 3, 2), listOf(c.firstRow, c.firstCol, c.lastRow, c.lastCol))
        assertTrue(a.color != c.color)
    }

    @Test
    fun one_reference_keeps_one_colour() {
        val refs = formulaRefs("=A1+B2+\$A\$1")
        assertEquals(3, refs.size)
        assertEquals(refs[0].color, refs[2].color)
    }

    @Test
    fun skips_strings_functions_names_and_other_sheets() {
        assertTrue(formulaRefs("=\"A1\"&LOG10(2)").isEmpty())
        assertTrue(formulaRefs("=Sheet2!A1+Rate").isEmpty())
        assertTrue(formulaRefs("A1").isEmpty())
        assertTrue(formulaRefs("=XFE1+A0").isEmpty())
    }

    @Test
    fun a_block_given_backwards_is_ordered() {
        val r = formulaRefs("=C5:A1").single()
        assertEquals(listOf(0, 0, 4, 2), listOf(r.firstRow, r.firstCol, r.lastRow, r.lastCol))
    }

    @Test
    fun the_call_at_the_cursor() {
        assertEquals("SUM" to 0, callAt("=SUM(", 5))
        assertEquals("SUM" to 1, callAt("=SUM(A1,", 8))
        assertEquals("IF" to 2, callAt("=SUM(IF(A1>2,1,", 15))
        assertEquals("SUM" to 1, callAt("=SUM(IF(A1>2,1,0),", 18))
        assertEquals("CONCAT" to 0, callAt("=CONCAT(\"a,b(\"", 14))
        assertEquals(null, callAt("=SUM(A1)", 8))
        assertEquals("ROUND" to 1, callAt("=ROUND((A1+B1),", 15))
        assertEquals(null, callAt("SUM(", 4))
    }

    @Test
    fun signature_arguments() {
        val args = signatureArgs("SUM(number1, [number2], ...)")
        assertEquals(listOf("number1", "[number2]", "..."), args)
        assertEquals(0, argumentShown(args, 0))
        assertEquals(1, argumentShown(args, 1))
        assertEquals(1, argumentShown(args, 5))
        assertEquals(-1, argumentShown(emptyList(), 0))
    }
}
