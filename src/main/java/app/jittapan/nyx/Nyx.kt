package app.jittapan.nyx

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerBedEnterEvent
import org.bukkit.event.player.PlayerBedLeaveEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

/**
 * @author Jittapan P. [secretdataz]
 */
class Nyx: JavaPlugin(), Listener {
    var transitions = HashMap<UUID, Int?>()
    var suppressLeave = HashMap<UUID, Boolean>()

    companion object {
        var instance: Nyx? = null
        private set
    }

    override fun onEnable() {
        config.addDefault("percentNeeded", 50)
        config.addDefault("transitionTime", 100)
        // Messages taken from Morpheus mod
        config.addDefault("sleepMessage", "&6{player} is now sleeping. ({current}/{max})")
        config.addDefault("leaveBedMessage", "&6{player} has left their bed. ({current}/{max})")
        config.addDefault("morningMessage", "&6Wakey, wakey, rise and shine... Good Morning everyone!")
        config.options().copyDefaults(true)
        saveConfig()

        server.pluginManager.registerEvents(this, this)

        instance = this
    }

    private fun getSleepingCountInWorld(world: World): Int {
        return world.players.filter { it.hasPermission("nyx.voteright") && it.isSleeping() }.size
    }

    private fun setMorning(world: World) {
        world.time = 23450
        if(world.hasStorm())
            world.setStorm(false)
        if(world.isThundering)
            world.isThundering = true
    }

    private fun prepareMessage(format: String): String {
        return ChatColor.translateAlternateColorCodes('&', format)
    }

    private fun prepareMessage(format: String, player: Player, current: Int, max: Int): String {
        return ChatColor.translateAlternateColorCodes('&', format)
                .replace("{player}", player.name)
                .replace("{current}", current.toString())
                .replace("{max}", max.toString())
    }

    private fun broadcastToWorld(world: World, msg: String) {
        world.players.forEach {
            it.sendMessage(msg)
        }
    }

    private fun canProceedToNextDay(world: World, offset: Int = 0): Boolean {
        val sleeping = getSleepingCountInWorld(world) + offset
        val playersCount = world.players.size
        val percent = sleeping / playersCount.toDouble()

        return percent >= config.getDouble("percentNeeded") / 100
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerSleep(event: PlayerBedEnterEvent) {
        val player = event.player
        val world = event.bed.world
        if(player.hasPermission("nyx.voteright") && transitions[world.uid] == null) {
            val sleeping = getSleepingCountInWorld(world) + 1
            val playersCount = world.players.size

            broadcastToWorld(world, prepareMessage(config.getString("sleepMessage"), player, sleeping, playersCount))

            if(canProceedToNextDay(world, 1)) {
                transitions[world.uid] = Bukkit.getScheduler().scheduleSyncDelayedTask(this, {
                    if(canProceedToNextDay(world)) {
                        suppressLeave[world.uid] = true
                        setMorning(world)
                        broadcastToWorld(world, prepareMessage(config.getString("morningMessage")))
                        Bukkit.getScheduler().scheduleSyncDelayedTask(this, {
                            suppressLeave[world.uid] = false
                        }, 100L)
                    }
                    transitions[world.uid] = null
                }, config.getLong("transitionTime"))
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerLeaveBed(event: PlayerBedLeaveEvent) {
        val player = event.player
        val world = event.bed.world
        if(player.hasPermission("nyx.voteright") && suppressLeave[world.uid] != true) {
            val sleeping = getSleepingCountInWorld(world)
            val playersCount = world.players.size

            broadcastToWorld(world, prepareMessage(config.getString("leaveBedMessage"), player, sleeping, playersCount))

            if(!canProceedToNextDay(world)) {
                transitions[world.uid]?.let { Bukkit.getScheduler().cancelTask(it) }
                transitions[world.uid] = null
            }
        }
    }
}
