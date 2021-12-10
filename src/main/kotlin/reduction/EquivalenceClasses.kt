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

// Computes the set of chain eq classes by considering each SCC and only making eq classes of those where there are
// exactly 2 switches that have edges going out of the SCC, all but these 2 switches are then an eq class.
fun chainEQC(cuspt: CUSPT) =
    partialTopologicalOrder(cuspt)
        .filter { it.size >= 4 } // The SCC must be of size at least 4 to gain anything from it, since 2 of the switches
                                // will not be in the eq class
        .map { scc -> Pair(scc.filter { !(cuspt.initialRouting[it]!! allIn scc && cuspt.finalRouting[it]!! allIn scc) }, scc) }
        .map { Pair(it.first, it.second.filter { cuspt.initialRouting[it] != cuspt.finalRouting[it] }.toSet())}
        .filter { it.first.size == 2 }
        .map { EquivalenceClass(it.second - it.first, BatchOrder.UNKNOWN) }.toSet()
