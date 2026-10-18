package io.github.arcaneplugins.levelledmobs.wrappers

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.scheduler.BukkitTask

/**
 * This class is used for when code needs to be executed in a specific thread context.
 * It allows for Folia servers to have code executed in the correct scheduler while
 * providing compatibility for Paper / Spigot servers without needing to use
 * different methods per server type
 *
 * @author stumper66
 * @since 3.11.0
 */
class SchedulerWrapper {
    var runnable: Runnable? = null
    var entity: Entity? = null
    var bukkitTask: BukkitTask? = null
        private set

    constructor(runnable: Runnable){
        this.runnable = runnable
    }

    constructor(entity: Entity?){
        this.entity = entity
    }

    constructor(entity: Entity?, runnable: Runnable){
        this.entity = entity
        this.runnable = runnable
    }

    var locationForRegionScheduler: Location? = null
    var retiredCallback: Runnable? = null
    var runDirectlyInFolia: Boolean = false
    var runDirectlyInBukkit: Boolean = false
    val main: LevelledMobs = LevelledMobs.instance

    fun runAsync() {
        run(true)
    }

    private fun canRunDirectlyOnFolia(): Boolean {
        if (!runDirectlyInFolia) return false
        if (entity != null) {
            return Bukkit.isOwnedByCurrentRegion(entity!!)
        }
        if (locationForRegionScheduler != null) {
            return Bukkit.isOwnedByCurrentRegion(locationForRegionScheduler!!)
        }
        return false
    }

    fun run(doRunAsync: Boolean = false) {
        if (main.ver.isRunningFolia) {
            if (canRunDirectlyOnFolia()) {
                runnable?.run()
                return
            }

            val task = Consumer { _: ScheduledTask -> runnable?.run() }

            if (entity != null) {
                val scheduled = entity!!.scheduler.run(main, task, retiredCallback)
                if (scheduled == null) {
                    retiredCallback?.run()
                }
            } else if (locationForRegionScheduler != null && !doRunAsync) {
                Bukkit.getRegionScheduler().run(main, locationForRegionScheduler!!, task)
            } else {
                Bukkit.getAsyncScheduler().runNow(main, task)
            }
        } else {
            if (runDirectlyInBukkit) {
                runnable?.run()
                return
            }

            // if you provided an entity in the constructor, it is assumed the main thread needs to be used
            // since accessing entities asynchronously will usually result in an error
            bukkitTask = if (entity != null && !doRunAsync)
                Bukkit.getScheduler().runTask(main, runnable!!)
            else
                Bukkit.getScheduler().runTaskAsynchronously(main, runnable!!)
        }
    }

    fun runTaskTimerAsynchronously(
        initialDelayMS: Long,
        repeatPeriodMS: Long
    ): SchedulerResult {
        if (main.ver.isRunningFolia) {
            val task = Consumer { _: ScheduledTask -> runnable?.run() }
            val safeInitialDelay = initialDelayMS.coerceAtLeast(1L)
            val safeRepeatPeriod = repeatPeriodMS.coerceAtLeast(1L)
            val scheduledTask = Bukkit.getAsyncScheduler().runAtFixedRate(
                main, task, safeInitialDelay, safeRepeatPeriod, TimeUnit.MILLISECONDS
            )

            return SchedulerResult(scheduledTask)
        } else {
            // convert milliseconds into approximent ticks
            // 1 tick = ~ 50ms
            val convertedDelay = initialDelayMS / 50L
            val convertedPeriod = (repeatPeriodMS / 50L).coerceAtLeast(1L)
            val bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                main, runnable!!, convertedDelay, convertedPeriod
            )

            return SchedulerResult(bukkitTask)
        }
    }

    fun runDelayed(
        delayInTicks: Long
    ): SchedulerResult {
        if (main.ver.isRunningFolia) {
            val task = Consumer { _: ScheduledTask? -> runnable?.run() }
            val safeDelayTicks = delayInTicks.coerceAtLeast(1L)
            val scheduledTask: ScheduledTask?
            if (this.entity != null) {
                scheduledTask = entity!!.scheduler.runDelayed(main, task, retiredCallback, safeDelayTicks)
                if (scheduledTask == null) {
                    retiredCallback?.run()
                }
            } else if (locationForRegionScheduler != null) {
                scheduledTask = Bukkit.getRegionScheduler().runDelayed(
                    main, locationForRegionScheduler!!, task, safeDelayTicks
                )
            } else {
                val milliseconds = safeDelayTicks * 50L
                scheduledTask = Bukkit.getAsyncScheduler().runDelayed(
                    main, task, milliseconds, TimeUnit.MILLISECONDS
                )
            }

            return SchedulerResult(scheduledTask)
        } else {
            val bukkitTask = Bukkit.getScheduler().runTaskLater(
                main,
                runnable!!, delayInTicks
            )

            return SchedulerResult(bukkitTask)
        }
    }

    val willRunDirectly: Boolean
        get() {
            return if (main.ver.isRunningFolia)
                canRunDirectlyOnFolia()
            else
                runDirectlyInBukkit
        }
}