package dev.munky.kozers.manager

import dev.munky.kozers.plugin
import dev.munky.kozers.util.Munky
import dev.munky.kozers.util.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.StringFormat
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

abstract class DataManager<T>(
    val fileName: String,
    val isDir: Boolean,
    val strat: () -> SerialFormat
) {
    protected val file = plugin.dataFolder.resolve(fileName)

    abstract val serializer: KSerializer<T>

    protected val e2file = HashMap<T, File>()

    suspend fun load() {
        if (file.exists()) check(file.isDirectory == isDir) {
            "Expected '${file.path}' to be a ${if (isDir) "directory" else "file"}, but it's not."
        }
        when {
            !file.exists() -> {
                withContext(Dispatchers.IO) {
                    if (isDir) {
                        file.mkdirs()
                        logger.info("Created new directory '${file.path}'.")
                    } else {
                        file.parentFile.mkdirs()
                        file.createNewFile()
                        logger.info("Created new file '${file.path}'.")
                    }
                }
                return
            }
            !isDir && file.length() == 0L -> {
                logger.warn("File '${file.path}' is empty.")
                return
            }
            isDir && file.list()?.isEmpty() == true -> {
                logger.warn("Directory '${file.path}' is empty.")
                return
            }
        }
        logger.info("Loading data from '${file.path}'.")
        val files = file.walk().filter { it.isFile }.toList()
        e2file.clear()
        coroutineScope {
            val strat = strat()
            for (file in files) launch(Dispatchers.Munky) {
                try {
                    if (file.length() == 0L) {
                        logger.warn("File '${file.path}' is empty.")
                        return@launch
                    }
                    logger.info("Reading file '${file.path}' in '${strat::class.simpleName}' format.")
                    file.inputStream().use {
                        when (strat) {
                            is BinaryFormat -> strat.decodeFromByteArray(serializer, it.readAllBytes())
                            is StringFormat -> strat.decodeFromString(
                                serializer,
                                it.readAllBytes().toString(Charsets.UTF_8)
                            )

                            else -> error("Unhandled serial format (strat) '${strat::class.qualifiedName}'.")
                        }
                    }.also(::onDecode).let { e2file[it] = file }
                } catch (t: Throwable) {
                    logger.error("Exception thrown while reading file ${file.absoluteFile}", t)
                }
            }
        }
    }

    suspend fun save() {
        withContext(Dispatchers.IO) {
            if (!file.exists()) {
                if (isDir) {
                    file.mkdirs()
                    logger.info("Created new directory '${file.path}'.")
                } else {
                    file.parentFile.mkdirs()
                    file.createNewFile()
                    logger.info("Created new file '${file.path}'.")
                }
            }
        }

        logger.info("Saving data to '${file.path}'.")

        coroutineScope {
            for ((e, file) in e2file) launch(Dispatchers.Munky) {
                file.outputStream().use {
                    try {
                        val data = when (val f = strat()) {
                            is BinaryFormat -> f.encodeToByteArray(serializer, e)
                            is StringFormat -> f.encodeToString(serializer, e)
                            else -> error("Unhandled serial format (strat) '${f::class.qualifiedName}'.")
                        }
                        it.write(
                            when (data) {
                                is String -> data.toByteArray(Charsets.UTF_8)
                                is ByteArray -> data
                                else -> error("Impossible")
                            }
                        )
                        logger.info("Saved ${e!!::class.simpleName} to '${file.path}'.")
                    } catch (t: Throwable) {
                        logger.error("Exception thrown while saving file ${file.absoluteFile}", t)
                    }
                }
            }
        }
    }

    open fun onDecode(value: T) {}

    companion object {
        private val logger = logger<DataManager<*>>()
    }
}