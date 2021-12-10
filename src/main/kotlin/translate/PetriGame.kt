class PetriGame(
    val places: Set<Place>,
    val transitions: Set<Transition>,
    val arcs: Set<Arc>
    )

var nextId = 0
    get() = field++

open class Node(val name: String, var pos: Pair<Int, Int> = Pair(0, 0))

class Place(val initialTokens: Int, name: String) : Node(name) {
    val id: Int = nextId

    override fun equals(other: Any?): Boolean {
        return other is Place
                && other.name == this.name
    }

    override fun hashCode(): Int {
        var result = initialTokens
        result = 31 * result + name.hashCode()
        return result
    }
}

class Transition(val controllable: Boolean, name: String) : Node(name) {
    val id: Int = nextId

    override fun equals(other: Any?): Boolean {
        return other is Transition
                && other.name == this.name
                && other.controllable == this.controllable
    }

    override fun hashCode(): Int {
        var result = controllable.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}

data class Arc(val source: Node, val target: Node, val weight: Int = 1) {
    val name: String = nextId.toString()

    override fun equals(other: Any?): Boolean {
        return other is Arc
                && other.source.name == this.source.name
                && other.target.name == this.target.name
    }

    override fun hashCode(): Int {
        var result = source.name.hashCode()
        result = 31 * result + target.name.hashCode()
        return result
    }
}

