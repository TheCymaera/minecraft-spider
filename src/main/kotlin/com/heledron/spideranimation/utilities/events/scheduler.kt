package com.heledron.spideranimation.utilities.events

import com.heledron.spideranimation.utilities.currentPlugin
import org.bukkit.scheduler.BukkitTask
import java.io.Closeable

fun runLater(delay: Long, task: () -> Unit): Closeable {
    val plugin = currentPlugin
    val handler = plugin.server.scheduler.runTaskLater(plugin, task, delay)
    return Closeable {
        handler.cancel()
    }
}

fun interval(delay: Long, period: Long, task: (it: Closeable) -> Unit): Closeable {
    val plugin = currentPlugin
    lateinit var handler: BukkitTask
    val closeable = Closeable { handler.cancel() }
    handler = plugin.server.scheduler.runTaskTimer(plugin, Runnable { task(closeable) }, delay, period)
    return closeable
}

fun onTick(task: (it: Closeable) -> Unit) = TickSchedule.schedule(TickSchedule.main, task)
fun onTickEnd(task: (it: Closeable) -> Unit) = TickSchedule.schedule(TickSchedule.end, task)




private object TickSchedule {
    val main = mutableListOf<() -> Unit>()
    val end = mutableListOf<() -> Unit>()

    fun schedule(list: MutableList<() -> Unit>, task: (it: Closeable) -> Unit): Closeable {
        lateinit var closeable: Closeable

        val handler = { task(closeable) }
        closeable = Closeable { list.remove(handler) }

        list.add(handler)

        return closeable
    }


    init {
        interval(0,1) {
            main.toList().forEach { it() }
            end.toList().forEach { it() }
        }
    }
}