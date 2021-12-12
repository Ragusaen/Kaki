package translate

import CUSP
import Switch
import guru.nidi.graphviz.attribute.Attributes
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Renderer
import guru.nidi.graphviz.graph
import guru.nidi.graphviz.model.MutableGraph
import guru.nidi.graphviz.toGraphviz
import java.io.File

private typealias State = Int

fun <T> dfaOf(alphabet: Set<T>, f: (dfa: DFAContext<T>) -> Unit): DFA<T> {
    val dfaContext = DFAContext<T>(alphabet)

    f(dfaContext)

    return dfaContext.toDFA()
}

class DFAContext<T>(val alphabet: Set<T>) {
    protected var sid = 0

    protected var initialState: State<T>? = null
    protected val finalStates = mutableSetOf<State<T>>()
    protected val states = mutableSetOf<State<T>>()
    protected val actions = mutableMapOf<State<T>, MutableMap<T, State<T>>>()

    val deadstate = State(this).apply { id = DFA.deadstate }

    class State<T>(val context: DFAContext<T>) {
        var id = context.sid++

        override fun toString() = "State $id"

        fun edgeTo(other: State<T>, labels: Set<T>) {
            for (l in labels) {
                context.actions[this]!![l] = other
            }
        }

        fun edgeTo(other: State<T>, label: T) {
            context.actions[this]!![label] = other
        }

        fun edgeToDead(labels: Set<T>) {
            for (l in labels) {
                context.actions[this]!![l] = context.deadstate
            }
        }

        fun edgeToDead(label: T) {
            context.actions[this]!![label] = context.deadstate
        }
    }

    fun state(initial: Boolean = false, final: Boolean = false): State<T> {
        val r = State(this)
        actions[r] = mutableMapOf()
        if (initial)
            initialState = r
        if (final)
            finalStates.add(r)
        return r
    }

    fun toDFA() = DFA(actions.map { Pair(it.key.id, it.value.map { Pair(it.key, it.value.id) }.toMap()) }.toMap(),
        initialState!!.id, finalStates.map { it.id }.toSet(), alphabet )
}

class DFA<T>(val delta: Map<State, Map<T, State>>, val initial: State, val finals: Set<State>, val alphabet: Set<T>) {
    companion object {
        const val deadstate = -1
    }

    val states = delta.keys + delta.flatMap { it.value.map { it.value } }

    data class Action<T>(val from: State, val label: T, val to:State)
    val allActions = delta.flatMap { o ->
        o.value.map { Action(o.key, it.key, it.value) } + (alphabet - o.value.map { it.key }).map { Action(o.key, it, o.key) }
    }

    operator fun get(s: State) = delta[s] ?: mapOf()
    operator fun get(s: State, a: T) = (delta[s] ?: mapOf())[a] ?: s
}

// This assumes they have the same alphabet
infix fun <T> DFA<T>.intersect(other: DFA<T>): DFA<T> =
    dfaOf(this.alphabet) { d ->
        val h = mutableMapOf<Pair<State, State>, DFAContext.State<T>>()
        val canReachFinal = mutableMapOf<DFAContext.State<T>, Boolean>()
        fun expand(s: Pair<State, State>): DFAContext.State<T> {
            if (s !in h) {
                if (s.first == DFA.deadstate || s.second == DFA.deadstate) {
                    h[s] = d.deadstate
                    canReachFinal[h[s]!!] = false
                } else {
                    h[s] = d.state(
                        initial = s.first == this.initial && s.second == other.initial,
                        final = s.first in this.finals && s.second in other.finals
                    )
                    canReachFinal[h[s]!!] = s.first in this.finals && s.second in other.finals

                    val nextStates = (this[s.first].keys + other[s.second].keys).map { l ->
                        val t = expand(Pair(this[s.first, l], other[s.second, l]))

                        canReachFinal[h[s]!!] = (canReachFinal[h[s]!!] ?: false) || (canReachFinal[t] ?: false)
                        Pair(t, l)
                    }

                    for ((ns, l) in nextStates) {
                        if (canReachFinal[ns] == true) {
                            h[s]!!.edgeTo(ns, l)
                        } else if (canReachFinal[h[s]!!] == true ) {
                            h[s]!!.edgeToDead(l)
                        }
                    }
                }
            }
            return h[s]!!
        }

        expand(Pair(this.initial, other.initial))
    }

fun <T> DFA<T>.relevantLabels() =
    this.delta.values.flatMap { it.entries.filter { it.value != DFA.deadstate }.map { it.key } }

//fun <T> DFA<T>.pruned() {
//    pruneByDirection(forward = true)
//    pruneByDirection(forward = false)
//}

//fun DFA.pruneByDirection(forward: Boolean) {
//    val nextActions: MutableSet<DFA.Action> =
//        if (forward) {
//            val sI = initialState
//            if (sI != null)
//                mutableSetOf(actions.find { it.from == initialState }!!)
//            else
//                throw Exception("Tried to do forward pruning of NFA, but no initial state is present in NFA.")
//        } else {
//            if (finalStates.isNotEmpty())
//                actions.filter { finalStates.contains(it.to) }.toMutableSet()
//            else
//                return
//        }
//    val actionsNotReached = (actions subtract nextActions.toMutableSet()).toMutableSet()
//
//    while (nextActions.isNotEmpty()) {
//        val action = nextActions.first()
//        actionsNotReached.remove(action)
//
//        nextActions += actionsNotReached.filter { if (forward) it.from == action.to else it.to == action.from }
//
//        nextActions.remove(action)
//    }
//
//    actions.removeAll(actionsNotReached)
//    this.setStatesByActions()
//}

fun <T> DFA<T>.toGraphviz(): Renderer {
    val graph: MutableGraph = graph(directed = true) {
        initial.toString().get().attrs().add("color", "red")
        finals.forEach { it.toString().get().attrs().add("shape", "doublecircle") }

        delta.forEach { (s, o) ->
            o.entries.groupBy { it.value }.map { Pair(it.key, it.value.map { it.key }) }.forEach inner@{ (t, labels) ->
                if (this@toGraphviz.states.size > 20 && t == DFA.deadstate)
                        return@inner
                (s.toString() - t.toString()).get().attrs().add("label", if (labels.size > 5) "many" else labels.joinToString(","))
            }
        }
    }
    return graph.toGraphviz().render(Format.SVG)
}

fun <T> DFA<T>.export(path: String){
    var output = "States:"
    output += states.joinToString(",") { it.toString() }
    output += "\nInitial state:${initial}"
    output += "\nFinal states:"
    output += finals.joinToString(",") { it.toString() }
    output += "\nActions:"
    output += delta.flatMap { o -> o.value.map { Triple(o.key, it.key, it.value) } }. joinToString(separator = ";") { "${it.first},${it.second},${it.third}" }

    File(path).writeText(output)
}

fun DFA<Switch>.getFLIPSubPaths(cusp: CUSP) : MutableList<MutableList<Switch>>{
    val currentPath = mutableListOf<Switch>()
    val pathsFound = mutableListOf<MutableList<Switch>>()

    fun pathsFromSwitch(currentNetworkSwitch: Switch, finalSwitches: List<Switch>, currentDFAState: State){
        if(currentDFAState == DFA.deadstate) return
        currentPath.add(currentNetworkSwitch)

        if(finalSwitches.contains(currentNetworkSwitch) && finals.contains(currentDFAState)){
            pathsFound.add(currentPath.toMutableList())
        }else{
            for(outgoing in (cusp.initialRouting[currentNetworkSwitch]!!.toList() union cusp.finalRouting[currentNetworkSwitch]!!.toList()).toList()){
                if (!currentPath.contains(outgoing)){
                    var nextDFAState: State = currentDFAState
                    if(delta[currentDFAState]!!.containsKey(outgoing))
                        nextDFAState = delta[currentDFAState]!![outgoing]!!

                    pathsFromSwitch(outgoing, finalSwitches, nextDFAState)
                }
            }
        }
        currentPath.remove(currentNetworkSwitch)
    }

    val initRelevantLabels = delta.filterKeys { it == initial }.values.flatMap { it.entries.filter { it.value != DFA.deadstate }.map { it.key } }
    val finalRelevantLabels = delta.values.flatMap { it.entries.filter { finals.contains(it.value) }.map { it.key } }

    for (iLabel in initRelevantLabels)
        pathsFromSwitch(iLabel, finalRelevantLabels, delta[initial]!![iLabel]!!)

    return pathsFound
}

