package korlibs.ffi

import korlibs.io.file.sync.*
import korlibs.platform.Platform

open class LibraryResolver(val fs: SyncIO, val platform: Platform) {
    val ldLibraries by lazy { LDLibraries(fs) }

    @SyncIOAPI
    companion object : LibraryResolver(platformSyncIOCaseInsensitive, Platform)

    fun resolve(vararg names: String): String? = names.firstNotNullOfOrNull { resolve(it) }

    fun resolve(name: String): String? {
        // @TODO: Search in LDLibraries, search frameworks, append .dll, prepend lib, etc.
        if (name.endsWith(".dylib") || name.endsWith(".so") || name.endsWith(".dll")) {
            return name
        }

        return when {
            platform.isMac -> {
                val bases = listOf("/Library/Frameworks", "/System/Library/Frameworks")
                bases.firstNotNullOfOrNull {
                    val base = "$it/$name.framework"
                    if (fs.file(base).isDirectory) {
                        "$base/$name"
                    } else {
                        null
                    }
                }
            }

            platform.isWindows -> {
                listOf(name, "$name.dll", "../$name.dll", "../../$name.dll", "../../../$name.dll", "C:/WINDOWS/SYSTEM32/$name.dll")
                    .map { fs.file(it) }
                    .filter {
                        //println("$it exists: ${it.exists()}")
                        it.exists()
                    }
                    .firstNotNullOfOrNull { it.fullPath }
            }

            else -> {
                //println("ldLibraries.ldFolders=${ldLibraries.ldFolders}")
                val exactFiles = ldLibraries.ldFolders.asSequence().mapNotNull { folder ->
                    val tries = listOf("lib$name.so", name)
                    tries.firstNotNullOfOrNull { folder[it].takeIf { it.exists() } }?.fullPath
                } + ldLibraries.ldFolders.asSequence().mapNotNull { folder ->
                    //println(folder.list())
                    folder.list().firstOrNull {
                        it.name.startsWith("lib$name") || it.name.startsWith(name)
                    }?.fullPath
                }
                //println("ldLibraries.ldFolders.exactFiles=${exactFiles}")

                exactFiles.firstOrNull {
                    if (Platform.arch.is64Bits) {
                        if (it.contains("i386")) return@firstOrNull false
                    }
                    true
                }
            }
        }
    }
}
