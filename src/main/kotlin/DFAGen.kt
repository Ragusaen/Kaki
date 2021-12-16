package translate

import CUSPT
import GRAPHICS_OUT
import Switch
import UpdateSynthesisModel
import generateCUSPFromUSM
import generateCUSPTFromCUSP
import partialTopologicalOrder
import java.io.File

fun generateDFAFromUSMProperties(usm: UpdateSynthesisModel): DFA<Switch> {
    // NFA for reachability
    val reachabilityNFA = genReachabilityDFA(usm)

    if(Options.noTopologicalNFAReduction)
        return reachabilityNFA intersect generateDFAFromUSMPropertiesNoReachability(usm)

    val pseudoCUSP = generateCUSPTFromCUSP(generateCUSPFromUSM(usm, dfaOf(usm.switches) { state(initial = true) }))
    val tto = totalTopologicalOrder(pseudoCUSP)

    return DFATopologicalOrderReduction(reachabilityNFA intersect generateDFAFromUSMPropertiesNoReachability(usm), tto)
}

fun generateDFAFromUSMPropertiesNoReachability(usm: UpdateSynthesisModel): DFA<Switch> {
    // NFA for waypoint
    val combinedWaypointNFA =
        if(usm.waypoint != null && usm.waypoint.waypoints.isNotEmpty())
            genCombinedDFAOf(usm, usm.waypoint.waypoints.map { waypointDFA(usm, it) } )
        else
            DFA.acceptingAll(usm.switches)
    if (Options.drawGraphs) combinedWaypointNFA.toGraphviz().toFile(File(GRAPHICS_OUT + "/waypointdfa.svg"))

    val conditionalEnforcementNFA =
        if (!usm.conditionalEnforcements.isNullOrEmpty())
            genCombinedDFAOf(usm, usm.conditionalEnforcements.map { condEnforcementDFA(usm, it.s, it.sPrime) })
        else
            DFA.acceptingAll(usm.switches)
    if (Options.drawGraphs) conditionalEnforcementNFA.toGraphviz().toFile(File(GRAPHICS_OUT + "/condEnf.svg"))

    val alternativeWaypointNFA =
        if (!usm.alternativeWaypoints.isNullOrEmpty())
            genCombinedDFAOf(usm, usm.alternativeWaypoints.map { altWaypointDFA(usm, it.s1, it.s2) })
        else
            DFA.acceptingAll(usm.switches)
    if (Options.drawGraphs) alternativeWaypointNFA.toGraphviz().toFile(File(GRAPHICS_OUT + "/altWaypoint.svg"))

    if(Options.noTopologicalNFAReduction)
        return combinedWaypointNFA intersect conditionalEnforcementNFA intersect alternativeWaypointNFA


    val pseudoCUSP = generateCUSPTFromCUSP(generateCUSPFromUSM(usm, DFA.acceptingAll(usm.switches)))
    val tto = totalTopologicalOrder(pseudoCUSP)

    return DFATopologicalOrderReduction(combinedWaypointNFA intersect conditionalEnforcementNFA intersect alternativeWaypointNFA, tto)
}

fun genCombinedDFAOf(usm:UpdateSynthesisModel, all: List<DFA<Switch>>): DFA<Switch> {
    if(Options.noTopologicalNFAReduction || all.size == 1)
        return all.reduce { acc, it -> acc intersect it }

    val pseudoCUSP = generateCUSPTFromCUSP(generateCUSPFromUSM(usm, DFA.acceptingAll(usm.switches)))
    val tto = totalTopologicalOrder(pseudoCUSP)

    return all.reduce { acc, it -> DFATopologicalOrderReduction(acc intersect it, tto) }
}

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
        if (usm.conditionalEnforcements == null)
            return@dfaOf
        val ss = state()
        val sF = state(final = true)

        sI.edgeTo(ss, s)
        ss.edgeTo(sF, sPrime)
        ss.edgeTo(sF, sPrime)
        sI.edgeTo(sF, sPrime)
    }

fun altWaypointDFA(usm: UpdateSynthesisModel, s1: Switch, s2: Switch) =
    dfaOf(usm.switches) {
        if (usm.alternativeWaypoints == null) {
            state(initial = true, final = true)
            return@dfaOf
        }

        val sI = state(initial = true)
        val sF = state(final = true)
        sI.edgeTo(sF, s1)
        sI.edgeTo(sF, s2)
    }

fun DFATopologicalOrderReduction(dfa: DFA<Switch>, tto: List<Set<Switch>>): DFA<Switch> {
    val relLabels = dfa.relevantLabels().toSet()
    val order = tto.filter { it.any { it in relLabels } }
    val labels = order.flatMap { it }.toSet()

    if (order.size <= 1)
        return dfa

    val ptoDFA = dfaOf<Switch>(dfa.alphabet) {
        val states = listOf(state(initial = true)) + (1 until order.size - 1).map { state() } + listOf(state(final = true))

        states.first().edgeToDead(labels - order.first())
        for ((p, n, o) in states.zipWithNext().zip(order.drop(1)).map { Triple(it.first.first, it.first.second, it.second) }) {
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
    val sccNarrowness = ptoi.map { it.index to 0.0 }.toMap().toMutableMap()

    assert(ptoi.first().value == setOf(-1))
    sccNarrowness[1] = 1.0

    for ((i, scc) in ptoi) {
        val a = scc.flatMap { edges[it] ?: setOf() }.filter { it !in scc }.map { switchToSCCId[it]!! }.toSet()
        for (sccid in a) {
            sccNarrowness[sccid] = sccNarrowness[sccid]!! + sccNarrowness[i]!! / a.size
        }
    }

    val bunches = mutableListOf<Set<Switch>>()
    val next = mutableSetOf<Switch>()
    for ((i, scc) in ptoi) {
        if (sccNarrowness[i]!! == 1.0) {
            bunches.add(next.toSet() + scc)
            next.clear()
        } else {
            next.addAll(scc)
        }
    }
    bunches.add(next)

    return bunches.filter { it.isNotEmpty() }
}