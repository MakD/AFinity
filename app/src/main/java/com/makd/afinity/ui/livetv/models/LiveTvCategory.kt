package com.makd.afinity.ui.livetv.models

import com.makd.afinity.data.models.livetv.AfinityProgram

enum class LiveTvCategory(
    val displayName: String,
    val matchingGenres: Set<String>
) {
    ON_NOW("On Now", emptySet()),
    MOVIES(
        "Movies",
        setOf(
            "Movie",
            "Film",
            "Drama",
            "Action",
            "Comedy",
            "Thriller",
            "Horror",
            "Romance",
            "Sci-Fi",
            "Fantasy"
        )
    ),
    SHOWS(
        "Shows",
        setOf(
            "Series",
            "TV Series",
            "Sitcom",
            "Reality",
            "Talk Show",
            "Game Show",
            "Variety"
        )
    ),
    SPORTS(
        "Sports",
        setOf(
            "Sports",
            "Football",
            "Basketball",
            "Soccer",
            "Baseball",
            "Hockey",
            "Tennis",
            "Golf",
            "Racing"
        )
    ),
    KIDS(
        "For Kids", setOf(
            "Kids",
            "Children",
            "Animation",
            "Cartoon",
            "Family",
            "Animated"
        )
    ),
    NEWS(
        "News", setOf(
            "News",
            "Documentary",
            "Current Affairs",
            "Politics",
            "Weather"
        )
    );

    companion object {
        fun categorizeProgram(program: AfinityProgram): List<LiveTvCategory> {
            val categories = mutableListOf<LiveTvCategory>()

            if (program.isCurrentlyAiring()) {
                categories.add(ON_NOW)
            }

            val programGenres = program.genres.map { it.lowercase() }.toSet()

            entries.filter { it != ON_NOW }.forEach { category ->
                val categoryGenresLower = category.matchingGenres.map { it.lowercase() }.toSet()
                if (programGenres.any { genre ->
                        categoryGenresLower.any { categoryGenre ->
                            genre.contains(categoryGenre) || categoryGenre.contains(genre)
                        }
                    }) {
                    categories.add(category)
                }
            }

            return categories
        }
    }
}