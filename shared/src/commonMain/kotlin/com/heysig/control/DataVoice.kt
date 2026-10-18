package com.heysig.control

/** File selection and review remain Android UI actions; no aid playback. */
enum class DataVoice {
    IMPORT_FITTING, IMPORT_PHRASES, EXPORT_PHRASES;
    companion object {
        fun parse(raw: String): DataVoice? = when(FittingWorkflow.normalize(raw)) {
            "import aid setup", "import fitting" -> IMPORT_FITTING
            "import personal phrases", "import phrases" -> IMPORT_PHRASES
            "export personal phrases", "export phrases" -> EXPORT_PHRASES
            else -> null
        }
    }
}
