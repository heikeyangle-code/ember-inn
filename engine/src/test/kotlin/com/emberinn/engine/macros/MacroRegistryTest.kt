package com.emberinn.engine.macros

import org.junit.Assert.assertEquals
import org.junit.Test

class MacroRegistryTest {

    private val env = MacroEnv(user = "User", char = "Character")

    @Test
    fun `custom macro resolves then unregisters`() {
        MacroRegistry.register("double") { args, _ -> (args.toIntOrNull()?.times(2))?.toString() }
        assertEquals("42", MacroEngine.substitute("{{double::21}}", env))
        MacroRegistry.unregister("double")
        assertEquals("{{double::21}}", MacroEngine.substitute("{{double::21}}", env))
    }

    @Test
    fun `custom macro can read env`() {
        MacroRegistry.register("whoami") { _, e -> e.user }
        assertEquals("User", MacroEngine.substitute("{{whoami}}", env))
        MacroRegistry.unregister("whoami")
    }
}
