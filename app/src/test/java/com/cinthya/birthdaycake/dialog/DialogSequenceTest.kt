package com.cinthya.birthdaycake.dialog

import com.cinthya.birthdaycake.R
import com.cinthya.birthdaycake.model.CharacterExpressions
import com.cinthya.birthdaycake.model.dialog.ActiveDialog
import com.cinthya.birthdaycake.model.dialog.DialogSequence
import com.cinthya.birthdaycake.model.dialog.DialogSequenceId
import com.cinthya.birthdaycake.model.dialog.DialogStep
import com.cinthya.birthdaycake.model.dialog.DialogText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogSequenceTest {

    private fun step(@Suppress("SameParameterValue") res: Int) =
        DialogStep(CharacterExpressions.IDLE_SIT, desc = DialogText(res))

    private fun sequence(size: Int) = DialogSequence(
        DialogSequenceId.INTRO,
        List(size) { step(R.string.intro_hello) }
    )

    @Test
    fun `next walks the sequence and stops at the end`() {
        var cursor: ActiveDialog? = ActiveDialog(sequence(3))

        assertEquals(0, cursor?.index)
        cursor = cursor?.next()
        assertEquals(1, cursor?.index)
        cursor = cursor?.next()
        assertEquals(2, cursor?.index)

        // Past the last step there is nothing to show - the caller acts on the null.
        assertNull(cursor?.next())
    }

    @Test
    fun `isLast is only true on the final step`() {
        val cursor = ActiveDialog(sequence(2))
        assertFalse(cursor.isLast)
        assertTrue(cursor.next()!!.isLast)
    }

    @Test
    fun `a single step sequence is immediately the last one`() {
        val cursor = ActiveDialog(sequence(1))
        assertTrue(cursor.isLast)
        assertNull(cursor.next())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an empty sequence is rejected at construction`() {
        DialogSequence(DialogSequenceId.INTRO, emptyList())
    }

    @Test
    fun `a step with no button advances on tap`() {
        assertTrue(step(R.string.intro_hello).advancesOnTap)
    }
}
