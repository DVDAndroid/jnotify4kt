package com.dvd.kotlin.jnotify4kt

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.receiveOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import java.io.File
import java.nio.file.Files

@ExperimentalCoroutinesApi
class KNotifyTest {

    private val testPath = File("./src/test/resources")

    @Test
    fun init(): Unit = runBlocking {
        testPath.asKNotify().apply {
            receive().apply {
                println(this)
                assert(this.file.absolutePath == testPath.absolutePath)
                assert(this.kind == FileEvent.Kind.Init)
                assert(this.name == null)
            }
            close()
        }
    }

    @Test
    fun createFile(): Unit = runBlocking {
        testPath.asKNotify().apply {
            receiveOrNull() // init

            val newFile = File(testPath, "test.txt").apply {
                createNewFile()
            }

            receive().apply {
                println(this)
                assert(this.file.absolutePath == newFile.absolutePath)
                assert(this.kind == FileEvent.Kind.Created)
                assert(this.name == "test.txt")
            }
            close()
        }
    }

    @Test
    fun editFile(): Unit = runBlocking {
        val newFile = File(testPath, "test.txt").apply {
            createNewFile()
        }

        testPath.asKNotify().apply {
            receiveOrNull() // init

            newFile.writeText("hi")
            delay(100)

            receive().apply {
                println(this)
                assert(this.file.absolutePath == newFile.absolutePath)
                assert(this.kind == FileEvent.Kind.Modified)
                assert(this.name == "test.txt")
            }
            close()
        }
    }

    @Test
    fun renameFile(): Unit = runBlocking {
        val newFile = File(testPath, "test.txt").apply {
            createNewFile()
        }

        testPath.asKNotify().apply {
            receiveOrNull() // init

            newFile.renameTo(File(testPath, "new.txt"))

            receive().apply {
                println(this)
                assert(this.file.absolutePath == File(testPath, "new.txt").absolutePath)
                assert(this.kind == FileEvent.Kind.Renamed)
                assert(this.oldName != null)
                assert(this.oldName == "test.txt")
                assert(this.name == "new.txt")
            }
            close()
        }
    }

    @Test
    fun deleteFile(): Unit = runBlocking {
        val newFile = File(testPath, "test.txt").apply {
            createNewFile()
        }

        testPath.asKNotify().apply {
            receiveOrNull() // init

            Files.delete(newFile.toPath())
            delay(200)

            receive().apply {
                println(this)
                assert(this.file.absolutePath == newFile.absolutePath)
                assert(this.kind == FileEvent.Kind.Deleted)
                assert(this.name == "test.txt")
            }
            close()
        }
    }

    @After
    fun cleanup() {
        File(testPath, "test.txt").delete()
        File(testPath, "new.txt").delete()
    }

}