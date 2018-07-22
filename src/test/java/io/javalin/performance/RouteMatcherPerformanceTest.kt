package io.javalin.performance

import com.carrotsearch.junitbenchmarks.BenchmarkOptions
import com.carrotsearch.junitbenchmarks.BenchmarkRule
import com.carrotsearch.junitbenchmarks.Clock
import io.javalin.Handler
import io.javalin.core.HandlerEntry
import io.javalin.core.HandlerType
import io.javalin.core.util.ContextUtil.urlDecode
import io.javalin.core.util.Util
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.util.*

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

    class EntriesV2(private val values: CharArray, private val graph: Graph) {

        companion object {
            private const val TERMINAL_INDEX = -1
            private const val SEPARATOR_CHAR = '/'
            private const val PARAMETER_CHAR = ':'
        }

        class Builder {
            private val values = mutableListOf('.')
            private val graphBuilder = Graph.Builder()

            fun add(route: String) {
                parse(route)
            }

            private fun parse(route: String) {
                var routeIndex = 0
                var valueIndex = 0

                while (routeIndex < route.length) {

                    val routeChar = route[routeIndex]
                    val value = values[valueIndex]

                    if (routeChar == PARAMETER_CHAR && value == SEPARATOR_CHAR) {
                        // record current one and skip to the next separator
                        while (routeIndex + 1 < route.length && route[routeIndex + 1] != SEPARATOR_CHAR) {
                            routeIndex++
                        }
                    }

                    var nextEdge = graphBuilder.first(valueIndex)

                    while (nextEdge != TERMINAL_INDEX) {
                        val end = graphBuilder.end(nextEdge)
                        if (values[end] == routeChar) {
                            valueIndex = end
                            routeIndex++
                            break
                        }

                        nextEdge = graphBuilder.next(nextEdge)
                    }

                    if (nextEdge == TERMINAL_INDEX) {
                        break
                    }
                }

                //add remainings of route
                while (routeIndex < route.length) {
                    // TODO: Code duplication
                    val routeChar = route[routeIndex]
                    val value = values[valueIndex]

                    if (routeChar == PARAMETER_CHAR && value == SEPARATOR_CHAR) {
                        // record current one and skip to the next separator
                        while (routeIndex + 1 < route.length && route[routeIndex + 1] != SEPARATOR_CHAR) {
                            routeIndex++
                        }
                    }

                    values.add(routeChar)
                    val newLetterIndex = values.lastIndex
                    graphBuilder.add(valueIndex, newLetterIndex)
                    valueIndex = newLetterIndex
                    routeIndex++
                }
            }

            fun build() = EntriesV2(
                values.toCharArray(),
                graphBuilder.build()
            )
        }

        class Graph(
            private val first: IntArray,
            private val next: IntArray,
            private val end: IntArray
        ) {

            class Builder {

                private var nextEdge = 0

                private var first = LinkedList<Int>()
                private var next = LinkedList<Int>()
                private var end = LinkedList<Int>()

                fun add(from: Int, to: Int) {
                    next.setElement(nextEdge, first.getElement(from))
                    first.setElement(from, nextEdge)
                    end.setElement(nextEdge, to)

                    nextEdge++
                }

                fun first(index: Int): Int = first.getElement(index)

                fun next(index: Int): Int = next.getElement(index)

                fun end(index: Int): Int = end.getElement(index)

                private fun MutableList<Int>.setElement(index: Int, value: Int) {
                    while (size <= index) {
                        add(TERMINAL_INDEX)
                    }
                    set(index, value)
                }

                private fun MutableList<Int>.getElement(index: Int): Int {
                    if (index >= size) {
                        return TERMINAL_INDEX
                    }
                    return get(index)
                }

                fun build() =
                    Graph(
                        first.toIntArray(),
                        next.toIntArray(),
                        end.toIntArray()
                    )
            }

            fun first(index: Int) = first.getOrElse(index) { TERMINAL_INDEX }
            fun next(index: Int) = next[index]
            fun end(index: Int) = end[index]
        }

        fun edges(i: Int): List<Int> {
            val iterator: IntIterator = object : IntIterator() {
                var index = graph.first(i)

                override fun hasNext() = index != -1
                override fun nextInt() = graph.end(index).also { index = graph.next(index) }
            }
            val list = mutableListOf<Int>()
            iterator.forEach {
                list += it
            }
            return list
        }

        fun matches(url: String): Boolean {
            // Traverse and check
            // FIXME: Code duplication
            var urlIndex = 0
            var valueIndex = 0

            while (urlIndex < url.length) {

                var nextEdgeIndex = graph.first(valueIndex)

                while (nextEdgeIndex != -1) {
                    val end = graph.end(nextEdgeIndex)
                    val value = values[end]

                    if (value == url[urlIndex]) {
                        valueIndex = end
                        urlIndex++
                        break
                    } else if (value == PARAMETER_CHAR) {
                        // Skip to the next separator
                        while (urlIndex < url.length && url[urlIndex] != SEPARATOR_CHAR) {
                            urlIndex++
                        }
                        valueIndex = end
                        break
                    }

                    nextEdgeIndex = graph.next(nextEdgeIndex)
                }

                if (nextEdgeIndex == -1) {
                    break
                }
            }

            return graph.first(valueIndex) == TERMINAL_INDEX && urlIndex >= url.length // Graph and route should be fully traversed to match
        }
    }

    companion object {
        val routes = listOf(
                "/test/:user/some/path/here",
//                "/test/*/some/more/path/here",
                "/test/path/route/without/splats",
//                "/test/has/splat/at/the/end/*",
                "/test/:id/simple/route/:user/create/",
//                "/matches/all/*/user",
//                "/matches/all/*/user/*/more",
//                "/test/*",
                "/hello"
        )

        val oldEntries = routes.map { OldHandlerEntry(HandlerType.AFTER, it, Handler { }) }
        val newEntries = routes.map { NewHandlerEntry(HandlerType.AFTER, it, Handler { }, Handler { }) }
        val entriesV2 = EntriesV2.Builder().run {
            routes.forEach {
                add(it)
            }
            build()
        }

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
            entriesV2.matches(requestUri)
        }
    }

    @Ignore("Manual execution")
    @Test
    fun verifyV2MatchingPerfomance() {
        testEntries.forEach { string ->
            val truth = newEntries.any { it.matches(string) }
            val sample = entriesV2.matches(string)

            assertEquals(string, truth, sample)
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
