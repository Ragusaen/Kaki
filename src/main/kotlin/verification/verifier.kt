package verification

import Batch
import Options
import pcreateTempFile
import println
import java.io.File
import java.nio.file.Path
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

class VerifyPNError(msg: String) : Exception(msg)

class Verifier(val modelPath: File) {
    var lastVerificationTime = -1.0
    fun verifyQuery(queryPath: String): Pair<Boolean, String?> {
        val command =
            "${Options.enginePath.toAbsolutePath()} --strategy-output _ ${modelPath.absolutePath} $queryPath -q 0 -r 0 -s RDFS"
        if (Options.outputVerifyPN)
            v.High.println(command)
        val pro: Process
        pro = Runtime.getRuntime().exec(command)
        pro.waitFor()
        val output = String(pro.inputStream.readAllBytes())
        lastVerificationTime =
            """Time \(seconds\) *: (\d+\.\d+)""".toRegex().find(output)?.groupValues?.get(1)?.toDouble() ?: -1.0
        if (Options.outputVerifyPN)
            v.High.println(output)

        return if (output.contains("is satisfied")) {
            val from = output.lastIndexOf("##BEGIN STRATEGY##")
            val to = output.indexOf("##END STRATEGY##")
            Pair(true, output.substring(from, to))
        } else if (output.contains("is NOT satisfied"))
            Pair(false, null)
        else {
            val s = String(pro.errorStream.readAllBytes())
            System.err.println(s)
            throw VerifyPNError(s)
        }
    }
}

//Bisection search. k starts at 5
fun bisectionSearch(verifier: Verifier, queryPath: File, upperBound: Int, minBatches: Int): List<Batch>? {
    var query = queryPath.readText()
    var k = if (upperBound < 5) upperBound else 5
    var lower = if (Options.maxSwicthesInBatch != 0) upperBound / Options.maxSwicthesInBatch else 0
    lower = max(lower, minBatches)
    k = max(k, lower)
    var upper = upperBound
    val tempQueryFile = pcreateTempFile("query")
    var strategy: String? = null
    var lowestBatchNum = Int.MAX_VALUE

    var first = true
    while (k < lowestBatchNum) {
        query = query.replace("UPDATE_P_BATCHES <= [0-9]*".toRegex(), "UPDATE_P_BATCHES <= $k")
        tempQueryFile.writeText(query)
        val (vf, s) = verifier.verifyQuery(tempQueryFile.path)
        v.High.println("Verification ${if (vf) "succeeded" else "failed"} in ${verifier.lastVerificationTime} seconds with <= $k batches")

        if (vf) {
            strategy = s
            upper = k
            lowestBatchNum = k
            k = ceil((upper + lower) / 2.0).toInt()
        } else {
            lower = k

            if (first && k != upper) {
                // If the first verification fails, then check upper bound
                // this is to faster determine negative cases as larger solutions are very unlikely
                k = upper
            } else {
                //If verification failed for k=upper, then we are done
                if (k == upper) break
                k = ceil((upper + lower) / 2.0).toInt()
            }
        }

        first = false
    }

    return if (strategy != null) getUpdateBatchesFromStrategy(strategy) else null
}


fun sequentialSearch(verifier: Verifier, queryPath: File, upperBound: Int): List<Batch>? {
    var case: Int

    var verified: Boolean
    var strategy: String? = null
    var query = queryPath.readText()
    val tempQueryFile = pcreateTempFile("query")
    var time: Long

    // Test with max amount of batches
    query = query.replace("UPDATE_P_BATCHES <= [0-9]*".toRegex(), "UPDATE_P_BATCHES <= $upperBound")
    tempQueryFile.writeText(query)
    val (vf, s) = verifier.verifyQuery(tempQueryFile.path)
    verified = vf
    if (vf)
        strategy = s
    v.High.println("Verification ${if (verified) "succeeded" else "failed"} in ${verifier.lastVerificationTime} seconds with <= $upperBound batches")
    if (Options.maxSwicthesInBatch == 1) {
        v.Low.println("Subproblem verification ${if (verified) "succeeded" else "failed"} in ${verifier.lastVerificationTime} seconds.")
        return if (strategy != null) getUpdateBatchesFromStrategy(strategy) else null
    }

    if (verified) {
        if (upperBound > 5) {
            case = 5
        } else if (upperBound == 5) {
            case = 4
        } else {
            case = upperBound - 1
        }
        // Test with 5 or less
        while (case > 0) {
            query = query.replace("UPDATE_P_BATCHES <= [0-9]*".toRegex(), "UPDATE_P_BATCHES <= $case")

            tempQueryFile.writeText(query)

            run {
                val (v, s) = verifier.verifyQuery(tempQueryFile.path)
                verified = v
                if (v)
                    strategy = s
            }

            v.High.println("Verification ${if (verified) "succeeded" else "failed"} in ${verifier.lastVerificationTime} seconds with <= $case batches\n")

            if (verified) {
                case -= 1
            } else if (case == 5) {
                case = upperBound - 1
                break
            } else {
                break
            }
        }
        //Test sequentially down from max batches
        if (!verified) {
            while (case > 5) {
                query = query.replace("UPDATE_P_BATCHES <= [0-9]*".toRegex(), "UPDATE_P_BATCHES <= $case")

                tempQueryFile.writeText(query)

                time = measureTimeMillis {
                    val (v, s) = verifier.verifyQuery(tempQueryFile.path)
                    verified = v
                    if (v)
                        strategy = s
                }

                v.High.println("Verification ${if (verified) "succeeded" else "failed"} in ${time / 1000.0} seconds with <= $case batches")

                if (verified) {
                    case -= 1
                }
                if (!verified) {
                    break
                }
            }
        }
    }

    return if (strategy != null) getUpdateBatchesFromStrategy(strategy!!) else null
}