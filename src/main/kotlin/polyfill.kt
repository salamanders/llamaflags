package info.benjaminhill.llamaflags

/**
 * Creates a Cartesian product of the given lists of parameters.
 * This is an equivalent to Python's itertools.product.
 */
fun <T> product(map: Map<String, List<T>>): List<Map<String, T>> {
    if (map.isEmpty()) return listOf(emptyMap())

    val result = mutableListOf<Map<String, T>>()
    val keys = map.keys.toList()

    fun generateCombinations(index: Int, current: Map<String, T>) {
        if (index == keys.size) {
            result.add(current)
            return
        }
        val key = keys[index]
        val values = map[key]!!
        for (value in values) {
            generateCombinations(index + 1, current + (key to value))
        }
    }

    generateCombinations(0, emptyMap())
    return result
}