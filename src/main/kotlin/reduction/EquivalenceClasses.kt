enum class BatchOrder { UNKNOWN, FIRST, LAST}
data class EquivalenceClass(val switches: Set<Switch>, val batchOrder: BatchOrder) {
    override fun toString() = "Equivalence class (${switches}) $batchOrder"
}


fun discoverEquivalenceClasses(cuspt: CUSPT): Set<EquivalenceClass> {
    val i = if (Options.noInitialFinalEquivalenceClasses) EquivalenceClass(setOf(), BatchOrder.UNKNOWN)
            else onlyInInitial(cuspt)

    val f = if (Options.noInitialFinalEquivalenceClasses) EquivalenceClass(setOf(), BatchOrder.UNKNOWN)
            else onlyInFinal(cuspt)

    val cs = if (Options.noChainEquivalenceClasses) setOf() else chainEQC(cuspt)

    // If switch is both in (i or f) and cs then prefer i or f
    return (cs.map { EquivalenceClass(it.switches - (i.switches + f.switches), it.batchOrder) }.toSet() + i + f)
                .filter { it.switches.isNotEmpty() }.toSet()
}


fun onlyInInitial(cuspt: CUSPT) =
    EquivalenceClass(
        cuspt.allSwitches.filter { cuspt.initialRouting[it]!!.isNotEmpty() && cuspt.finalRouting[it]!!.isEmpty() }.toSet(),
        BatchOrder.LAST
    )

fun onlyInFinal(cuspt: CUSPT) =
    EquivalenceClass(
        cuspt.allSwitches.filter { cuspt.initialRouting[it]!!.isEmpty() && cuspt.finalRouting[it]!!.isNotEmpty() }.toSet(),
        BatchOrder.FIRST
    )

infix fun <T> Collection<T>.allIn(other: Collection<T>) = other.containsAll(this)

data class EndPointsSCC(val endpoints: Set<Switch>, val scc: SCC)

// Computes the set of chain eq classes by considering each SCC and only making eq classes of those where there are
// exactly 2 switches that have edges going out of the SCC, all but these 2 switches are then an eq class.
fun chainEQC(cuspt: CUSPT) =
    partialTopologicalOrder(cuspt)
        // The SCC must be of size at least 4 to gain anything from it, since 2 of the switches will not be in the eq class
        .filter { it.size >= 4 }
        // Find the endpoints of the SCC
        .map { scc -> EndPointsSCC(scc.filter { !(cuspt.initialRouting[it]!! allIn scc && cuspt.finalRouting[it]!! allIn scc) }.toSet(), scc) }
        // Only use those with exactly 2 endpoints
        .filter { it.endpoints.size == 2 }
        // Check if this SCC is chain reducible to inverting final routing and finding a topological order
        .filter { isChainReducible(it.scc, cuspt) }
        .map { EquivalenceClass(it.scc - it.endpoints, BatchOrder.UNKNOWN) }.toSet()

data class Edge(val from: Switch, val to: Switch)
fun isChainReducible(a: SCC, cuspt: CUSPT) =
    // Make edges with inverted final routing
    a.flatMap { s -> cuspt.initialRouting[s]!!.map { t -> Edge(s, t) } + cuspt.finalRouting[s]!!.map { t -> Edge(t, s) } }.let { edges ->
        topologicalSort(edges.toSet()) != null
    }

fun topologicalSort(edges: Set<Edge>): List<Switch>? {
    val es = edges.toMutableSet()
    val l = mutableListOf<Switch>() // List that will contain the sorted elements
    val s = (edges.map { it.from } - edges.map { it.to }.toSet()).toMutableSet() // List of switches with no ingoing edges

    fun <T> MutableSet<T>.pop(): T = this.first().let { this.remove(it); it }

    while (s.isNotEmpty()) {
        val n = s.pop()
        l.add(n)
        for (e in es.filter { it.to == n }) {
            es.remove(e)
            if (es.filter { it.to == e.to }.isEmpty())
                s.add(e.to)
        }
    }

    return if (es.isNotEmpty()) null else l
}