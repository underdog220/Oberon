package com.devloop.core.domain.agent

/**
 * Wie der Desktop Codex CLI zum Coding-Agent-Lauf einbindet.
 *
 * Hinweis: Der eigentliche Prozess läuft aktuell immer mit erfasstem stdout/stderr (Supervisor, UI, Trace).
 * Der Modus [VISIBLE_COMPANION_TERMINAL] öffnet **zusätzlich** ein lesbares Terminal im Repo — nicht ersetzend.
 */
enum class CodexExecutionMode {
    /** Nur Hintergrundprozess (Standard), volle Ausgabe-Erfassung. */
    BACKGROUND_CAPTURE,

    /**
     * Zusätzlich ein abgekoppeltes Terminal-Fenster im CLI-Arbeitsverzeichnis (z. B. Windows Terminal).
     * Der Codex-Lauf für den Supervisor erfolgt weiterhin im Hintergrund; das Fenster dient der sichtbaren
     * Mitverfolgung / manuellen Nacharbeit — kein zweiter Codex-Prozess.
     */
    VISIBLE_COMPANION_TERMINAL,
}
