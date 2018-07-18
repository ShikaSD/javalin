package io.javalin.performance

import com.carrotsearch.junitbenchmarks.BenchmarkOptions
import com.carrotsearch.junitbenchmarks.BenchmarkRule
import com.carrotsearch.junitbenchmarks.Clock
import io.javalin.Handler
import io.javalin.core.HandlerEntry
import io.javalin.core.HandlerType
import io.javalin.core.util.ContextUtil.urlDecode
import io.javalin.core.util.Util
import jdk.nashorn.internal.objects.NativeArray.forEach
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

typealias NewHandlerEntry = HandlerEntry

@BenchmarkOptions(callgc = false, benchmarkRounds = 100000, warmupRounds = 500, concurrency = 4, clock = Clock.NANO_TIME)
class RouteMatcherPerformanceTest {

    @get:Rule
    val benchmarkRun = BenchmarkRule()

    data class OldHandlerEntry(val type: HandlerType, val path: String, val handler: Handler)

    fun oldMatch(handlerPath: String, fullRequestPath: String): Boolean {
        val hpp = Util.pathToList(handlerPath) // handler-path-parts
        val rpp = Util.pathToList(fullRequestPath) // request-path-parts

        fun isLastAndSplat(i: Int) = i == hpp.lastIndex && hpp[i] == "*"
        fun isNotPathOrSplat(i: Int) = hpp[i].first() != ':' && hpp[i] != "*"

        if (hpp.size == rpp.size) {
            for (i in hpp.indices) {
                when {
                    isLastAndSplat(i) && handlerPath.endsWith('*') -> return true
                    isNotPathOrSplat(i) && hpp[i] != rpp[i] -> return false
                }
            }
            return true
        }
        if (hpp.size < rpp.size && handlerPath.endsWith('*')) {
            for (i in hpp.indices) {
                when {
                    isLastAndSplat(i) -> return true
                    isNotPathOrSplat(i) && hpp[i] != rpp[i] -> return false
                }
            }
            return false
        }
        return false
    }

    fun oldSplat(request: List<String>, matched: List<String>): List<String> {
        val numRequestParts = request.size
        val numHandlerParts = matched.size
        val splat = ArrayList<String>()
        var i = 0
        while (i < numRequestParts && i < numHandlerParts) {
            val matchedPart = matched[i]
            if (matchedPart == "*") {
                val splatParam = StringBuilder(request[i])
                if (numRequestParts != numHandlerParts && i == numHandlerParts - 1) {
                    for (j in i + 1..numRequestParts - 1) {
                        splatParam.append("/")
                        splatParam.append(request[j])
                    }
                }
                splat.add(urlDecode(splatParam.toString()))
            }
            i++
        }
        return splat
    }

    fun oldParams(requestPaths: List<String>, handlerPaths: List<String>): Map<String, String> {
        val params = HashMap<String, String>()
        var i = 0
        while (i < requestPaths.size && i < handlerPaths.size) {
            val matchedPart = handlerPaths[i]
            if (matchedPart.startsWith(":")) {
                params[matchedPart.toLowerCase()] = urlDecode(requestPaths[i])
            }
            i++
        }
        return params
    }

    fun newMatch(entry: NewHandlerEntry, path: String) = entry.matches(path)

    fun newSplat(entry: NewHandlerEntry, path: String) = entry.extractSplats(path)
    fun newParams(entry: NewHandlerEntry, path: String) = entry.extractPathParams(path)

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
                    // Splat, return true if the last element
                    if (routeIndex == route.lastIndex) return true

                    // Or try to match substring until we reach the end or the next splat
                    var splatIndex = routeIndex

                    while (pathIndex < path.length) {
                        // First try to find a common place
                        while (pathIndex.lengthGuard(path) && path[pathIndex] != route[routeIndex]) pathIndex++

                        // If path ended without one, it does not match
                        if (pathIndex == path.length) return false

                        // Check common area until we reach the end or find the next splat
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
                            // Check for the last element
                            if (routeIndex == route.lastIndex) return true

                            splatIndex = routeIndex
                        }

                        // Go to latest splat and try to match path again
                        routeIndex = splatIndex + 1
                    }
                }
                routeSymbol != pathSymbol -> return false
            }

            pathIndex++
            routeIndex++
        }

        return pathIndex == path.length && routeIndex == route.length
    }

    inline fun Int.lengthGuard(string: String) = this < string.length

    companion object {
        val routes = listOf(
                "/test/:user/some/path/here",
                "/test/*/some/more/path/here",
                "/test/path/route/without/splats",
                "/test/has/splat/at/the/end/*",
                "/test/:id/simple/route/:user/create/",
                "/matches/all/*/user",
                "/matches/all/*/user/*/more",
                "/test/*",
                "/hello"
        )

        val oldEntries = routes.map { OldHandlerEntry(HandlerType.AFTER, it, Handler { }) }
        val newEntries = routes.map { NewHandlerEntry(HandlerType.AFTER, it, Handler { }, Handler { }) }

        val testEntries = listOf(
                "/test/1234/some/path/here",
                "/test/1234/some/here/path",
                "/test/3322/some/more/path/here",
                "/test/more/path/some/more/path/here",
                "/test/path/route/without/splats",
                "/test/path/route/without/solats",
                "/test/has/splat/at/the/end/for/everything",
                "/test/has/splat/at/the/and/something/else",
                "/test/1/simple/route/John/create/",
                "/test/2/simple/route/Lisa/create/",
                "/test/3/simple/route/Temp/create/",
                "/matches/all/that/ends/with/user",
                "/matches/all/that/ends/with/user/and/even/more",
                "/matches/all/that/user"
        )
    }

    @Ignore("Manual execution")
    @Test
    fun testOldMatchingPerformance() {
        testEntries.forEach { requestUri ->
            oldEntries.forEach { entry ->
                oldMatch(entry.path, requestUri)
            }
        }
    }

    @Ignore("Manual execution")
    @Test
    fun testNewMatchingPerformance() {
        testEntries.forEach { requestUri ->
            newEntries.forEach { entry ->
                newMatch(entry, requestUri)
            }
        }
    }

    @Ignore("Manual execution")
    @Test
    fun testV2MatchingPerfomance() {
        testEntries.forEach { requestUri ->
            routes.forEach { entry ->
                matchV2(entry, requestUri)
            }
        }
    }

    @Ignore("Manual execution")
    @Test
    fun verifyV2MatchingPerfomance() {
        testEntries.forEach { requestUri ->
            routes.forEachIndexed { i, entry ->
                assertEquals(
                    "$requestUri matches $entry",
                    newEntries[i].matches(requestUri),
                    matchV2(entry, requestUri)
                )
            }
        }
    }


    @Ignore("Manual execution")
    @Test
    fun testOldParamAndSplatPerformance() {
        testEntries.forEach { requestUri ->
            oldEntries.forEach { entry ->
                val requestList = Util.pathToList(requestUri)
                val entryList = Util.pathToList(entry.path)
                oldParams(requestList, entryList)
                oldSplat(requestList, entryList)
            }
        }
    }

    @Ignore("Manual execution")
    @Test
    fun testNewParamAndSplatPerformance() {
        testEntries.forEach { requestUri ->
            newEntries.forEach { entry ->
                newParams(entry, requestUri)
                newSplat(entry, requestUri)
            }
        }
    }
}
