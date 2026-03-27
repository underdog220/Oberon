package com.devloop.core.domain.experiment

enum class ExperimentStatus {
    DRAFT,
    PLANNED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}

enum class RunExecutionStatus {
    PENDING,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    TIMEOUT,
    CANCELLED,
}

enum class TranscriptionEngine {
    WHISPER_LOCAL,
    WHISPER_CLOUD,
    AZURE_SPEECH,
    GOOGLE_SPEECH,
    DEEPGRAM,
    CUSTOM,
}

enum class AudioScenarioType {
    CLEAN_SPEECH,
    NOISY_ENVIRONMENT,
    MULTI_SPEAKER,
    TECHNICAL_JARGON,
    NUMBERS_HEAVY,
    MIXED_LANGUAGE,
    LOW_QUALITY_MIC,
}
