package io.github.arcaneplugins.levelledmobs.listeners

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockDispenseLootEvent
import org.bukkit.event.world.LootGenerateEvent

class LootListener : Listener {

    private fun getExcludedItems(): Set<String> {
        val list = LevelledMobs.instance.helperSettings.getStringList("levelled-loot-excluded-items")
        return list.map { it.trim().lowercase() }.toSet()
    }

    private fun isLootEnabled(): Boolean {
        return LevelledMobs.instance.helperSettings.getBoolean("enable-levelled-loot", true)
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onLootGenerate(event: LootGenerateEvent) {
        if (!isLootEnabled()) return
        if (event.isPlugin) return

        // プレイヤーによる直接開封のみを対象とする (ホッパー・自動化、およびモブドロップは除外)
        val player = event.entity as? Player ?: return

        val location = event.lootContext.location

        val main = LevelledMobs.instance
        val applicableRules = main.levelManager.getApplicableRulesForLocation(location)
        val multiplier = main.levelManager.calculateLootMultiplier(location, applicableRules, false)
        if (multiplier <= 0.001f) return

        val maxSlots = event.inventoryHolder?.inventory?.size ?: 27
        val excludedItems = getExcludedItems()

        main.levelManager.processLevelledLoot(event.loot, multiplier, maxSlots, excludedItems)
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onBlockDispenseLoot(event: BlockDispenseLootEvent) {
        if (!isLootEnabled()) return

        // プレイヤーによる解錠のみを対象とする
        val player = event.player ?: return
        val location = event.block.location

        val main = LevelledMobs.instance
        val applicableRules = main.levelManager.getApplicableRulesForLocation(location)
        val multiplier = main.levelManager.calculateLootMultiplier(location, applicableRules, true)
        if (multiplier <= 0.001f) return

        val dispensed = event.dispensedLoot.toMutableList()
        val excludedItems = getExcludedItems()

        main.levelManager.processLevelledLoot(dispensed, multiplier, 54, excludedItems)
        event.setDispensedLoot(dispensed)
    }
}
