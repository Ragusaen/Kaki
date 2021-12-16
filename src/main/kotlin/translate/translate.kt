package translate

import PetriGame
import Arc
import CUSPT
import EquivalenceClass
import Place
import Switch
import Transition
import UpdateSynthesisModel
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import org.redundent.kotlin.xml.*
import pcreateTempFile
import java.io.File

// Constants used for consistent naming
const val dfaPrefix = "DFA"
const val topologyPrefix = "TOPOLOGY"
const val updatePrefix = "UPDATE"
const val switchPrefix = "SWITCH"

typealias DFAState = Int

data class PetriGameQueryPath(val petriGame: PetriGame, val queryFile: File, val updateSwitchCount: Int)

private data class Edge(val from: Switch, val to: Switch) {
    override fun toString(): String {
        return "$from --> $to"
    }
}

private infix fun Switch.edge(other: Switch) = Edge(this, other)

fun generatePetriGameFromCUSPT(cuspt: CUSPT, eqclasses: Set<EquivalenceClass>): PetriGameQueryPath {
    // Sets so duplicates cannot occur
    val places: MutableSet<Place> = mutableSetOf()
    val transitions: MutableSet<Transition> = mutableSetOf()
    val arcs: MutableSet<Arc> = mutableSetOf()

    val switchToTopologyPlaceMap: MutableMap<Switch, Place> = mutableMapOf()
    val switchToTopologyUnvisitedPlaceMap: MutableMap<Switch, Place> = mutableMapOf()
    val edgeToTopologyTransitionMap: MutableMap<Edge, Transition> = mutableMapOf()
    val switchComponentsFinalPlaces: MutableSet<Place> = mutableSetOf()

    // Network Topology Components
    // Create places. Everyone has a placekeeper place and a yet-to-be-visited place,
    for (s: Int in cuspt.allSwitches) {
        val p = Place(0, "${topologyPrefix}_P_$s")
        val pUnvisited = Place(1, "${topologyPrefix}_UV_$s")

        switchToTopologyPlaceMap[s] = p
        switchToTopologyUnvisitedPlaceMap[s] = pUnvisited

        places.add(p)
        places.add(pUnvisited)
    }

    // Create shared transitions
    for ((source, nextHops) in (cuspt.initialRouting.entries + cuspt.finalRouting.entries)) {
        for (nextHop in nextHops) {
            val t = Transition(false, "${topologyPrefix}_T_" + source + "_" + nextHop)
            transitions.add(t)
            edgeToTopologyTransitionMap[source edge nextHop] = t

            // Add arc from place to transition
            val place: Place = switchToTopologyPlaceMap[source]!!
            arcs.add(Arc(place, t, 1))

            // Add arc from unvisitedplace to transition
            val uPlace = switchToTopologyUnvisitedPlaceMap[source]!!
            arcs.add(Arc(uPlace, t, 1))

            // Add arc from transition to nextHop node
            val tPlace: Place? = switchToTopologyPlaceMap[nextHop]
            assert(tPlace != null)
            arcs.add(Arc(t, tPlace!!, 1))
        }
    }

    // Switch Components
    // Find u \in V where R^i(u) != R^f(u)

    // Only switches that are different in final and initial routing
    val updatableSwitches = (cuspt.initialRouting.entries.toList() + cuspt.finalRouting.entries.toList())
        .groupBy { it.key }.filter { it.value.size != 2 || it.value[0] != it.value[1] }.map { it.key }.toSet()

    val updatableNonEQClass: Set<Switch> = updatableSwitches.toSet() - eqclasses.flatMap { it.switches }.toSet()

    // Update State Component
    val numSwitchComponents = updatableNonEQClass.size + eqclasses.size
    val maxInBatch = if (Options.maxSwicthesInBatch != 0) Options.maxSwicthesInBatch else numSwitchComponents
    val pCountInitialTokens = eqclasses.count { it.batchOrder == BatchOrder.FIRST }

    val pQueueing = Place(1, "${updatePrefix}_P_QUEUEING").apply { places.add(this) }
    val pUpdating = Place(0, "${updatePrefix}_P_UPDATING").apply { places.add(this) }
    val pBatches = Place(0, "${updatePrefix}_P_BATCHES").apply { places.add(this) }
    val pInvCount = Place(maxInBatch - pCountInitialTokens, "${updatePrefix}_P_INVCOUNT").apply { places.add(this) }
    val pCount = Place(pCountInitialTokens, "${updatePrefix}_P_COUNT").apply { places.add(this) }
    val pTotalQueued = Place(eqclasses.count { it.batchOrder == BatchOrder.FIRST }, "${updatePrefix}_P_TOTAL_QUEUED").apply { places.add(this) }
    val pInvTotalUpdated = Place(numSwitchComponents, "${updatePrefix}_P_INV_TOTAL_UPDATED").apply { places.add(this) }
    val tConup = Transition(true, "${updatePrefix}_T_CONUP").apply { transitions.add(this) }
    val tReady = Transition(false, "${updatePrefix}_T_READY").apply { transitions.add(this) }

    arcs.add(Arc(pCount, tConup, 1))
    arcs.add(Arc(tConup, pCount, 1))
    arcs.add(Arc(pQueueing, tConup, 1))
    arcs.add(Arc(tConup, pUpdating, 1))
    arcs.add(Arc(tConup, pBatches, 1))
    arcs.add(Arc(pUpdating, tReady, 1))
    arcs.add(Arc(tReady, pQueueing, 1))
    arcs.add(Arc(pInvCount, tReady, maxInBatch))
    arcs.add(Arc(tReady, pInvCount, maxInBatch))
    arcs.add(Arc(tReady, pInvTotalUpdated, 1))
    arcs.add(Arc(pInvTotalUpdated, tReady, 1))

    for (eqc in eqclasses + updatableNonEQClass.map { EquivalenceClass(setOf(it), BatchOrder.UNKNOWN) }) {
        val pg = createSwitchComponent(eqc, cuspt, edgeToTopologyTransitionMap, switchComponentsFinalPlaces, pCount, pInvCount, pQueueing, pUpdating, pTotalQueued, pInvTotalUpdated, numSwitchComponents)
        places.addAll(pg.places)
        transitions.addAll(pg.transitions)
        arcs.addAll(pg.arcs)
    }

    // Visited Places are already handled previously

    // Packet Injection Component
    val tInject = Transition(false, "PACKET_INJECT_T")
    transitions.add(tInject)
    arcs.add(Arc(pUpdating, tInject, 1))
    arcs.add(Arc(tInject, switchToTopologyPlaceMap[cuspt.ingressSwitch]!!, 1))

    // DFA
    // First we translate the DFA into a Petri Game
    val (dfaPetriGame, dfaStateToPlaceMap, dfaActionToTransitionMap) = DFAToPetriGame(cuspt.policy)

    // Add all information from dfa petri to this full petri
    places.addAll(dfaPetriGame.places)
    transitions.addAll(dfaPetriGame.transitions)
    arcs.addAll(dfaPetriGame.arcs)

    // DFA tracking component
    val turnSwitch = Place(0, "${dfaPrefix}_TURN").apply { places.add(this) }
    val switchToTrackPlace = mutableMapOf<Switch, Place>()
    for (s in cuspt.policy.relevantLabels()) {
        val trackPlace = Place(0, "${dfaPrefix}_TRACK_Pswitch${s}")
        switchToTrackPlace[s] = trackPlace
    }
    places.addAll(switchToTrackPlace.values)

    // Create dfa actions transitions
    for (action in cuspt.policy.allActions.filter { it.label in cuspt.policy.relevantLabels() }) {
        val dfaSwitchTransition = dfaActionToTransitionMap[action]!!
        arcs.add(Arc(switchToTrackPlace[action.label]!!, dfaSwitchTransition))
    }

    for ((e, t) in edgeToTopologyTransitionMap.filter { it.key.to in cuspt.policy.relevantLabels() }) {
        arcs.add(Arc(t, switchToTrackPlace[e.to]!!))
    }

    arcs.add(Arc(tInject, turnSwitch))

    // Arcs from turn place to topology transitions
    for ((_, t) in edgeToTopologyTransitionMap.filter { it.key.to in cuspt.policy.relevantLabels() })
        arcs.add(Arc(turnSwitch, t))

    // Arcs from DFA transitions to turn place
    for (t in dfaPetriGame.transitions) {
        arcs.add(Arc(t, turnSwitch))
    }

    // Generate the query
    val queryFile = pcreateTempFile("query")
    val DFAFinalStatePlaces = cuspt.policy.finals.map { dfaStateToPlaceMap[it]!! }

    queryFile.writeText(generateQuery(DFAFinalStatePlaces))

    return PetriGameQueryPath(PetriGame(places, transitions, arcs), queryFile, numSwitchComponents)
}

private fun createSwitchComponent(eqc: EquivalenceClass, cuspt: CUSPT, edgeToTopologyTransitionMap: Map<Edge, Transition>, switchComponentsFinalPlaces: MutableSet<Place>, pCount: Place, pInvCount: Place, pQueueing: Place, pUpdating: Place, pTotalQueued: Place, pInvTotalUpdated: Place, numSwitchComponents: Int): PetriGame {
    val name = eqc.switches.joinToString(separator = "_") { it.toString() }

    val places = mutableSetOf<Place>()
    val transitions = mutableSetOf<Transition>()
    val arcs = mutableSetOf<Arc>()

    // Make sure the initial edge is different to its final correspondent
    val pInit = Place(1, "${switchPrefix}_P_${name}_INIT").apply { places.add(this) }
    val pQueue = Place(if (eqc.batchOrder == BatchOrder.FIRST) 1 else 0, "${switchPrefix}_P_${name}_QUEUE").apply { places.add(this) }
    val pFinal = Place(0, "${switchPrefix}_P_${name}_FINAL").apply { places.add(this); switchComponentsFinalPlaces.add(this) }
    val pLimiter = Place(if (eqc.batchOrder == BatchOrder.FIRST) 0 else 1, "${switchPrefix}_P_${name}_LIMITER").apply { places.add(this) }
    val tQueue = Transition(true, "${switchPrefix}_T_${name}_QUEUE").apply { transitions.add(this) }
    val tUpdate = Transition(false, "${switchPrefix}_T_${name}_UPDATE").apply { transitions.add(this) }

    arcs.add(Arc(tQueue, pCount, 1))
    arcs.add(Arc(pCount, tUpdate, 1))
    arcs.add(Arc(pInit, tQueue, 1))
    arcs.add(Arc(tQueue, pInit, 1))
    arcs.add(Arc(pLimiter, tQueue, 1))
    arcs.add(Arc(tQueue, pQueue, 1))
    arcs.add(Arc(pInit, tUpdate, 1))
    arcs.add(Arc(pQueue, tUpdate, 1))
    arcs.add(Arc(tUpdate, pFinal, 1))
    arcs.add(Arc(pInvCount, tQueue, 1))
    arcs.add(Arc(tQueue, pQueueing, 1))
    arcs.add(Arc(pQueueing, tQueue, 1))
    arcs.add(Arc(pUpdating, tUpdate, 1))
    arcs.add(Arc(tUpdate, pUpdating, 1))
    arcs.add(Arc(tUpdate, pInvCount, 1))
    arcs.add(Arc(tQueue, pTotalQueued, 1))
    arcs.add(Arc(pInvTotalUpdated, tUpdate, 1))

    if (eqc.batchOrder == BatchOrder.LAST) {
        // This assumes at most 1 LAST batch
        arcs.add(Arc(pTotalQueued, tQueue, numSwitchComponents - 1))
    }

    for (s_i in eqc.switches) {
        // Do not create arcs for transitions that are both in initial and final
        val initialNextHops = cuspt.initialRouting[s_i]!! - cuspt.finalRouting[s_i]!!
        val finalNextHops = cuspt.finalRouting[s_i]!! - cuspt.initialRouting[s_i]!!

        for (nextHop in initialNextHops) {
            arcs.add(Arc(pInit, edgeToTopologyTransitionMap[s_i edge nextHop]!!))
            arcs.add(Arc(edgeToTopologyTransitionMap[s_i edge nextHop]!!, pInit))
        }

        for (nextHop in finalNextHops) {
            arcs.add(Arc(pFinal, edgeToTopologyTransitionMap[s_i edge nextHop]!!))
            arcs.add(Arc(edgeToTopologyTransitionMap[s_i edge nextHop]!!, pFinal))
        }
    }
    return PetriGame(places, transitions, arcs)
}

data class DFAToPetriGame(val petriGame: PetriGame,
                          val stateToPlaceMap: Map<DFAState, Place>,
                          val actionToTransitionMap: Map<DFA.Action<Switch>, Transition>)

fun DFAToPetriGame(dfa: DFA<Switch>): DFAToPetriGame {
    val arcs: MutableSet<Arc> = mutableSetOf()


    val stateToPlaceMap = dfa.states.associateWith { Place(if (it == dfa.initial) 1 else 0, "${dfaPrefix}_Pstate_${it}") }

    var i = 0
    val actionToTransitionMap = dfa.allActions.filter { it.label in dfa.relevantLabels() }.associateWith {
        Transition(false, "${dfaPrefix}_T${i++}_Switch${it.label}")
    }

    for (aToT in actionToTransitionMap) {
        arcs.add(Arc(stateToPlaceMap[aToT.key.from]!!, aToT.value))
        arcs.add(Arc(aToT.value, stateToPlaceMap[aToT.key.to]!!))
    }

    return DFAToPetriGame(PetriGame(stateToPlaceMap.values.toSet(), actionToTransitionMap.values.toSet(), arcs.toSet()), stateToPlaceMap, actionToTransitionMap)
}

fun updateSynthesisModelFromJsonText(jsonText: String): UpdateSynthesisModel {
    // HACK: Change waypoint with 1 element of type int to type list of ints
    val regex = """waypoint": (\d+)""".toRegex()
    val text = regex.replace(jsonText) { m ->
        "waypoint\": [" + m.groups[1]!!.value + "]"
    }

    // Update Synthesis Model loaded from json
    return Json.decodeFromString<UpdateSynthesisModel>(text)
}

fun generatePnmlFileFromPetriGame(petriGame: PetriGame): String {
    val pnml = xml("pnml") {
        xmlns = """http://www.pnml.org/version-2009/grammar/pnml"""
        "net" {
            attribute("id", """ComposedModel""")
            attribute("type", """http://www.pnml.org/version-2009/grammar/ptnet""")

            "page" {
                attribute("id", """page0""")
                for (p: Place in petriGame.places) {
                    "place" {
                        attribute("id", p.name)
                        "graphics" {
                            "position" {
                                attribute("x", p.pos.first)
                                attribute("y", p.pos.second)
                            }
                        }
                        "name" {
                            "graphics" {
                                "offset" {
                                    attribute("x", "0")
                                    attribute("y", "0")
                                }
                            }
                            "text" {
                                -p.name
                            }
                        }
                        "initialMarking" {
                            "text" {
                                attribute("removeWhitespace", "")
                                -p.initialTokens.toString()
                            }
                        }
                    }
                }
                for (t: Transition in petriGame.transitions) {
                    "transition" {
                        attribute("id", t.name)
                        "player" {
                            "value" {
                                -if (t.controllable) "0" else "1"
                            }
                        }

                        "name" {
                            "graphics" {
                                "offset" {
                                    attribute("x", "0")
                                    attribute("y", "0")
                                }
                            }
                            "text" {
                                -t.name
                            }
                        }
                        "graphics" {
                            "position" {
                                attribute("x", t.pos.first)
                                attribute("y", t.pos.second)
                            }
                        }
                    }
                }
                for (a: Arc in petriGame.arcs) {
                    "arc" {
                        attribute("id", a.name)
                        attribute("source", a.source.name)
                        attribute("target", a.target.name)
                        attribute("type", "normal")
                        if (a.weight > 1) {
                            "inscription" {
                                "text" {
                                    attribute("removeWhitespace", "")
                                    -a.weight.toString()
                                }
                            }
                        }
                    }
                }
            }

            "name" {
                "text" {
                    -"ComposedModel"
                }
            }
        }
    }

    // Apply some initial xml-format
    var res = """<?xml version="1.0" encoding="UTF-8" standalone="no"?>""" + "\n" + pnml.toString()

    // Tapaal does not know xml.. so we must remove some newlines before and after ints
    res = res.replace("""<([^\s]*) removeWhitespace="">\s*([^\s]+)\s*""".toRegex(), "<$1>$2")

    return res
}

fun generateQuery(finalDFAState: List<Place>) =
        "EF (UPDATE_P_BATCHES <= 0 and (" +
                finalDFAState.joinToString(" or ") { "${it.name} == 1" } +
        "))"