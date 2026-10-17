package com.vericore.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ServerCommandTest {
    @Test
    fun `loopback hosts do not need an unsafe remote bind opt in`() {
        assertTrue(validateServerBindHost("127.0.0.1", allowRemoteHost = false))
        assertTrue(validateServerBindHost("127.0.0.2", allowRemoteHost = false))
        assertTrue(validateServerBindHost("localhost", allowRemoteHost = false))
        assertTrue(validateServerBindHost("::1", allowRemoteHost = false))
        assertTrue(validateServerBindHost("[::1]", allowRemoteHost = false))
    }

    @Test
    fun `non-loopback bind is refused unless explicitly opted in`() {
        assertFailsWith<IllegalArgumentException> {
            validateServerBindHost("0.0.0.0", allowRemoteHost = false)
        }
        assertFailsWith<IllegalArgumentException> {
            validateServerBindHost("192.168.1.12", allowRemoteHost = false)
        }

        assertEquals(false, validateServerBindHost("0.0.0.0", allowRemoteHost = true))
    }

    @Test
    fun `blank and padded hosts are rejected even with remote opt in`() {
        assertFailsWith<IllegalArgumentException> {
            validateServerBindHost("", allowRemoteHost = true)
        }
        assertFailsWith<IllegalArgumentException> {
            validateServerBindHost(" 127.0.0.1 ", allowRemoteHost = true)
        }
    }
}
