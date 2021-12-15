package translate

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

    // NFA for waypoint
    val combinedWaypointNFA = genCombinedDFAOf(usm, usm.waypoint.waypoints.map { waypointDFA(usm, it) } )
    if (Options.drawGraphs) combinedWaypointNFA.toGraphviz().toFile(File(GRAPHICS_OUT + "/waypointdfa.svg"))

    val conditionalEnforcementNFA =
        if (usm.conditionalEnforcements != null)
            genCombinedDFAOf(usm, usm.conditionalEnforcements.map { condEnforcementDFA(usm, it.s, it.sPrime) })
        else
            dfaOf(usm.switches) {it.state(initial = true, final = true)}
    if (Options.drawGraphs) conditionalEnforcementNFA.toGraphviz().toFile(File(GRAPHICS_OUT + "/condEnf.svg"))

    val alternativeWaypointNFA =
        if (usm.alternativeWaypoints != null)
            genCombinedDFAOf(usm, usm.alternativeWaypoints.map { altWaypointDFA(usm, it.s1, it.s2) })
        else
            dfaOf(usm.switches) {it.state(initial = true, final = true)}
    if (Options.drawGraphs) alternativeWaypointNFA.toGraphviz().toFile(File(GRAPHICS_OUT + "/altWaypoint.svg"))

    // Intersect the reachability NFA with the waypoints
    val res = combinedWaypointNFA intersect
            reachabilityNFA intersect
            conditionalEnforcementNFA intersect
            alternativeWaypointNFA
    return res
}

fun genCombinedDFAOf(usm:UpdateSynthesisModel, all: List<DFA<Switch>>): DFA<Switch> {
    if(Options.noTopologicalNFAReduction || all.size == 1)
        return all.reduce { acc, it -> acc intersect it }

    val pseudoCUSP = generateCUSPTFromCUSP(generateCUSPFromUSM(usm, dfaOf(usm.switches) { it.state(initial = true) }))
    val pto = partialTopologicalOrder(pseudoCUSP)

    return all.reduce { acc, it -> DFATopologicalOrderReduction(acc intersect it, pto) }
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

fun condEnforcementDFA(usm: UpdateSynthesisModel, s: Switch, sPrime: Switch) =
    dfaOf(usm.switches) { d ->
        val sI = d.state(initial = true, final = true)
        if (usm.conditionalEnforcements == null)
            return@dfaOf
        val ss = d.state()
        val sF = d.state(final = true)

        sI.edgeTo(ss, s)
        ss.edgeTo(sF, sPrime)
        ss.edgeTo(sF, sPrime)
        sI.edgeTo(sF, sPrime)
    }

fun altWaypointDFA(usm: UpdateSynthesisModel, s1: Switch, s2: Switch) =
    dfaOf(usm.switches) { d ->
        if (usm.alternativeWaypoints == null) {
            d.state(initial = true, final = true)
            return@dfaOf
        }

        val sI = d.state(initial = true)
        val sF = d.state(final = true)
        sI.edgeTo(sF, s1)
        sI.edgeTo(sF, s2)
    }

fun DFATopologicalOrderReduction(dfa: DFA<Switch>, pto: List<SCC>): DFA<Switch> {
    val relLabels = dfa.relevantLabels().toSet()
    val order = pto.map { it intersect relLabels }.filter { it.isNotEmpty() }

    if (order.isEmpty())
        return dfa

    val ptoDFA = dfaOf<Switch>(dfa.alphabet) { d ->
        val states = listOf(d.state(initial = true)) + (1 until order.size).map { d.state() } + listOf(d.state(final = true))

        for ((p, n, o) in states.zipWithNext().zip(order).map { Triple(it.first.first, it.first.second, it.second) }) {
            p.edgeTo(n, o)
            n.edgeToDead(relLabels - o)
        }
    }

    return dfa intersect ptoDFA
}