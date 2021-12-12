package translate

import CUSPT
import GRAPHICS_OUT
import SCC
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

    val pseudoCUSP = generateCUSPTFromCUSP(generateCUSPFromUSM(usm, dfaOf(usm.switches) { it.state(initial = true) }))
    val pto = partialTopologicalOrder(pseudoCUSP)

    return DFATopologicalOrderReduction(reachabilityNFA intersect generateDFAFromUSMPropertiesNoReachability(usm), pto)
}

fun generateDFAFromUSMPropertiesNoReachability(usm: UpdateSynthesisModel): DFA<Switch> {
    // NFA for waypoint
    val combinedWaypointNFA = genCombinedWaypointDFA(usm)
    if (Options.drawGraphs) combinedWaypointNFA.toGraphviz().toFile(File(GRAPHICS_OUT + "/waypointdfa.svg"))

    val conditionalEnforcementNFA = genConditionalEnforcementDFA(usm)
    if (Options.drawGraphs) conditionalEnforcementNFA.toGraphviz().toFile(File(GRAPHICS_OUT + "/condEnf.svg"))

    val alternativeWaypointNFA = genAlternativeWaypointDFA(usm)
    if (Options.drawGraphs) alternativeWaypointNFA.toGraphviz().toFile(File(GRAPHICS_OUT + "/altWaypoint.svg"))

    if(Options.noTopologicalNFAReduction)
        return combinedWaypointNFA intersect conditionalEnforcementNFA intersect alternativeWaypointNFA


    val pseudoCUSP = generateCUSPTFromCUSP(generateCUSPFromUSM(usm, dfaOf(usm.switches) { it.state(initial = true) }))
    val pto = partialTopologicalOrder(pseudoCUSP)

    return DFATopologicalOrderReduction(combinedWaypointNFA intersect conditionalEnforcementNFA intersect alternativeWaypointNFA, pto)
}


fun genCombinedWaypointDFA(usm: UpdateSynthesisModel): DFA<Switch> {
    val waypoints = waypointDFAs(usm)

    if(Options.noTopologicalNFAReduction)
        return waypoints.reduce { acc, it -> acc intersect it }

    if (waypoints.size > 1) {
        val pseudoCUSPT = generateCUSPTFromCUSP(generateCUSPFromUSM(usm, dfaOf(emptySet()) { it.state(initial = true) }))
        val tto = totalTopologicalOrder(pseudoCUSPT)

        return waypoints.reduce { acc, it -> DFATopologicalOrderReduction(acc intersect it, tto) }
    } else {
        return waypoints.reduce { acc, it -> acc intersect it }
    }
}

fun genConditionalEnforcementDFA(usm: UpdateSynthesisModel): DFA<Switch> {
    val condEnfDFA = dfaOf(usm.switches) { d ->
        val sI = d.state(initial = true, final = true)
        if (usm.conditionalEnforcement == null)
            return@dfaOf
        val ss = d.state()
        val sF = d.state(final = true)

        sI.edgeTo(ss, usm.conditionalEnforcement.s)
        ss.edgeTo(sF, usm.conditionalEnforcement.sPrime)
        ss.edgeTo(sF, usm.conditionalEnforcement.sPrime)
        sI.edgeTo(sF, usm.conditionalEnforcement.sPrime)
    }


    if(Options.noTopologicalNFAReduction)
        return condEnfDFA

    val pseudoCUSP = generateCUSPTFromCUSP(generateCUSPFromUSM(usm, dfaOf(usm.switches) { it.state(initial = true) }))
    val pto = partialTopologicalOrder(pseudoCUSP)

    return DFATopologicalOrderReduction(condEnfDFA, pto)
}

fun genAlternativeWaypointDFA(usm: UpdateSynthesisModel): DFA<Switch> {
    val altWayDFA = dfaOf(usm.switches) { d ->
        if (usm.alternativeWaypoint == null) {
            d.state(initial = true, final = true)
            return@dfaOf
        }

        val sI = d.state(initial = true)
        val sF = d.state(final = true)
        sI.edgeTo(sF, usm.alternativeWaypoint.s1)
        sI.edgeTo(sF, usm.alternativeWaypoint.s2)
    }

    if(Options.noTopologicalNFAReduction)
        return altWayDFA

    val pseudoCUSP = generateCUSPTFromCUSP(generateCUSPFromUSM(usm, dfaOf(usm.switches) { it.state(initial = true) }))
    val pto = partialTopologicalOrder(pseudoCUSP)

    return DFATopologicalOrderReduction(altWayDFA, pto)
}

fun genReachabilityDFA(usm: UpdateSynthesisModel): DFA<Switch> =
    dfaOf<Switch>(usm.switches) { d ->
        val sI = d.state(initial = true)
        val sF = d.state(final = true)

        sI.edgeTo(sF, usm.reachability.finalNode)
        sF.edgeToDead(usm.switches)
    }

fun waypointDFAs(usm: UpdateSynthesisModel): Set<DFA<Switch>> =
    usm.waypoint.waypoints.map { waypointDFA(usm, it) }.toSet()

fun waypointDFA(usm: UpdateSynthesisModel, w: Switch) =
    dfaOf<Switch>(usm.switches) { d ->
        val sI = d.state(initial = true)
        val sJ = d.state(final = true)

        sI.edgeTo(sJ, w)
        sJ.edgeToDead(w)
    }

fun DFATopologicalOrderReduction(dfa: DFA<Switch>, tto: List<Set<Switch>>): DFA<Switch> {
    val relLabels = dfa.relevantLabels().toSet()
    val order = tto.map { it intersect relLabels }.filter { it.isNotEmpty() }

    if (order.size <= 1)
        return dfa

    val ptoDFA = dfaOf<Switch>(dfa.alphabet) { d ->
        val states = listOf(d.state(initial = true)) + (1 until order.size - 1).map { d.state() } + listOf(d.state(final = true))

        states.first().edgeToDead(relLabels - order.first())
        for ((p, n, o) in states.zipWithNext().zip(order.drop(1)).map { Triple(it.first.first, it.first.second, it.second) }) {
            p.edgeTo(n, o)
            n.edgeToDead(relLabels - o)
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
            bunches.add(next.toSet())
            next.clear()
            bunches.add(scc)
        } else {
            next.addAll(scc)
        }
    }
    bunches.add(next)

    return bunches.filter { it.isNotEmpty() }
}