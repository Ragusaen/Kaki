package utils

import UpdateSynthesisModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import translate.updateSynthesisModelFromJsonText
import java.io.File
import java.nio.file.Path
import kotlin.random.Random

fun generateNewFilesByRandom(
    transform: (UpdateSynthesisModel, Random) -> UpdateSynthesisModel?, pathToFolder: Path, ext: String, randomSeed: Int
) {
    val random = Random(randomSeed)
    val dir = pathToFolder.toFile()
    assert(dir.isDirectory)
    val newDir = Path.of(pathToFolder.toString() + ext).toFile()
    if (!newDir.exists()) newDir.mkdir()

    for (file in dir.walk().iterator()) {
        if (file.isDirectory) continue

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

fun addRandomWaypointsToNetworks(
    usm: UpdateSynthesisModel, random: Random, numMoreWaypoints: Int
): UpdateSynthesisModel? {
    val candidateSwitches =
        usm.initialRouting.filter { i_it -> usm.finalRouting.map { it.source }.contains(i_it.source) }.map { it.source }
            .toMutableList()
    candidateSwitches -= usm.waypoint.waypoints

    val newWaypoints = mutableListOf<Int>()
    for (i in 1..numMoreWaypoints) {
        if (candidateSwitches.isEmpty()) {
            return null
        }
        val new = candidateSwitches.sorted()[random.nextInt(candidateSwitches.size)]
        newWaypoints += new
        candidateSwitches -= new
    }
    val newUsm = usm.addWaypoints(newWaypoints)
    assert(newUsm.waypoint.waypoints.size == newUsm.waypoint.waypoints.distinct().size)
    assert(newUsm.waypoint.waypoints.size == usm.waypoint.waypoints.size + numMoreWaypoints)

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
    val sPrime = candidateSwitches[random.nextInt(candidateSwitches.size)]

    return usm.setConditionalEnforcement(s, sPrime)
}

fun setAlternativeWaypointToUSM(usm: UpdateSynthesisModel, random: Random): UpdateSynthesisModel? {
    val candidateSwitches = usm.switches.toMutableList()
    if (candidateSwitches.size < 2)
        return null

    val s1 = candidateSwitches[random.nextInt(candidateSwitches.size)]
    candidateSwitches -= s1

    if (s1 in usm.initialRouting.map { it_i -> it_i.source }) candidateSwitches.filter { it_f -> it_f in usm.finalRouting.map { it.source } }
    else if (s1 in usm.finalRouting.map { it_i -> it_i.source }) candidateSwitches.filter { it_f -> it_f in usm.initialRouting.map { it.source } }
    val s2 = candidateSwitches[random.nextInt(candidateSwitches.size)]

    return usm.setAlternativeWaypoint(s1, s2)
}

fun UpdateSynthesisModel.addWaypoints(waypoints: List<Int>): UpdateSynthesisModel {
    val initRouting = this.initialRouting.map { listOf(it.source, it.target) }.toSet()
    val finalRouting = this.finalRouting.map { listOf(it.source, it.target) }.toSet()

    val newWaypoint =
        UpdateSynthesisModel.Waypoint(reachability.initialNode, reachability.finalNode, waypoint.waypoints + waypoints)
    val properties = UpdateSynthesisModel.Properties(
        newWaypoint, conditionalEnforcement, alternativeWaypoint, loopFreedom, reachability
    )

    return UpdateSynthesisModel(initRouting, finalRouting, properties)
}

fun UpdateSynthesisModel.setConditionalEnforcement(s: Int, sPrime: Int): UpdateSynthesisModel {
    val initRouting = this.initialRouting.map { listOf(it.source, it.target) }.toSet()
    val finalRouting = this.finalRouting.map { listOf(it.source, it.target) }.toSet()

    val newConditionalEnforcement = UpdateSynthesisModel.ConditionalEnforcement(s, sPrime)
    val properties = UpdateSynthesisModel.Properties(
        waypoint, newConditionalEnforcement, alternativeWaypoint, loopFreedom, reachability
    )

    return UpdateSynthesisModel(initRouting, finalRouting, properties)
}

fun UpdateSynthesisModel.setAlternativeWaypoint(s1: Int, s2: Int): UpdateSynthesisModel {
    val initRouting = this.initialRouting.map { listOf(it.source, it.target) }.toSet()
    val finalRouting = this.finalRouting.map { listOf(it.source, it.target) }.toSet()

    val newAlternativeWaypoint = UpdateSynthesisModel.AlternativeWaypoint(s1, s2)
    val properties = UpdateSynthesisModel.Properties(
        waypoint, conditionalEnforcement, newAlternativeWaypoint, loopFreedom, reachability
    )

    return UpdateSynthesisModel(initRouting, finalRouting, properties)

}
