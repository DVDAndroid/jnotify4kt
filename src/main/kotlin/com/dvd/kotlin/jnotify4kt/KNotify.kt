package com.dvd.kotlin.jnotify4kt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import net.contentobjects.jnotify.JNotify
import net.contentobjects.jnotify.JNotifyListener
import java.io.File

fun File.asKNotify(
    mask: Int = JNotify.FILE_ANY,
    watchSubtree: Boolean = true,
    ignoreWinTempFiles: Boolean = true,
    scope: CoroutineScope = GlobalScope
) = KNotify(
    path = this.absolutePath,
    mask,
    watchSubtree,
    ignoreWinTempFiles,
    scope
)

data class FileEvent(
    val file: File,
    val kind: Kind,
    val oldName: String? = null,
    val name: String? = null,
) {

    fun whenEvent(
        onInitialized: (File) -> Unit = { _ -> (Unit) },
        onCreated: (File, String) -> Unit = { _, _ -> (Unit) },
        onRenamed: (File, String, String) -> Unit = { _, _, _ -> (Unit) },
        onModified: (File, String) -> Unit = { _, _ -> (Unit) },
        onDeleted: (File, String) -> Unit = { _, _ -> (Unit) },
    ) = when (kind) {
        Kind.Init -> onInitialized(file)
        Kind.Created -> onCreated(file, name!!)
        Kind.Renamed -> onRenamed(file, oldName!!, name!!)
        Kind.Modified -> onModified(file, name!!)
        Kind.Deleted -> onDeleted(file, name!!)
    }

    fun whenEvent(event: FileEventChange): Unit = when (kind) {
        Kind.Init -> event.onInitialized(file)
        Kind.Created -> event.onCreated(file, name!!)
        Kind.Renamed -> event.onRenamed(file, oldName!!, name!!)
        Kind.Modified -> event.onModified(file, name!!)
        Kind.Deleted -> event.onDeleted(file, name!!)
    }

    interface FileEventChange {
        fun onInitialized(file: File) {}
        fun onCreated(file: File, name: String)
        fun onModified(file: File, name: String)
        fun onRenamed(file: File, oldName: String, name: String)
        fun onDeleted(file: File, name: String)
    }

    enum class Kind {
        Init,
        Created,
        Renamed,
        Modified,
        Deleted
    }
}

class KNotify(
    val path: String,
    private val mask: Int = JNotify.FILE_ANY,
    private val watchSubtree: Boolean = true,
    val ignoreWinTempFiles: Boolean = true,
    val scope: CoroutineScope = GlobalScope,
    private val channel: Channel<FileEvent> = Channel()
) : Channel<FileEvent> by channel {

    var watchId: Int = -1
        private set

    init {
        scope.launch(Dispatchers.IO) {
            try {
                watchId = JNotify.addWatch(path, mask, watchSubtree, object : JNotifyListener {
                    override fun fileCreated(wd: Int, rootPath: String, name: String) {
                        if (ignoreWinTempFiles && (name.startsWith("~") || name.endsWith("~"))) return

                        scope.launch {
                            channel.send(FileEvent(File(rootPath, name), FileEvent.Kind.Created, oldName = null, name))
                        }
                    }

                    override fun fileDeleted(wd: Int, rootPath: String, name: String) {
                        if (ignoreWinTempFiles && (name.startsWith("~") || name.endsWith("~"))) return

                        scope.launch {
                            channel.send(FileEvent(File(rootPath, name), FileEvent.Kind.Deleted, oldName = null, name))
                        }
                    }

                    override fun fileModified(wd: Int, rootPath: String, name: String) {
                        if (ignoreWinTempFiles && (name.startsWith("~") || name.endsWith("~"))) return

                        val file = File(rootPath, name)
                        if (file.exists().not()) return

                        scope.launch {
                            channel.send(FileEvent(file, FileEvent.Kind.Modified, oldName = null, name))
                        }
                    }

                    override fun fileRenamed(wd: Int, rootPath: String, oldName: String, newName: String) {
                        if (ignoreWinTempFiles) {
                            if (oldName.startsWith("~") || oldName.endsWith("~")) return
                            if (newName.startsWith("~") || newName.endsWith("~")) return
                        }

                        scope.launch {
                            channel.send(FileEvent(File(rootPath, newName), FileEvent.Kind.Renamed, oldName, newName))
                        }
                    }
                })
                channel.send(FileEvent(File(path), FileEvent.Kind.Init))
            } catch (e: Throwable) {
                e.printStackTrace()
                close()
            }
        }
    }

    override fun close(cause: Throwable?): Boolean {
        if (watchId != -1)
            JNotify.removeWatch(watchId)

        return channel.close()
    }
}