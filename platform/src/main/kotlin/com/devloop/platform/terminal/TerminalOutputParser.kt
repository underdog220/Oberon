package com.devloop.platform.terminal

/**
 * Parser fuer Terminal-Output der ANSI-Escape-Sequenzen entfernt
 * und den sichtbaren Text extrahiert.
 *
 * Wird vom PTY-Supervisor verwendet um den Terminal-Inhalt zu analysieren
 * und auf Prompts, Fragen oder Zustandsaenderungen zu reagieren.
 */
object TerminalOutputParser {

    // ANSI Escape-Sequenzen: ESC[ ... m (Farben), ESC[ ... H (Cursor), etc.
    private val ANSI_ESCAPE = Regex("\u001B\\[[0-9;]*[a-zA-Z]")
    // Carriage Return ohne Newline (Zeile ueberschreiben)
    private val CR_OVERWRITE = Regex("\r(?!\n)")
    // Steuerzeichen (ausser \n und \t)
    private val CONTROL_CHARS = Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]")

    /**
     * Entfernt ANSI-Escape-Sequenzen und gibt sauberen Text zurueck.
     */
    fun stripAnsi(raw: String): String {
        return ANSI_ESCAPE.replace(raw, "")
            .let { CR_OVERWRITE.replace(it, "") }
            .let { CONTROL_CHARS.replace(it, "") }
    }

    /**
     * Analysiert den Terminal-Output und erkennt bekannte Zustaende.
     */
    fun detectState(rawOutput: String): TerminalState {
        val clean = stripAnsi(rawOutput)
        val lastLines = clean.lines().takeLast(20).joinToString("\n")

        return when {
            // Claude Code Bypass-Permissions
            lastLines.contains("Yes, I accept") && lastLines.contains("Bypass Permissions") ->
                TerminalState.BYPASS_PERMISSIONS_PROMPT

            // Claude Code wartet auf Input (Prompt-Zeile)
            lastLines.trimEnd().endsWith(">") && lastLines.contains("claude") ->
                TerminalState.CLAUDE_READY

            // Claude Code arbeitet (Spinner/Progress)
            lastLines.contains("Thinking") || lastLines.contains("Working") ->
                TerminalState.CLAUDE_WORKING

            // Ja/Nein-Frage
            lastLines.contains("[y/n]") || lastLines.contains("(y/n)") ||
            lastLines.contains("[yes/no]") ->
                TerminalState.YES_NO_QUESTION

            // Permission-Frage (allow/deny)
            lastLines.contains("Allow") && (lastLines.contains("once") || lastLines.contains("always")) ->
                TerminalState.PERMISSION_QUESTION

            // Nummerierte Auswahl
            lastLines.contains("Enter to confirm") ->
                TerminalState.NUMBERED_CHOICE

            // Shell-Prompt (PowerShell oder cmd)
            lastLines.trimEnd().let { it.endsWith("> ") || it.endsWith("$ ") || it.matches(Regex(".*PS [A-Z]:\\\\.*>\\s*$")) } ->
                TerminalState.SHELL_READY

            // Agent fertig (Exit oder Zusammenfassung)
            lastLines.contains("Agent finished") || lastLines.contains("completed") ->
                TerminalState.AGENT_COMPLETED

            else -> TerminalState.UNKNOWN
        }
    }

    /**
     * Extrahiert die letzte Frage oder Prompt-Zeile aus dem Terminal.
     */
    fun extractLastPrompt(rawOutput: String): String? {
        val clean = stripAnsi(rawOutput)
        val lines = clean.lines().filter { it.isNotBlank() }
        return lines.lastOrNull()?.trim()
    }
}

/**
 * Erkannter Zustand des Terminal-Inhalts.
 */
enum class TerminalState {
    BYPASS_PERMISSIONS_PROMPT,
    CLAUDE_READY,
    CLAUDE_WORKING,
    YES_NO_QUESTION,
    PERMISSION_QUESTION,
    NUMBERED_CHOICE,
    SHELL_READY,
    AGENT_COMPLETED,
    UNKNOWN,
}
