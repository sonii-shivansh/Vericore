package com.vericore.core.exceptions

sealed class VericoreException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class ConfigurationException(message: String, cause: Throwable? = null) : VericoreException(message, cause)

class AIProviderException(message: String, cause: Throwable? = null) : VericoreException(message, cause)

class AnalysisException(message: String, cause: Throwable? = null) : VericoreException(message, cause)

class ValidationException(message: String, cause: Throwable? = null) : VericoreException(message, cause)
