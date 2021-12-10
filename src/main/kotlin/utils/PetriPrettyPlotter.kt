import guru.nidi.graphviz.model.*
import guru.nidi.graphviz.*
import guru.nidi.graphviz.engine.Format
import kotlinx.serialization.json.*
import java.lang.Exception

// This function uses graph creating tool Graphviz to artificially add positional information to a petri game
fun addGraphicCoordinatesToPG(pg: PetriGame) {
    val nodes: MutableList<Node> = mutableListOf()
    val nameToNodeMap: MutableMap<String, Node> = mutableMapOf()

    // All places and transitions must be spaced out, we create a map by their name
    for (n: Node in pg.places + pg.transitions) {
        nodes.add(n)
        nameToNodeMap[n.name] = n
    }

    // Graphviz creates a graph and the arcs determine the spacing
    val graph: MutableGraph = graph(directed = true) {
        for (a: Arc in pg.arcs) {
            a.source.name - a.target.name
        }
    }

    // Name to graphical (x,y) position
    val nameToPos: MutableMap<String, Pair<Int, Int>> = mutableMapOf()

    // Render the Graphviz graph to json, in order to extract the positions of nodes
    val gv = graph.toGraphviz()
    val jsonText = gv.render(Format.JSON).toString()
    val jRoot = Json.parseToJsonElement(jsonText).jsonObject["objects"]

    // Extracting the position directly from the json node tree
    for (e in jRoot!!.jsonArray) {
        val name = e.jsonObject["name"]!!.jsonPrimitive.content
        val posX: Int =
            e.jsonObject["_ldraw_"]!!.jsonArray[2].jsonObject["pt"]!!.jsonArray[0].jsonPrimitive.double.toInt()
        val posY: Int =
            e.jsonObject["_ldraw_"]!!.jsonArray[2].jsonObject["pt"]!!.jsonArray[1].jsonPrimitive.double.toInt()
        nameToPos[name] = Pair(posX, posY)
    }

    // For each name and position tuple we add the graphics to the petri node, using the name to node map created initially
    for (nameAndPos in nameToPos) {
        val node = nameToNodeMap[nameAndPos.key]
        if (node != null) {
            node.pos = nameAndPos.value
        }
        else
            throw Exception("Positional graphics could not match name with any place or transition.")
    }
}
