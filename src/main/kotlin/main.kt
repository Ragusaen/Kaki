import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import translate.*
import verification.Verifier
import verification.sequentialSearch
import java.io.File
import java.lang.Integer.max
import java.nio.file.Path
import kotlin.system.measureTimeMillis

const val GRAPHICS_OUT = "graphics_out"
const val PETRI_OUT = "petri_out"

typealias Switch = Int

data class CUSP(
    val ingressSwitches: Set<Switch>,
    val egressSwitches: Set<Switch>,
    val initialRouting: Map<Switch, Set<Switch>>,
    val finalRouting: Map<Switch, Set<Switch>>,
    val policy: DFA<Switch>,
) {
    val allSwitches: Set<Switch> = (initialRouting + finalRouting).entries.flatMap { setOf(it.key) + it.value }.toSet()
}

// Same as CUSP, but we have pseudonodes as initial and final that routes to set of initial and final switches, respectively.
data class CUSPT(
    val ingressSwitch: Switch,
    val egressSwitch: Switch,
    val initialRouting: Map<Switch, Set<Switch>>,
    val finalRouting: Map<Switch, Set<Switch>>,
    val policy: DFA<Switch>,
) {
    val allSwitches: Set<Switch> = (initialRouting + finalRouting).entries.flatMap { setOf(it.key) + it.value }.toSet()

    override fun toString(): String {
        var res = "flow: $ingressSwitch to $egressSwitch\n"
        res += "Initial: "
        for ((from, to) in initialRouting) {
            res += "$from -> $to"
        }
        res += "Final: "
        for ((from, to) in finalRouting) {
            res += "$from -> $to"
        }
        return res
    }
}

enum class Verbosity { None, Minimal, Low, High }
typealias v = Verbosity

var printbuffer = ""
fun pseudoprint(s: String) {
    if (Options.printOnce) {
        printbuffer += "$s\n"
    } else {
        println(s)
    }
}
fun printFinal() {
    if (Options.printOnce) {
        println(printbuffer)
    }
}
fun Verbosity.println(s: String) {
    if (Options.verbosity >= this)
        pseudoprint(s)
}

fun generateCUSPFromUSM(usm: UpdateSynthesisModel, dfa: DFA<Switch>) =
    CUSP(
        setOf(usm.reachability.initialNode),
        setOf(usm.reachability.finalNode),
        usm.switches.associateWith { s ->
            usm.initialRouting.filter { it.source == s }.map { it.target }.toSet() },
        usm.switches.associateWith { s ->
            usm.finalRouting.filter { it.source == s }.map { it.target }.toSet() },
        dfa
    )

fun generateCUSPTFromCUSP(cusp: CUSP) =
    CUSPT(
        -1,
        -2,
        cusp.initialRouting
                + mapOf(-1 to cusp.ingressSwitches)
                + cusp.egressSwitches.associateWith { setOf(-2) }
                + mapOf(-2 to setOf()),
        cusp.finalRouting
                + mapOf(-1 to cusp.ingressSwitches)
                + cusp.egressSwitches.associateWith { setOf(-2) }
                + mapOf(-2 to setOf()),
        cusp.policy,
    )


fun runProblem() {
    var time: Long = measureTimeMillis {
        val jsonText = File(Options.testCase).readText()

        val usm = updateSynthesisModelFromJsonText(jsonText)

        val dfa: DFA<Switch>
        var time: Long = measureTimeMillis {
            dfa = generateDFAFromUSMProperties(usm)
        }

        if (Options.drawGraphs) dfa.toGraphviz().toFile(File("${GRAPHICS_OUT}/dfa.svg"))
        v.Low.println("DFA generation time: ${time / 1000.0} seconds \nDFA states: ${dfa.states.size} \nDFA transitions: ${dfa.delta.entries.sumOf { it.value.size }}")

        val cuspt = generateCUSPTFromCUSP(generateCUSPFromUSM(usm, dfa))
        v.Minimal.println("Problem file: ${Options.testCase}\n" +
            "Switches to update: ${cuspt.allSwitches.count { cuspt.initialRouting[it] != cuspt.finalRouting[it] }}\n"
        )

        if (Options.drawGraphs) outputPrettyNetwork(usm).toFile(File("${GRAPHICS_OUT}/network.svg"))

        val subcuspts: List<CUSPT>
        time = measureTimeMillis {
            subcuspts = if (Options.noTopologicalDecompositioning) listOf(cuspt) else topologicalDecomposition(cuspt)
        }
        v.Low.println("Decomposed topology into ${subcuspts.size} subproblems")
        v.Low.println("Topological decomposition took ${time / 1000.0} seconds")

        var omega = listOf<Batch>()
        var unsolvable = false

        subproblems@for ((i, subcuspt) in subcuspts.withIndex()) {
            v.High.println("-- Solving subproblem $i --")

            val eqclasses = discoverEquivalenceClasses(subcuspt)

            v.High.println(eqclasses.joinToString("\n"))

            val modelFile = File.createTempFile("model$i", ".pnml")

            val petriGame: PetriGame
            val queryFile: File
            val updateSwitchCount: Int
            time = measureTimeMillis {
                val (_petriGame, _queryPath, _updateSwitchCount) = generatePetriGameFromCUSPT(subcuspt, eqclasses)
                petriGame = _petriGame
                queryFile = _queryPath
                updateSwitchCount = _updateSwitchCount
            }
            v.High.println("Translation to Petri game took ${time / 1000.0} seconds.")

            if (Options.debugPath != null)
                petriGame.apply { addGraphicCoordinatesToPG(this) }

            val pnml = generatePnmlFileFromPetriGame(petriGame)
            if (Options.debugPath != null) {
                File(PETRI_OUT + "/" + Options.debugPath!! + "_model$i.pnml").writeText(pnml)
                File(PETRI_OUT + "/" + Options.debugPath!! + "_query$i.q").writeText(queryFile.readText())
            }
            modelFile.writeText(pnml)
            v.High.println(
                "Petri game switches: ${usm.switches.size} \nPetri game updateable switches: ${updateSwitchCount}\nPetri game places: ${petriGame.places.size} \nPetri game transitions: ${petriGame.transitions.size}" +
                        "\nPetri game arcs: ${petriGame.arcs.size}\nPetri game initial markings: ${petriGame.places.sumOf { it.initialTokens }}"
            )

            val verifier: Verifier
            time = measureTimeMillis {
                verifier = Verifier(modelFile)
                val ub = sequentialSearch(verifier, queryFile, updateSwitchCount)
                val omegaPrime = ub?.mapIndexed { i, b ->
                    if (i == 0)
                        b union eqclasses.filter { it.batchOrder == BatchOrder.FIRST }.fold(setOf()) { acc, a -> acc union a.switches }
                    else if (i == ub.size - 1)
                        b union eqclasses.filter { it.batchOrder == BatchOrder.LAST }.fold(setOf()) { acc, a -> acc union a.switches }
                    else
                        b
                }

                if (omegaPrime == null) {
                    v.Low.println("Subproblem $i unsolvable!")
                    unsolvable = true
                } else {
                    v.High.println("Subproblem $i solvable with minimum ${omegaPrime.size} batches.")
                    v.High.println("$omegaPrime")
                    omega = (0 until max(omega.size, omegaPrime.size))
                        .map { omega.getOrElse(it) { setOf() } union omegaPrime.getOrElse(it) { setOf() } }
                }
            }
            v.Low.println("Subproblem verification time: ${time / 1000.0} seconds")
            if (unsolvable) break@subproblems
        }

        if (unsolvable) {
            v.Minimal.println("Problem is unsolvable!")
        } else {
            v.Minimal.println("Minimum batches required: ${omega.size}")
            v.Low.println("$omega")
        }
    }
    v.Minimal.println("Total program runtime: ${time / 1000.0} seconds")
    printFinal()
}

fun calcFlipSubpaths(){
    var flipSubpaths = mutableListOf<MutableList<Switch>>()
    val time = measureTimeMillis {
        val jsonText = File(Options.testCase).readText()
        val usm = updateSynthesisModelFromJsonText(jsonText)
        val dfa = generateDFAFromUSMPropertiesNoReachability(usm)

        if (Options.drawGraphs) dfa.toGraphviz().toFile(File(GRAPHICS_OUT + "/noreachabilitydfa.svg"))

        flipSubpaths = dfa.getWaypointSubPaths(generateCUSPFromUSM(usm, generateDFAFromUSMProperties(usm)))
    }

    File(Options.onlyFLIPSubpaths!!).writeText(flipSubpaths.joinToString(";") { it.joinToString(",") })
    println("Flip subpaths generated in ${time / 1000.0} seconds!")
}

object Options {
    val argParser = ArgParser(programName = "conupsyn")

    private val _enginePath by argParser.argument(ArgType.String, description = "Path to verifypn-games engine")
    val enginePath: Path by lazy { Path.of(_enginePath) }

    val testCase by argParser.argument(
        ArgType.String,
        fullName = "test_case",
        description = "The test case to run on"
    )

    val drawGraphs by argParser.option(
        ArgType.Boolean,
        shortName = "g",
        description = "Draw graphs for various components"
    ).default(false)

    val verbosity by argParser.option(
        ArgType.Choice<Verbosity>(),
        shortName = "V",
        description = "Verbosity of print output"
    ).default(Verbosity.Low)


    val onlyFLIPSubpaths by argParser.option(
        ArgType.String,
        shortName = "f",
        description = "Only calculate subpaths for FLIP, nothing more"
    )

    val debugPath by argParser.option(
        ArgType.String,
        shortName = "d",
        fullName = "debugPrefix",
        description = "Output debugging files with the given prefix"
    )

    val noTopologicalDecompositioning by argParser.option(
        ArgType.Boolean,
        shortName = "T",
        description = "Disable topological decompositioning"
    ).default(false)

    val noInitialFinalEquivalenceClasses by argParser.option(
        ArgType.Boolean,
        shortName = "E",
        description = "Disable initial/final equivalence classes"
    ).default(false)

    val noChainEquivalenceClasses by argParser.option(
        ArgType.Boolean,
        shortName = "C",
        description = "Disable chain equivalence classes"
    ).default(false)

    val noTopologicalNFAReduction by argParser.option(
        ArgType.Boolean,
        shortName = "R",
        description = "Disable topological NFA reduction"
    ).default(false)

    val maxSwicthesInBatch by argParser.option(
        ArgType.Int,
        shortName = "m",
        fullName = "switches_in_batch",
        description = "The maximum number of switches that can be in a batch. 0 = No limit"
    ).default(0)

    val printOnce by argParser.option(
        ArgType.Boolean,
        shortName = "O",
        fullName = "print_once",
        description = "Only print once"
    ).default(false)

    val outputVerifyPN by argParser.option(ArgType.Boolean, shortName = "P", description = "output the output from verifypn").default(false)
}

const val version = "1.9"

fun main(args: Array<String>) {
    println("Version: $version \n ${args.joinToString(" ")}")

    Options.argParser.parse(args)
    if (Options.onlyFLIPSubpaths != null) calcFlipSubpaths()
    else runProblem()
}

typealias Batch = Set<Int>
typealias ConupSeq = List<Batch>
fun <T> Collection<T>.powerset(): Set<Set<T>> = when {
    isEmpty() -> setOf(setOf())
    else -> drop(1).powerset().let { it + it.map { it + first() } }
}

fun <T> Collection<T>.powersetne(): Set<Set<T>> =
    powerset().filter { it.isNotEmpty() }.toSet()
fun sols(s: Set<Int>): Set<ConupSeq> =
    if (s.isEmpty()) setOf()
    else s.powersetne().flatMap {
            ot: Batch ->
        if ((s - ot).isNotEmpty())
            sols(s - ot).map { listOf(ot) + it }.toSet()
        else
            setOf(listOf(ot))
    }.toSet()

fun test() {
    val ls = 4
    val switches = (1..ls).toSet()

    println(switches.powersetne())

    println(sols(switches).filter { it.first().contains(1) && it.last().contains(ls) })
    println(sols(switches).filter { it.first().contains(1) && it.last().contains(ls) }.size)
}