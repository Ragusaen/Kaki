package reduction

fun gcd(a: Int, b: Int): Int {
    if (b == 0) return a
    return gcd(b, a % b)
}

data class Rational(val _numerator: Int, val _denominator: Int) {
    val numerator: Int
    val denominator: Int

    init {
        gcd(_numerator, _denominator).let { gcd ->
            numerator = _numerator / gcd
            denominator = _denominator / gcd
        }
    }

    operator fun plus(other: Rational) = Rational(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)
    operator fun div(other: Int) = Rational(numerator, denominator * other)

    override fun toString() = "$numerator/$denominator"
}