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

    private var watchId: Int = -1

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

                        scope.launch {
                            channel.send(FileEvent(File(rootPath, name), FileEvent.Kind.Modified, oldName = null, name))
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