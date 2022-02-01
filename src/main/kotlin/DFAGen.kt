package translate

import CUSPT
import GRAPHICS_OUT
import Switch
import UpdateSynthesisModel
import generateCUSPFromUSM
import generateCUSPTFromCUSP
import partialTopologicalOrder
import reduction.Rational
import java.io.File

object Cached {
    lateinit var usm: UpdateSynthesisModel
    val pseudoCUSPT by lazy { generateCUSPTFromCUSP(generateCUSPFromUSM(usm, dfaOf(usm.switches) { state(initial = true) })) }
    val tto by lazy { totalTopologicalOrder(Cached.pseudoCUSPT) }
}

fun generateDFAFromUSMProperties(usm: UpdateSynthesisModel): DFA<Switch> {
    Cached.usm = usm
    return if (Options.noTopologicalNFAReduction)
            generateDFAFromUSMPropertiesNoReachability(usm) intersect genReachabilityDFA(usm)
        else
            DFATopologicalOrderReduction(generateDFAFromUSMPropertiesNoReachability(usm) intersect genReachabilityDFA(usm), Cached.tto)
}



fun generateDFAFromUSMPropertiesNoReachability(usm: UpdateSynthesisModel, topologicalReduction: Boolean = true): DFA<Switch> {
    // NFA for waypoint
    val combinedWaypointNFA =
        if(usm.waypoint != null && usm.waypoint.waypoints.isNotEmpty())
            genCombinedDFAOf(usm.waypoint.waypoints.map { waypointDFA(usm, it) }, true)
        else null

    val conditionalEnforcementNFA =
        if (!usm.conditionalEnforcements.isNullOrEmpty())
            genCombinedDFAOf(usm.conditionalEnforcements.map { condEnforcementDFA(usm, it.s, it.sPrime) }, false)
        else null

    val alternativeWaypointNFA =
        if (!usm.alternativeWaypoints.isNullOrEmpty())
            genCombinedDFAOf(usm.alternativeWaypoints.map { altWaypointDFA(usm, it.s1, it.s2) }, false)
        else null

    val arbitraryNFA =
        if (usm.dfa != null)
            genArbitraryDfaDFA(usm, usm.dfa)
        else null

    val nfas = listOfNotNull(combinedWaypointNFA, conditionalEnforcementNFA, alternativeWaypointNFA, arbitraryNFA)
    return if (nfas.isEmpty())
            DFA.acceptingAll(usm.switches)
    else if (Options.noTopologicalNFAReduction || nfas.size == 1)
        nfas.reduce { acc, dfa -> acc intersect dfa }
    else
        DFATopologicalOrderReduction(nfas.reduce { acc, dfa -> acc intersect dfa }, Cached.tto)
}


fun genCombinedDFAOf(all: List<DFA<Switch>>, topologicalReduction: Boolean): DFA<Switch> =
    if(!topologicalReduction || Options.noTopologicalNFAReduction || all.size == 1)
        all.reduce { acc, it -> acc intersect it }
    else
        all.reduce { acc, it -> DFATopologicalOrderReduction(acc intersect it, Cached.tto) }


fun genReachabilityDFA(usm: UpdateSynthesisModel): DFA<Switch> =
    dfaOf<Switch>(usm.switches) {
        val sI = state(initial = true)
        val sF = state(final = true)

        sI.edgeTo(sF, usm.reachability.finalNode)
        sF.edgeToDead(usm.switches)
    }

fun waypointDFA(usm: UpdateSynthesisModel, w: Switch) =
    dfaOf<Switch>(usm.switches) {
        val sI = state(initial = true)
        val sJ = state(final = true)

        sI.edgeTo(sJ, w)
        sJ.edgeToDead(w)
    }


fun condEnforcementDFA(usm: UpdateSynthesisModel, s: Switch, sPrime: Switch) =
    dfaOf(usm.switches) {
        val sI = state(initial = true, final = true)
        val sMet = state()
        val sPrimeMet = state(final = true)
        val sF = state(final = true)

        sI.edgeTo(sMet, s)
        sMet.edgeTo(sF, sPrime)
        sI.edgeTo(sPrimeMet, sPrime)
        sPrimeMet.edgeToDead(s)
    }

fun altWaypointDFA(usm: UpdateSynthesisModel, s1: Switch, s2: Switch) =
    dfaOf(usm.switches) {
        val sI = state(initial = true)
        val sF = state(final = true)
        sI.edgeTo(sF, s1)
        sI.edgeTo(sF, s2)
    }

fun genArbitraryDfaDFA(usm: UpdateSynthesisModel, dfa: UpdateSynthesisModel.DFA) =
    dfaOf(usm.switches) {
        val stateToNewStateMap = dfa.finalStates.associateWith { state(final = true) }.toMutableMap()
        if (dfa.initialState in dfa.finalStates)
            stateToNewStateMap[dfa.initialState] = state(initial = true, final = true)
        else
            stateToNewStateMap[dfa.initialState] = state(initial = true)

        for (edge in dfa.edges) {
            if (stateToNewStateMap[edge.from] == null)
                stateToNewStateMap[edge.from] = state()
            if (stateToNewStateMap[edge.to] == null)
                stateToNewStateMap[edge.to] = state()
        }

        for (e in dfa.edges) {
            stateToNewStateMap[e.from]!!.edgeTo(stateToNewStateMap[e.to]!!, e.label)
        }
    }

fun DFATopologicalOrderReduction(dfa: DFA<Switch>, tto: List<Set<Switch>>): DFA<Switch> {
    val relLabels = dfa.relevantLabels().toSet()
    val order = tto.filter { it.any { it in relLabels } }
    val labels = order.flatMap { it }.toSet()

    if (order.size <= 1)
        return dfa

    val ptoDFA = dfaOf<Switch>(dfa.alphabet) {
        val states = listOf(state(initial = true)) + (1 until order.size).map { state() } + listOf(state(final = true))

        states.first().edgeToDead(labels - order.first())
        for ((p, n, o) in states.zipWithNext().zip(order).map { Triple(it.first.first, it.first.second, it.second) }) {
            p.edgeTo(n, o)
            n.edgeToDead(labels - o)
        }
    }

    return dfa intersect ptoDFA
}

fun totalTopologicalOrder(cuspt: CUSPT): List<Set<Switch>> {
    val edges = (cuspt.finalRouting.toList() + cuspt.initialRouting.toList()).groupBy { it.first }.map { it.key to it.value.flatMap { it.second } }.toMap()
    val ptoi = partialTopologicalOrder(cuspt).withIndex()
    val switchToSCCId: Map<Switch, Int> = ptoi.flatMap { (sccid, scc) -> scc.map { Pair(it, sccid) } }.toMap()
    val sccNarrowness = ptoi.map { it.index to Rational(0,1) }.toMap().toMutableMap()

    assert(ptoi.first().value == setOf(-1))
    sccNarrowness[0] = Rational(1,1)

    for ((i, scc) in ptoi) {
        val a = scc.flatMap { edges[it] ?: setOf() }.filter { it !in scc }.map { switchToSCCId[it]!! }.toSet()
        for (sccid in a) {
            sccNarrowness[sccid] = sccNarrowness[sccid]!! + sccNarrowness[i]!! / a.size
        }
    }

    val bunches = mutableListOf<Set<Switch>>()
    val next = mutableSetOf<Switch>()
    for ((i, scc) in ptoi) {
        if (sccNarrowness[i]!! == Rational(1,1)) {
            bunches.add(next.toSet() + scc)
            next.clear()
        } else {
            next.addAll(scc)
        }
    }
    bunches.add(next)

    return bunches.filter { it.isNotEmpty() }
}