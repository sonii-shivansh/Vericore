package com.vericore.core.parser

import java.io.File
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object FileAsStringSerializer : KSerializer<File> {
    override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("File", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: File) = encoder.encodeString(value.absolutePath)
    override fun deserialize(decoder: Decoder): File = File(decoder.decodeString())
}

@Serializable
data class GitMetadata(
        val lastModified: Long = 0,
        val changeFrequency: Int = 0,
        val topAuthors: List<String> = emptyList(),
        val recentMessages: List<String> = emptyList()
)

@Serializable
data class ParsedFile(
        @Serializable(with = FileAsStringSerializer::class)
        val file: File,
        val packageName: String,
        val imports: List<String>,
        val referencedTypes: List<String> = emptyList(),
        var gitMetadata: GitMetadata = GitMetadata(),
        val description: String = "",
        val parseWarning: String? = null
)

interface LanguageParser {
    fun parse(file: File): ParsedFile
}
