package net.amunak.bukkit.flywithfood;

/**
 * Copyright 2013 Jiří Barouš
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

public class FlyWithFood extends JavaPlugin {

    protected static Log log;
    public PluginDescriptionFile pdfFile;
    public FileConfiguration config;
    public HashMap<Player, FlyablePlayerRecord> flyablePlayers = new HashMap<>();
    public List<String> flyControlCommands = new ArrayList<>();
    public List<String> listOfFood;
    public List<String> listOfForbiddenFood;
    private long hungerDrainInterval;
    public int hungerMin;
    public int hungerWarning;
    private int hungerTaskId = -1;
    public int maxFoodEaten;

    @Override
    public void onEnable() {
        log = new Log(this);

        this.pdfFile = this.getDescription();

        this.flyControlCommands.add("toggle");
        this.flyControlCommands.add("on");
        this.flyControlCommands.add("off");

        for (Player p : this.getServer().getOnlinePlayers()) {
            this.flyablePlayers.put(p, new FlyablePlayerRecord());
            this.checkFlyingCapability(p);
        }

        this.saveDefaultConfig();
        this.getConfig().options().copyDefaults(true);
        this.reloadConfig();

        if (this.config.getBoolean("options.general.checkVersion")) {
            CheckVersion.check(this);
        }

        //Register events and command listener
        this.getCommand("fly").setExecutor(new FlyWithFoodCommandExecutor(this));
        this.getCommand("fwf").setExecutor(new FlyWithFoodCommandExecutor(this));
        this.getCommand("flywithfood").setExecutor(new FlyWithFoodCommandExecutor(this));
        this.getServer().getPluginManager().registerEvents(new FlyWithFoodEventListener(this), this);
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        this.config = this.getConfig();
        if (this.config.getBoolean("options.drainHunger.enable")) {
            if (this.config.getLong("options.drainHunger.interval") < 1) {
                log.warning("Configuration error: drain hunger interval invalid, using default (100 ticks)");
                this.hungerDrainInterval = 100L;
            } else {
                this.hungerDrainInterval = this.config.getLong("options.drainHunger.interval");
            }
            this.restartHungerRemoveScheduler();

            if (this.config.getInt("options.drainHunger.min") < 0 || this.config.getInt("options.drainHunger.min") > 20) {
                log.warning("Configuration error: minial food level for flying invalid, using default (5)");
                this.hungerMin = 5;
            } else {
                this.hungerMin = this.config.getInt("options.drainHunger.min");
            }

            if (this.config.getBoolean("options.drainHunger.warning.enableText") || this.config.getBoolean("options.drainHunger.warning.enableSound")) {
                if (this.config.getInt("options.drainHunger.warning.min") < 0 || this.config.getInt("options.drainHunger.warning.min") > 20) {
                    log.warning("Configuration error: minial food level for flying invalid, using default (6)");
                    this.hungerWarning = 6;
                } else {
                    this.hungerWarning = this.config.getInt("options.drainHunger.warning.min");
                }
                log.fine("hungerWarning: " + this.hungerWarning);
            }
        }
        if (this.config.getBoolean("options.limitFoodConsumption.enable")) {
            if (this.config.getInt("options.limitFoodConsumption.max") < 1) {
                log.warning("Configuration error: maximum food eaten in flight too low, using no limit (-1)");
                this.maxFoodEaten = -1;
            } else {
                this.maxFoodEaten = this.config.getInt("options.limitFoodConsumption.max");
            }
            log.fine("maxFoodEaten: " + this.maxFoodEaten);
        }

        log.raiseFineLevel = config.getBoolean("options.general.verboseLogging");

        this.listOfFood = config.getStringList("listOfFood");
        Common.fixEnumLists(this.listOfFood);
        this.listOfForbiddenFood = config.getStringList("options.limitFoodConsumption.listOfDisallowedFood");
        Common.fixEnumLists(this.listOfForbiddenFood);

        log.info("reloaded config");
    }

    public void setFlightPreference(Player p) {
        if (this.flyablePlayers.get(p).hasEnabledFlying) {
            setFlightPreference(p, false);
        } else {
            setFlightPreference(p, true);
        }
    }

    public void setFlightPreference(Player p, boolean b) {
        this.flyablePlayers.get(p).hasEnabledFlying = b;
        this.checkFlyingCapability(p);
    }

    public void checkFlyingCapability(Player player) {
        if (player.getGameMode().equals(GameMode.SURVIVAL)) {
            if (player.getAllowFlight()) {
                if (!player.hasPermission("fly.force") && !this.flyablePlayers.get(player).hasEnabledFlying) {
                    player.setAllowFlight(false);
                    player.setFlying(false);
                    player.sendMessage(ChatColor.BLUE + "You have lost the ability to fly!");
                }
            } else {
                if (player.hasPermission("fly.force") || this.flyablePlayers.get(player).hasEnabledFlying) {
                    player.setAllowFlight(true);
                    player.sendMessage(ChatColor.BLUE + "You have gained the ability to fly!");
                }
            }
        }
    }

    private void restartHungerRemoveScheduler() {
        if (this.hungerTaskId != -1) {
            this.getServer().getScheduler().cancelTask(this.hungerTaskId);
            this.hungerTaskId = -1;
        }
        if (this.config.getBoolean("options.drainHunger.enable")) {
            this.hungerTaskId = this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new OnRemoveHunger(this), 0L, this.hungerDrainInterval);
        }
    }

    void foodLevelCheck(Player p, int foodLevel) {
        if (p.getGameMode().equals(GameMode.SURVIVAL)
                && p.isFlying()
                && this.config.getBoolean("options.drainHunger.enable")
                && !p.hasPermission("fly.nohunger")) {
            if (foodLevel < this.hungerMin) {
                p.sendMessage(ChatColor.BLUE + "Your food level dropped under "
                        + ChatColor.DARK_PURPLE + ((double) this.hungerMin / 2)
                        + ChatColor.BLUE + ". You are too weak to fly.");
                p.setFlying(false);
            } else if (foodLevel <= this.hungerWarning) {
                if (this.config.getBoolean("options.drainHunger.warning.enableSound")) {
                    final String pName = p.getName();
                    for (int i = 0; i < 6; i++) {
                        this.getServer().getScheduler().runTaskLater(this, new FlyWithFood.ScheduleWarningsound(pName, i), (long) (i * 4));
                    }
                }
                if (this.config.getBoolean("options.drainHunger.warning.enableText")) {
                    p.sendMessage(ChatColor.BLUE + "Your food level is too low. You may soon fall down.");
                }
            }
        }
    }

    private static class OnRemoveHunger implements Runnable {

        protected FlyWithFood plugin;

        public OnRemoveHunger(FlyWithFood plugin) {
            this.plugin = plugin;
        }

        @Override
        public void run() {
            for (Player player : this.plugin.getServer().getOnlinePlayers()) {
                //Remove hunger if player is in survival, flying and has no bypass
                if (player.getGameMode().equals(GameMode.SURVIVAL)
                        && player.isFlying()
                        && !player.hasPermission("fly.nohunger")) {
                    int newFoodLevel = player.getFoodLevel() - this.plugin.config.getInt("options.drainHunger.rate");
                    if (newFoodLevel < 0) {
                        newFoodLevel = 0;
                    }
                    if (newFoodLevel > 20) {
                        newFoodLevel = 20;
                    }
                    player.setFoodLevel(newFoodLevel);
                    log.fine(player.getName() + " now has foodLevel of " + newFoodLevel);
                    this.plugin.foodLevelCheck(player, newFoodLevel);
                }
            }
        }
    }

    private class ScheduleWarningsound implements Runnable {

        private final String pName;
        private final float soundNr;

        public ScheduleWarningsound(String pName, int soundNr) {
            this.pName = pName;
            this.soundNr = (float) soundNr;
        }

        @Override
        public void run() {
            Player p = getServer().getPlayer(this.pName);
            log.fine("running sound for player " + pName);
            if (p != null) {
                p.playSound(p.getLocation(), Sound.NOTE_BASS_GUITAR, 3.0F, 3F - (this.soundNr / 8F));
            }
        }
    }
}