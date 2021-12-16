import translate.*
import java.util.*

typealias SCC = Set<Int>
typealias SCCId = Int

fun topologicalDecomposition(cuspt: CUSPT): List<CUSPT> {
    val pto = partialTopologicalOrder(cuspt)
    val posDFAState = switchPossibleDFAStates(cuspt, pto)
    val ptoi: Iterable<IndexedValue<SCC>> = pto.withIndex()
    val switchToSCCId: Map<Switch, SCCId> = ptoi.flatMap { (sccid, scc) -> scc.map { Pair(it, sccid) } }.toMap()

    val sccNarrowness = ptoi.associate { Pair(it.index, 0.0) }.toMutableMap() // SCC to Narrowness

    val sccId = ptoi.first { cuspt.ingressSwitch in it.value }.index
    sccNarrowness[sccId] = 1.0

    data class Subproblem(val switches: MutableSet<Switch>, val initSwitch: Switch, var finalSwitch: Switch)
    var currentSubproblem = Subproblem(mutableSetOf(), cuspt.ingressSwitch, -1)
    val subproblems = mutableListOf<Subproblem>()

    for ((i, scc) in ptoi) {
        if (sccNarrowness[i]!! == 1.0 && scc.size == 1 && posDFAState[scc.first()]!!.size == 1 && currentSubproblem.switches.isNotEmpty()) {
            currentSubproblem.switches.addAll(scc)
            currentSubproblem.finalSwitch = scc.first()
            subproblems.add(currentSubproblem)
            currentSubproblem = Subproblem(mutableSetOf(), scc.first(), -1)
        }

        val nexts = scc.flatMap { s -> (cuspt.initialRouting[s] ?: setOf()) union (cuspt.finalRouting[s] ?: setOf()) }
            .filter { it !in scc }
        val outdegree = nexts.size
        nexts.map { switchToSCCId[it]!! }.forEach {
            sccNarrowness[it] = sccNarrowness[it]!! + (sccNarrowness[i]!! / outdegree)
        }
        currentSubproblem.switches.addAll(scc)
    }

    currentSubproblem.finalSwitch = ptoi.last().value.first()
    subproblems.add(currentSubproblem)

    // Remove trivial subproblems
    subproblems.removeIf { it.switches.size <= 2 }

    val subCusps = subproblems.map { sp ->
        val dfa = dfaOf<Switch>(sp.switches) {
            val initialSwitchState = posDFAState[sp.initSwitch]!!.single()
            val finalSwitchState = if (sp.finalSwitch == -2) posDFAState[sp.finalSwitch]!! else setOf(posDFAState[sp.finalSwitch]!!.single())

            val oldToNewState = cuspt.policy.states.associateWith {
                state(initial = it == initialSwitchState, final = it in finalSwitchState)
            }

            cuspt.policy.delta.forEach { (from, outgoing) ->
                outgoing.forEach { (label, to) ->
                    if (label in sp.switches)
                        oldToNewState[from]!!.edgeTo(oldToNewState[to]!!, label)
                }
            }
        }

        val subreachability = dfaOf<Switch>(sp.switches) {
            val sI = state(initial = true)
            val sF = state(final = true)

            sI.edgeTo(sF, sp.finalSwitch)
        }

        CUSPT(
            sp.initSwitch,
            sp.finalSwitch,
            cuspt.initialRouting.filter { it.key != sp.finalSwitch && it.key in sp.switches } + mapOf(sp.finalSwitch to setOf()),
            cuspt.finalRouting.filter { it.key != sp.finalSwitch && it.key in sp.switches } + mapOf(sp.finalSwitch to setOf()),
            dfa intersect subreachability
        )
    }

    return subCusps
}


fun switchPossibleDFAStates(cuspt: CUSPT, pto: List<SCC>): Map<Switch, Set<DFAState>> {
    fun nextStates(ns: Set<DFAState>, s: Switch) =
        ns.map { cuspt.policy[it, s] }

    val posDFAStates = cuspt.allSwitches.associateWith { setOf<DFAState>() }.toMutableMap()

    posDFAStates[cuspt.ingressSwitch] = nextStates(setOf(cuspt.policy.initial), cuspt.ingressSwitch).toSet()

    for (scc in pto) {
        for (i in 0 until scc.size - 1) {
            for (s in scc) {
                val nexts = (cuspt.initialRouting[s] ?: setOf()) union (cuspt.finalRouting[s] ?: setOf())

                nexts.filter { it in scc }.forEach {
                    posDFAStates[it] = posDFAStates[it]!! union nextStates(posDFAStates[s]!!, it)
                }
            }
        }

        for (s in scc) {
            val nexts = (cuspt.initialRouting[s] ?: setOf()) union (cuspt.finalRouting[s] ?: setOf())

            nexts.filter { it !in scc }.forEach {
                posDFAStates[it] = posDFAStates[it]!! union nextStates(posDFAStates[s]!!, it)
            }
        }
    }

    for ((s, dfaStates) in posDFAStates) {
        val unusableSwitches = cuspt.policy.relevantLabels() intersect pto.takeWhile { s !in it }.flatten()
        posDFAStates[s] = dfaStates.filter { canReachGoalFromState(cuspt.policy, it, unusableSwitches) }.toSet()
    }

    // Hack to make sure pseudo-final switch has DFA state
    posDFAStates[-2] = cuspt.policy.finals

    return posDFAStates
}


fun <T> canReachGoalFromState(dfa: DFA<T>, state: DFAState, blacklistedLabels: Set<T> = setOf()): Boolean {
    val h = mutableMapOf<DFAState, Boolean>()
    fun aux(state: DFAState): Boolean {
        if (state in dfa.finals)
            h[state] = true
        if (state in h) return h[state]!!

        h[state] = false
        h[state] = dfa.delta[state]?.filter { state != it.value && it.key !in blacklistedLabels}?.any { aux(it.value) } ?: false
        return h[state]!!
    }
    return aux(state)
}

private val _ptos = mutableMapOf<CUSPT, List<SCC>>()
fun partialTopologicalOrder(cuspt: CUSPT): List<SCC> {
    if (cuspt in _ptos) return _ptos[cuspt]!!

    data class NodeInfo(var index: Int, var lowlink: Int, var onStack: Boolean)
    data class Edge(val source: Switch, val target: Switch)

    var index = 0
    val stack = Stack<Int>()
    val info = cuspt.allSwitches.associateWith { NodeInfo(-1, -1, false) }
    var scc = 0
    val sccs = mutableMapOf<Int, Int>() // Switch to scc

    val allEdges = (cuspt.finalRouting.toList() + cuspt.initialRouting.toList()).flatMap { (s, Rs) -> Rs.map { Edge(s, it) } }

    fun strongConnect(node: Int) {
        info[node]!!.index = index
        info[node]!!.lowlink = index
        index += 1
        stack.push(node)
        info[node]!!.onStack = true


        val nodeEdges = allEdges.filter { it.source == node }
        if (nodeEdges.isNotEmpty()) {
            nodeEdges.map { it.target }.forEach {
                if (info[it]!!.index == -1) {
                    strongConnect(it)
                    info[node]!!.lowlink = Integer.min(info[node]!!.lowlink, info[it]!!.lowlink)
                } else if (info[it]!!.onStack) {
                    info[node]!!.lowlink = Integer.min(info[node]!!.lowlink, info[it]!!.index)
                }
            }
        }

        if (info[node]!!.lowlink == info[node]!!.index) {
            scc++
            do {
                val w = stack.pop()
                info[w]!!.onStack = false
                sccs[w] = scc
            } while(node != w)
        }
    }

    strongConnect(cuspt.ingressSwitch)
    cuspt.allSwitches.forEach {
        if (info[it]!!.index == -1) {
            strongConnect(it)
        }
    }

    return sccs.map { Pair(scc - it.value, it.key) }.groupBy { it.first }.toList().sortedBy { it.first }.map { it.second.map { it.second }.toSet() }
}