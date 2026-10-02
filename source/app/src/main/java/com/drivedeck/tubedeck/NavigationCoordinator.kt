package com.drivedeck.tubedeck

/**
 * Owns logical MyTube navigation history independently from WebView's internal history.
 *
 * Video /watch URLs handled by the native player are deliberately not inserted here. This keeps
 * Android Back deterministic and prevents Browser -> Player -> Browser -> same Player loops.
 */
internal data class NavigationEntry(
    val screen: ActiveScreen,
    val browserUrl: String? = null
)

internal class NavigationCoordinator(initial: NavigationEntry) {
    private val backStack = ArrayDeque<NavigationEntry>()
    private val forwardStack = ArrayDeque<NavigationEntry>()

    var current: NavigationEntry = initial
        private set

    fun navigate(entry: NavigationEntry): NavigationEntry {
        if (entry == current) return current
        backStack.addLast(current)
        current = entry
        forwardStack.clear()
        trim(backStack)
        return current
    }

    /**
     * Updates the current route without creating a new Back stop.
     * Used for passive browser URL synchronization after a page commits.
     */
    fun replace(entry: NavigationEntry): NavigationEntry {
        current = entry
        return current
    }

    fun back(): NavigationEntry? {
        val previous = backStack.removeLastOrNull() ?: return null
        forwardStack.addLast(current)
        current = previous
        trim(forwardStack)
        return current
    }

    fun forward(): NavigationEntry? {
        val next = forwardStack.removeLastOrNull() ?: return null
        backStack.addLast(current)
        current = next
        trim(backStack)
        return current
    }

    fun canGoBack(): Boolean = backStack.isNotEmpty()
    fun canGoForward(): Boolean = forwardStack.isNotEmpty()

    fun reset(entry: NavigationEntry) {
        backStack.clear()
        forwardStack.clear()
        current = entry
    }

    private fun trim(stack: ArrayDeque<NavigationEntry>) {
        while (stack.size > MAX_HISTORY) stack.removeFirst()
    }

    private companion object {
        const val MAX_HISTORY = 80
    }
}
