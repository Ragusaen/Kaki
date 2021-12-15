import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Renderer
import guru.nidi.graphviz.graph
import guru.nidi.graphviz.toGraphviz

fun outputPrettyNetwork(usm: UpdateSynthesisModel): Renderer {
    val g = graph(directed = true) {
        usm.reachability.initialNode.toString().get().attrs().add("shape","hexagon")
        usm.reachability.finalNode.toString().get().attrs().add("shape","doublecircle")
        usm.waypoint.waypoints.forEach { it.toString().get().attrs().add("shape", "house") }
        if (usm.conditionalEnforcements != null) {
            for (c in usm.conditionalEnforcements) {
                c.s.toString().get().attrs().add("shape", "rarrow")
                c.sPrime.toString().get().attrs().add("shape", "box")
            }
        }

        for ((from, to) in usm.initialRouting) {
            (from.toString() - to.toString()).attrs().add("color","blue")
        }
        for ((from, to) in usm.finalRouting) {
            (from.toString() - to.toString()).attrs().add("color","red")
        }
    }

    return g.toGraphviz().render(Format.SVG)
}
