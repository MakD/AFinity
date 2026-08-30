package com.makd.afinity.util.logging

data class LogSignature(val chunks: List<String>, val values: List<String>)

sealed interface LabelPart {
    data class Literal(val text: String) : LabelPart

    data object Variable : LabelPart
}

object LogSignatures {

    private val variablePattern =
        Regex(
            "https?://\\S+" +
                "|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}" +
                "|\\b[0-9a-fA-F]{8,}\\b" +
                "|\"[^\"]*\"" +
                "|\\b\\d+(?:\\.\\d+)?\\b"
        )

    fun of(message: String): LogSignature {
        val chunks = mutableListOf<String>()
        val values = mutableListOf<String>()
        var cursor = 0

        variablePattern.findAll(message).forEach { match ->
            chunks.add(message.substring(cursor, match.range.first))
            values.add(match.value)
            cursor = match.range.last + 1
        }
        chunks.add(message.substring(cursor))

        return LogSignature(chunks, values)
    }

    fun label(signatures: List<LogSignature>): List<LabelPart> {
        if (signatures.isEmpty()) return emptyList()

        val first = signatures.first()
        val parts = mutableListOf<LabelPart>()
        val literal = StringBuilder()

        first.chunks.forEachIndexed { index, chunk ->
            literal.append(chunk)

            if (index >= first.values.size) return@forEachIndexed

            val value = first.values[index]
            val stable = signatures.all { it.values.getOrNull(index) == value }

            if (stable) {
                literal.append(value)
            } else {
                if (literal.isNotEmpty()) {
                    parts.add(LabelPart.Literal(literal.toString()))
                    literal.clear()
                }
                parts.add(LabelPart.Variable)
            }
        }

        if (literal.isNotEmpty()) parts.add(LabelPart.Literal(literal.toString()))
        return parts
    }

    fun plainLabel(signatures: List<LogSignature>): String =
        label(signatures).joinToString("") { part ->
            when (part) {
                is LabelPart.Literal -> part.text
                LabelPart.Variable -> "n"
            }
        }
}
