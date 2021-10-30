import com.soywiz.korim.bitmap.*
import com.soywiz.korim.format.*
import com.soywiz.korio.*
import com.soywiz.korio.file.*
import com.soywiz.korio.file.std.*
import kotlinx.coroutines.flow.*
import kotlin.jvm.*
import kotlin.system.*

object CheckReferences {
    @JvmStatic
    fun main(args: Array<String>) = Korio {
        val references = localCurrentDirVfs["references"]
        var notSimilarCount = 0
        for (kind in listOf("jvm", "mingwx64", "linuxx64", "macosx64")) {
            val generatedVfs = localCurrentDirVfs["build/screenshots/$kind"]
            val exists = generatedVfs.exists()
            println("generatedVfs=$generatedVfs . exists=${exists}")

            data class Result(val similar: Boolean, val equals: Boolean, val psnr: Double)

            if (exists) {
                references.list().filter { it.extensionLC == "png" && !it.baseName.endsWith(".alt.png") }.collect { file ->
                    val files = listOf(file, file.withExtension("alt.png"))
                    val otherFile = generatedVfs[file.baseName]
                    //println(otherFilesUnfiltered)
                    println("Comparing ${otherFile.absolutePath} <-> ${files.map { it.absolutePath }}")
                    val bitmap1List = files.map { it.readBitmap().toBMP32() }
                    val bitmap2 = otherFile.readBitmap().toBMP32()

                    val results = bitmap1List.map { bitmap1 ->
                        val similar = Bitmap32.matches(bitmap1, bitmap2, threshold = 32)
                        val equals = bitmap1.contentEquals(bitmap2)
                        val psnr = Bitmap32.computePsnr(bitmap1, bitmap2)
                        Result(similar, equals, psnr)
                    }
                    println(" --> equals=${results.map { it.equals }}, similar=${results.map { it.similar }}, psnr=${results.map { it.psnr }}")
                    if (!results.any { it.similar }) notSimilarCount++
                }
            }
        }

        println("Exiting with... exitCode=$notSimilarCount")
        exitProcess(notSimilarCount)
    }
}
