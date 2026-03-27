package com.devloop.core.domain.enums

enum class MemoryKind {
    /** Stabiles Langzeitwissen: Architektur, Regeln, Ziele, Konventionen. */
    STABLE_KNOWLEDGE,
    /** Dynamischer Resume-/Startup-Kontext: letzter Stand, offene Punkte. */
    RESUME_CONTEXT,
    /** Session-Notiz: kurzlebige Beobachtungen innerhalb einer Session. */
    SESSION_NOTE,
    /** Entscheidungsprotokoll: architektonische/strategische Entscheidungen. */
    DECISION_LOG,
}
