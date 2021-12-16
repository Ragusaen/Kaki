package translate

import CUSPT
import GRAPHICS_OUT
import Switch
import UpdateSynthesisModel
import generateCUSPFromUSM
import generateCUSPTFromCUSP
import partialTopologicalOrder
import java.io.File

object Cached {
    lateinit var usm: UpdateSynthesisModel
    val pseudoCUSPT by lazy { generateCUSPTFromCUSP(generateCUSPFromUSM(usm, dfaOf(usm.switches) { state(initial = true) })) }
    val tto by lazy { totalTopologicalOrder(Cached.pseudoCUSPT) }
}

fun generateDFAFromUSMProperties(usm: UpdateSynthesisModel): DFA<Switch> {
    Cached.usm = usm
    return DFATopologicalOrderReduction(generateDFAFromUSMPropertiesNoReachability(usm) intersect genReachabilityDFA(usm), Cached.tto)
}


fun generateDFAFromUSMPropertiesNoReachability(usm: UpdateSynthesisModel, topologicalReduction: Boolean = true): DFA<Switch> {
    // NFA for waypoint
    val combinedWaypointNFA =
        if(usm.waypoint != null && usm.waypoint.waypoints.isNotEmpty())
            genCombinedDFAOf(usm, usm.waypoint.waypoints.map { waypointDFA(usm, it) }, true)
        else null

    val conditionalEnforcementNFA =
        if (!usm.conditionalEnforcements.isNullOrEmpty())
            genCombinedDFAOf(usm, usm.conditionalEnforcements.map { condEnforcementDFA(usm, it.s, it.sPrime) }, false)
        else null

    val alternativeWaypointNFA =
        if (!usm.alternativeWaypoints.isNullOrEmpty())
            genCombinedDFAOf(usm, usm.alternativeWaypoints.map { altWaypointDFA(usm, it.s1, it.s2) }, false)
        else null

    val nfas = listOfNotNull(combinedWaypointNFA, conditionalEnforcementNFA, alternativeWaypointNFA)
    return if (nfas.isEmpty())
            DFA.acceptingAll(usm.switches)
    else if (Options.noTopologicalNFAReduction || nfas.size == 1)
        nfas.reduce { acc, dfa -> acc intersect dfa }
    else
        DFATopologicalOrderReduction(nfas.reduce { acc, dfa -> acc intersect dfa }, Cached.tto)
}


fun genCombinedDFAOf(usm:UpdateSynthesisModel, all: List<DFA<Switch>>, topologicalReduction: Boolean): DFA<Switch> =
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

fun waypointDFAs(usm: UpdateSynthesisModel): Set<DFA<Switch>> =
    usm.waypoint?.waypoints?.map { waypointDFA(usm, it) }?.toSet() ?: emptySet()

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
        val ss = state()
        val sF = state(final = true)

        sI.edgeTo(ss, s)
        ss.edgeTo(sF, sPrime)
        sI.edgeTo(sF, sPrime)
    }

fun altWaypointDFA(usm: UpdateSynthesisModel, s1: Switch, s2: Switch) =
    dfaOf(usm.switches) {
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
    val sccNarrowness = ptoi.map { it.index to 0.0 }.toMap().toMutableMap()

    assert(ptoi.first().value == setOf(-1))
    sccNarrowness[0] = 1.0

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