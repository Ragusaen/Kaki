package utils

import UpdateSynthesisModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import translate.updateSynthesisModelFromJsonText
import java.io.File
import java.nio.file.Path
import kotlin.random.Random

fun generateNewFilesByRandom(
    transform: (UpdateSynthesisModel, Random) -> UpdateSynthesisModel?, pathToFolder: Path, ext: String, randomSeed: Int, numMore: Int
) {
    val dir = pathToFolder.toFile()
    assert(dir.isDirectory)
    val newDir = Path.of(pathToFolder.toString() + ext).toFile()
    if (!newDir.exists()) newDir.mkdir()

    for (file in dir.walk().iterator()) {
        if (file.isDirectory) continue
        for (i in 0..numMore) {
            val random = Random(randomSeed + 13 * numMore)
            val newUSM = transform(updateSynthesisModelFromJsonText(file.readText()), random)
            if (newUSM == null) {
                println("Failed: Could not transform ${file.name}")
                continue
            }
            val jElem = Json.encodeToJsonElement(newUSM)

            val newPath = newDir.absolutePath + File.separator + file.name
            val newFile = File(newPath)
            if (!newFile.exists()) newFile.createNewFile()
            newFile.writeText(jElem.toString())

            println("Transform ${file.name}")
        }
    }
}

fun addRandomWaypointsToNetworks(
    usm: UpdateSynthesisModel, random: Random
): UpdateSynthesisModel? {
    val candidateSwitches =
        usm.initialRouting.filter { i_it -> usm.finalRouting.map { it.source }.contains(i_it.source) }.map { it.source }
            .toMutableList()
    candidateSwitches -= usm.waypoint.waypoints
    if (candidateSwitches.isEmpty())
        return null

    val new = candidateSwitches.sorted()[random.nextInt(candidateSwitches.size)]

    val newUsm = usm.addWaypoints(listOf(new))
    assert(newUsm.waypoint.waypoints.size == newUsm.waypoint.waypoints.distinct().size)
    assert(newUsm.waypoint.waypoints.size == usm.waypoint.waypoints.size + 1)

    return newUsm
}

fun setConditionalEnforcementToUSM(usm: UpdateSynthesisModel, random: Random): UpdateSynthesisModel? {
    val candidateSwitches = usm.switches.toMutableList()
    if (candidateSwitches.size < 2)
        return null

    val s = candidateSwitches[random.nextInt(candidateSwitches.size)]
    candidateSwitches -= s

    if (s in usm.initialRouting.map { it.source }) candidateSwitches.filter { it in usm.initialRouting.map { it.source } }
    else if (s in usm.finalRouting.map { it.source }) candidateSwitches.filter { it in usm.finalRouting.map { it.source } }

    if (usm.conditionalEnforcements != null && s in usm.conditionalEnforcements.map { it.s })
        candidateSwitches -= usm.conditionalEnforcements.first { it.s == s }.sPrime
    if (usm.conditionalEnforcements != null && s in usm.conditionalEnforcements.map { it.sPrime })
        candidateSwitches -= usm.conditionalEnforcements.first { it.sPrime == s }.s
    if (candidateSwitches.isEmpty())
        return null

    val sPrime = candidateSwitches[random.nextInt(candidateSwitches.size)]

    return usm.setConditionalEnforcement(s, sPrime)
}

fun addAlternativeWaypointToUSM(usm: UpdateSynthesisModel, random: Random, numMoreAltWaypoints: Int): UpdateSynthesisModel? {
    val candidateSwitches = usm.switches.toMutableList()
    if (candidateSwitches.size < 2)
        return null

    val s1 = candidateSwitches[random.nextInt(candidateSwitches.size)]
    candidateSwitches -= s1

    if (s1 in usm.initialRouting.map { it_i -> it_i.source }) candidateSwitches.filter { it_f -> it_f in usm.finalRouting.map { it.source } }
    else if (s1 in usm.finalRouting.map { it_i -> it_i.source }) candidateSwitches.filter { it_f -> it_f in usm.initialRouting.map { it.source } }

    if (usm.alternativeWaypoints != null && s1 in usm.alternativeWaypoints.map { it.s1 })
        candidateSwitches -= usm.alternativeWaypoints.first { it.s1 == s1 }.s2
    if (candidateSwitches.isEmpty())
        return null

    val s2 = candidateSwitches[random.nextInt(candidateSwitches.size)]

    return usm.setAlternativeWaypoint(s1, s2)
}

fun UpdateSynthesisModel.addWaypoints(waypoints: List<Int>): UpdateSynthesisModel {
    val initRouting = this.initialRouting.map { listOf(it.source, it.target) }.toSet()
    val finalRouting = this.finalRouting.map { listOf(it.source, it.target) }.toSet()

    val newWaypoint =
        UpdateSynthesisModel.Waypoint(reachability.initialNode, reachability.finalNode, waypoint.waypoints + waypoints)
    val properties = UpdateSynthesisModel.Properties(
        newWaypoint, conditionalEnforcements, alternativeWaypoints, loopFreedom, reachability
    )

    return UpdateSynthesisModel(initRouting, finalRouting, properties)
}

fun UpdateSynthesisModel.setConditionalEnforcement(s: Int, sPrime: Int): UpdateSynthesisModel {
    val initRouting = this.initialRouting.map { listOf(it.source, it.target) }.toSet()
    val finalRouting = this.finalRouting.map { listOf(it.source, it.target) }.toSet()

    val newConditionalEnforcement = (conditionalEnforcements ?: listOf()) + UpdateSynthesisModel.ConditionalEnforcement(s, sPrime)
    val properties = UpdateSynthesisModel.Properties(
        waypoint, newConditionalEnforcement, alternativeWaypoints, loopFreedom, reachability
    )

    return UpdateSynthesisModel(initRouting, finalRouting, properties)
}

fun UpdateSynthesisModel.setAlternativeWaypoint(s1: Int, s2: Int): UpdateSynthesisModel {
    val initRouting = this.initialRouting.map { listOf(it.source, it.target) }.toSet()
    val finalRouting = this.finalRouting.map { listOf(it.source, it.target) }.toSet()

    val newAlternativeWaypoint = (alternativeWaypoints ?: listOf()) + UpdateSynthesisModel.AlternativeWaypoint(s1, s2)
    val properties = UpdateSynthesisModel.Properties(
        waypoint, conditionalEnforcements, newAlternativeWaypoint, loopFreedom, reachability
    )

    return UpdateSynthesisModel(initRouting, finalRouting, properties)

}
