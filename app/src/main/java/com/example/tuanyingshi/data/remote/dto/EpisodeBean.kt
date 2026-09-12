package com.example.tuanyingshi.data.remote.dto

import com.example.tuanyingshi.domain.model.Episode

data class EpisodeBean(
    val name: String,
    val url: String
) {
    fun toEpisode(): Episode {
        return Episode(name = name, url = url)
    }
}
