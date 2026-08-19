package com.makd.afinity.data.network

import com.makd.afinity.data.models.wikidata.WikidataSubjectType

object WikidataAwardQueries {

    private const val TMDB_MOVIE_ID = "P4947"
    private const val TMDB_SERIES_ID = "P4983"
    private const val TMDB_PERSON_ID = "P4985"
    private const val LABEL_LANGUAGES = "en,mul"

    const val RESULT_WON = "Won"
    const val RESULT_NOMINATED = "Nominated"

    fun build(subjectType: WikidataSubjectType, tmdbId: String): String =
        when (subjectType) {
            WikidataSubjectType.PERSON -> buildPersonQuery(tmdbId)
            WikidataSubjectType.MOVIE -> buildTitleQuery(TMDB_MOVIE_ID, tmdbId)
            WikidataSubjectType.TV -> buildTitleQuery(TMDB_SERIES_ID, tmdbId)
        }

    private fun buildTitleQuery(idProperty: String, tmdbId: String): String {
        val id = escape(tmdbId)
        return """
            SELECT DISTINCT ?award ?awardLabel ?result ?year ?personLabel WHERE {
              ?film wdt:$idProperty "$id" .
              {
                ?film p:P166 ?st1 . ?st1 ps:P166 ?award .
                OPTIONAL { ?st1 pq:P585 ?d1 . BIND(YEAR(?d1) AS ?year) }
                BIND("$RESULT_WON" AS ?result)
              }
              UNION
              {
                ?film p:P1411 ?st2 . ?st2 ps:P1411 ?award .
                OPTIONAL { ?st2 pq:P585 ?d2 . BIND(YEAR(?d2) AS ?year) }
                BIND("$RESULT_NOMINATED" AS ?result)
              }
              UNION
              {
                ?person p:P166 ?st3 . ?st3 ps:P166 ?award ; pq:P1686 ?film .
                OPTIONAL { ?st3 pq:P585 ?d3 . BIND(YEAR(?d3) AS ?year) }
                BIND("$RESULT_WON" AS ?result)
              }
              UNION
              {
                ?person p:P1411 ?st4 . ?st4 ps:P1411 ?award ; pq:P1686 ?film .
                OPTIONAL { ?st4 pq:P585 ?d4 . BIND(YEAR(?d4) AS ?year) }
                BIND("$RESULT_NOMINATED" AS ?result)
              }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "$LABEL_LANGUAGES". }
            }
        """
            .trimIndent()
    }

    private fun buildPersonQuery(tmdbId: String): String {
        val id = escape(tmdbId)
        return """
            SELECT DISTINCT ?award ?awardLabel ?result ?year ?workLabel WHERE {
              ?person wdt:$TMDB_PERSON_ID "$id" .
              {
                ?person p:P166 ?st1 . ?st1 ps:P166 ?award .
                OPTIONAL { ?st1 pq:P585 ?d1 . BIND(YEAR(?d1) AS ?year) }
                OPTIONAL { ?st1 pq:P1686 ?work . }
                BIND("$RESULT_WON" AS ?result)
              }
              UNION
              {
                ?person p:P1411 ?st2 . ?st2 ps:P1411 ?award .
                OPTIONAL { ?st2 pq:P585 ?d2 . BIND(YEAR(?d2) AS ?year) }
                OPTIONAL { ?st2 pq:P1686 ?work . }
                BIND("$RESULT_NOMINATED" AS ?result)
              }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "$LABEL_LANGUAGES". }
            }
        """
            .trimIndent()
    }

    fun isValidTmdbId(tmdbId: String?): Boolean =
        !tmdbId.isNullOrBlank() && tmdbId.all { it.isDigit() }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}