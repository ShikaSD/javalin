/*
 * Javalin - https://javalin.io
 * Copyright 2017 David Åse
 * Licensed under Apache 2.0: https://github.com/tipsy/javalin/blob/master/LICENSE
 */

package io.javalin.core

import io.javalin.Handler
import io.javalin.core.util.ContextUtil.urlDecode
import org.slf4j.LoggerFactory
import java.util.*

data class HandlerEntry(val type: HandlerType, val path: String, val handler: Handler, val rawHandler: Handler) {
    private val pathParser = PathParser(path)
    fun matches(requestUri: String) = pathParser.matches(requestUri)
    fun extractPathParams(requestUri: String) = pathParser.extractPathParams(requestUri)
    fun extractSplats(requestUri: String) = pathParser.extractSplats(requestUri)
}

class PathParser(
        private val path: String,
        private val pathParamNames: List<String> = path.split("/")
                .filter { it.startsWith(":") }
                .map { it.replace(":", "") },
        private val matchRegex: Regex = pathParamNames
                .fold(path) { p, name -> p.replace(":$name", "[^/]+?") } // Replace path param names with wildcards (accepting everything except slash)
                .replace("//", "/") // Replace double slash occurrences
                .replace("/*/", "/.*?/") // Replace splat between slashes to a wildcard
                .replace("^\\*".toRegex(), ".*?") // Replace splat in the beginning of path to a wildcard (allow paths like (*/path/)
                .replace("/*", "/.*?") // Replace splat in the end of string to a wildcard
                .replace("/$".toRegex(), "/?") // Replace trailing slash to optional one
                .run { if (!endsWith("/?")) this + "/?" else this } // Add slash if doesn't have one
                .run { "^" + this + "$" } // Let the matcher know that it is the whole path
                .toRegex(),
        private val splatRegex: Regex = matchRegex.pattern.replace(".*?", "(.*?)").toRegex(),
        private val pathParamRegex: Regex = matchRegex.pattern.replace("[^/]+?", "([^/]+?)").toRegex()) {

    fun matches(url: String) = matchV2(path.removeSuffix("/"), url.removeSuffix("/"))

    fun extractPathParams(url: String) = pathParamNames.zip(values(pathParamRegex, url)) { name, value ->
        name.toLowerCase() to urlDecode(value)
    }.toMap()

    fun extractSplats(url: String) = values(splatRegex, url).map { urlDecode(it) }

    // Match and group values, then drop first element (the input string)
    private fun values(regex: Regex, url: String) = regex.matchEntire(url)?.groupValues?.drop(1) ?: emptyList()

    fun matchV2(route: String, path: String): Boolean {

        var pathIndex = 0
        var routeIndex = 0

        while (pathIndex < path.length && routeIndex < route.length) {

            // Get current symbol
            val pathSymbol = path[pathIndex]
            val routeSymbol = route[routeIndex]

            when {
                routeSymbol == ':' -> {
                    // Path parameter, skip until next separator
                    while (pathIndex.lengthGuard(path) && path[pathIndex] != '/') pathIndex++
                    while (routeIndex.lengthGuard(route) && route[routeIndex] != '/') routeIndex++
                }
                routeSymbol == '*' -> {
                    // Matches if splat at the end
                    if (routeIndex == route.lastIndex) return true

                    // Or try to match substring until we reach the end or the next splat
                    var splatIndex = routeIndex

                    while (pathIndex < path.length) {

                        // Go to latest splat and try to match path again
                        routeIndex = splatIndex + 1

                        // First try to find a common place
                        while (pathIndex.lengthGuard(path) && path[pathIndex] != route[routeIndex]) pathIndex++

                        // If path ended without one, it does not match
                        if (pathIndex == path.length) return false

                        // Check common substring until we reach the end or find the next splat
                        while (pathIndex.lengthGuard(path)
                            && routeIndex.lengthGuard(route)
                            && path[pathIndex] == route[routeIndex]
                            && route[routeIndex] != '*'
                        ) {
                            pathIndex++
                            routeIndex++
                        }

                        // Reached the end of both strings, they should match
                        if (pathIndex == path.length && routeIndex == route.length) return true

                        // However if only route is finished, they do not
                        if (routeIndex == route.length) return false

                        // If reached the splat, update splat index
                        if (route[routeIndex] == '*') {
                            // Matches if splat at the end
                            if (routeIndex == route.lastIndex) return true

                            splatIndex = routeIndex
                        }
                    }
                }
                routeSymbol != pathSymbol -> return false
            }

            pathIndex++
            routeIndex++
        }

        return pathIndex >= path.length && routeIndex >= route.length
    }

    inline fun Int.lengthGuard(string: String) = this < string.length
}

class PathMatcher(var ignoreTrailingSlashes: Boolean = true) {

    private val log = LoggerFactory.getLogger(PathMatcher::class.java)

    val handlerEntries = HandlerType.values().associateTo(EnumMap<HandlerType, ArrayList<HandlerEntry>>(HandlerType::class.java)) {
        it to arrayListOf()
    }

    fun findEntries(requestType: HandlerType, requestUri: String) =
            handlerEntries[requestType]!!.filter { he -> match(he, requestUri) }

    private fun match(entry: HandlerEntry, requestPath: String): Boolean = when {
        entry.path == "*" -> true
        entry.path == requestPath -> true
        !this.ignoreTrailingSlashes && slashMismatch(entry.path, requestPath) -> false
        else -> entry.matches(requestPath)
    }

    private fun slashMismatch(s1: String, s2: String) = (s1.endsWith('/') || s2.endsWith('/')) && (s1.last() != s2.last())

    fun findHandlerPath(predicate: (HandlerEntry) -> Boolean): String? {
        val entries = handlerEntries.values.flatten().filter(predicate)
        if (entries.size > 1) {
            log.warn("More than one path found for handler, returning first match: '{} {}'", entries[0].type, entries[0].path)
        }
        return if (entries.isNotEmpty()) entries[0].path else null
    }

}
