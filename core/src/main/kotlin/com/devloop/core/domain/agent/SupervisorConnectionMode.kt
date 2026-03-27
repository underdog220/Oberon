package com.devloop.core.domain.agent

/**
 * Betriebsmodus der Verbindung zwischen Supervisor und Coding Agent.
 */
enum class SupervisorConnectionMode {
    /** HTTP-API-Aufruf (z. B. OpenAI Coding Agent). Kein lokaler Prozess. */
    API_ONESHOT,

    /** CLI-Aufruf: Prompt als Argument/stdin, Agent laeuft einmal und liefert Ergebnis. */
    CLI_ONESHOT,

    /** Persistente Shell-Session: Agent laeuft interaktiv, Supervisor ueberwacht stdout und antwortet via stdin. */
    SHELL_INTERACTIVE,
}
