package verification

import Batch
import Switch

fun getUpdateBatchesFromStrategy(strategy: String): List<Batch> {
    // This assumes batches will always be in correct order
    val m = """\["([\w_\d]+)"]""".toRegex().findAll(strategy).map { it.groupValues[1] }.toMutableList()
    val ub = mutableListOf(mutableSetOf<Switch>())

    for (s in m.dropLast(1)) {
        if (s == "UPDATE_T_CONUP")
            ub.add(mutableSetOf())
        else {
            val switches = """_(\d+)""".toRegex().findAll(s).map { it.groupValues[1] }.map { it.toInt() }
            ub.last().addAll(switches)
        }
    }

    return ub
}
