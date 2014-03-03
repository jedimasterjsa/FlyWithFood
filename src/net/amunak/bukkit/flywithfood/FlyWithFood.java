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
    private ConfigAccessor languageAccessor;
    public FileConfiguration languageFile;
    public HashMap<Player, FlyablePlayerRecord> flyablePlayers = new HashMap<>();
    public List<String> flyControlCommands = new ArrayList<>();
    public List<String> listOfFood;
    public List<String> listOfForbiddenFood;
    private long hungerDrainInterval;
    public int hungerMin;
    public int hungerLiftoffMin;
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
            this.checkFlyingCapability(p, true);
        }

        this.saveDefaultConfig();
        this.getConfig().options().copyDefaults(true);
        this.reloadConfig();

        //load language configuration
        this.languageAccessor = new ConfigAccessor(this, "localization.yml");
        this.languageAccessor.reloadConfig();
        this.languageAccessor.saveDefaultConfig();
        this.languageFile = this.languageAccessor.getConfig();

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
        //reload language configuration first
        this.languageFile = this.languageAccessor.getConfig();

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

            if (this.config.getInt("options.drainHunger.liftOffMin") < 0 || this.config.getInt("options.drainHunger.liftOffMin") > 20) {
                log.warning("Configuration error: minial food level for lift off invalid, using default (10)");
                this.hungerLiftoffMin = 10;
            } else {
                this.hungerLiftoffMin = this.config.getInt("options.drainHunger.liftOffMin");
            }

            if (this.config.getInt("options.drainHunger.min") < 0 || this.config.getInt("options.drainHunger.min") > 20) {
                log.warning("Configuration error: minial food level for flying invalid, using default (5)");
                this.hungerMin = 5;
            } else {
                this.hungerMin = this.config.getInt("options.drainHunger.min");
            }

            if (hungerLiftoffMin < hungerMin) {
                hungerLiftoffMin = hungerMin;
                log.warning("Configuration error: minimal lift off food level is smaller than minimal food level for flying, setting lift off minimum to " + hungerLiftoffMin);
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

        log.fine("reloaded config");
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

    public void checkFlyingCapability(Player player, Boolean delayed) {
        if (delayed) {
            this.getServer().getScheduler().scheduleSyncDelayedTask(this, new FlyWithFood.DelayedFlyingCapabilityCheck(player.getName(), this), 4);
        } else {
            this.checkFlyingCapability(player);
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
                    for (int i = 0; i < 4; i++) {
                        this.getServer().getScheduler().runTaskLater(this, new FlyWithFood.ScheduleWarningsound(pName, i), (long) (i * 4));
                    }
                }
                if (this.config.getBoolean("options.drainHunger.warning.enableText")) {
                    p.sendMessage(ChatColor.BLUE + "Your food level is too low. You may soon fall down.");
                }
            }
        }
    }

    void fixDataInconsistency(Player p) {
        this.getServer().getScheduler().runTaskLater(this, new FixDataInconsistency(p), 1L);
    }

    private static class OnRemoveHunger implements Runnable {

        protected FlyWithFood plugin;

        public OnRemoveHunger(FlyWithFood plugin) {
            this.plugin = plugin;
        }

        @Override
        public void run() {
            for (Player player : this.plugin.getServer().getOnlinePlayers()) {
                //Remove hunger/saturation if player is in survival, flying and has no bypass
                if (player.getGameMode().equals(GameMode.SURVIVAL)
                        && player.isFlying()
                        && !player.hasPermission("fly.nohunger")) {
                    //remoce from saturation first if we can, then do food level
                    if (player.getSaturation() > 0) {
												int newSaturationLevel = player.getSaturation() - this.plugin.config.getInt("options.drainHunger.rate");
												if (newSaturationLevel < 0) {
														newSaturationLevel = 0;
												}
												if (newSaturationLevel > 20) {
														newSaturationLevel = 20;
												}
												player.setSaturation(newSaturationLevel);
                    		log.fine(player.getName() + " now has saturation of " + newSaturationLevel);
                    }
                    else {
												int newFoodLevel = player.getFoodLevel() - this.plugin.config.getInt("options.drainHunger.rate");
												if (newFoodLevel < 0) {
														newFoodLevel = 0;
												}
												if (newFoodLevel > 20) {
														newFoodLevel = 20;
												}
                    		player.setFoodLevel(newFoodLevel);
                    		log.fine(player.getName() + " now has foodLevel of " + newFoodLevel);
                    }
                    this.plugin.foodLevelCheck(player, newFoodLevel);
                }
            }
        }
    }

    private class DelayedFlyingCapabilityCheck implements Runnable {

        private final String pName;
        protected FlyWithFood plugin;

        public DelayedFlyingCapabilityCheck(String pName, FlyWithFood plugin) {
            this.pName = pName;
            this.plugin = plugin;
        }

        @Override
        public void run() {
            Player p = getServer().getPlayer(this.pName);
            log.fine("running delayed check on " + pName);
            if (p != null) {
                this.plugin.checkFlyingCapability(p);
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
                p.playSound(p.getLocation(), Sound.ORB_PICKUP, 2F, 2F - (this.soundNr / 10F));
            }
        }
    }

    private static class FixDataInconsistency implements Runnable {

        private final Player p;

        public FixDataInconsistency(Player p) {
            this.p = p;
        }

        @Override
        public void run() {
            p.updateInventory();
            p.setFoodLevel(p.getFoodLevel());
        }
    }
}