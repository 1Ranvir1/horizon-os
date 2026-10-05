package dev.gtos.domain

/** Transient navigation state only; persistent placements will use a separate model. */
data class HomeSession(val query: String = "", val homeGeneration: Int = 0) {
    fun search(value: String) = copy(query = value.take(256))
    fun home() = copy(query = "", homeGeneration = homeGeneration + 1)
    fun back() = copy(query = "", homeGeneration = homeGeneration + 1)
}
