////////////////////////////////////////////////////////////////////////////////////////////////////
// PlotSquared - A plot manager and world generator for the Bukkit API                             /
// Copyright (c) 2014 IntellectualSites/IntellectualCrafters                                       /
//                                                                                                 /
// This program is free software; you can redistribute it and/or modify                            /
// it under the terms of the GNU General Public License as published by                            /
// the Free Software Foundation; either version 3 of the License, or                               /
// (at your option) any later version.                                                             /
//                                                                                                 /
// This program is distributed in the hope that it will be useful,                                 /
// but WITHOUT ANY WARRANTY; without even the implied warranty of                                  /
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                                   /
// GNU General Public License for more details.                                                    /
//                                                                                                 /
// You should have received a copy of the GNU General Public License                               /
// along with this program; if not, write to the Free Software Foundation,                         /
// Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA                               /
//                                                                                                 /
// You can contact us via: support@intellectualsites.com                                           /
////////////////////////////////////////////////////////////////////////////////////////////////////
package com.plotsquared.bukkit.database.plotme;

import com.intellectualcrafters.configuration.MemorySection;
import com.intellectualcrafters.configuration.file.FileConfiguration;
import com.intellectualcrafters.configuration.file.YamlConfiguration;
import com.intellectualcrafters.plot.PS;
import com.intellectualcrafters.plot.config.Settings;
import com.intellectualcrafters.plot.database.DBFunc;
import com.intellectualcrafters.plot.generator.HybridGen;
import com.intellectualcrafters.plot.object.Plot;
import com.intellectualcrafters.plot.object.PlotArea;
import com.intellectualcrafters.plot.object.PlotId;
import com.intellectualcrafters.plot.util.TaskManager;
import com.plotsquared.bukkit.generator.BukkitPlotGenerator;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class LikePlotMeConverter {

    private final String plugin;

    /**
     * Constructor.
     *
     * @param plugin Plugin Used to run the converter
     */
    public LikePlotMeConverter(String plugin) {
        this.plugin = plugin;
    }

    public static String getWorld(String world) {
        for (World newworld : Bukkit.getWorlds()) {
            if (newworld.getName().equalsIgnoreCase(world)) {
                return newworld.getName();
            }
        }
        return world;
    }

    private void sendMessage(String message) {
        PS.debug("&3PlotMe&8->&3PlotSquared&8: &7" + message);
    }

    public String getPlotMePath() {
        return new File(".").getAbsolutePath() + File.separator + "plugins" + File.separator + this.plugin + File.separator;
    }

    public String getAthionPlotsPath() {
        return new File(".").getAbsolutePath() + File.separator + "plugins" + File.separator + this.plugin + File.separator;
    }

    public FileConfiguration getPlotMeConfig(String dataFolder) {
        File plotMeFile = new File(dataFolder + "config.yml");
        if (!plotMeFile.exists()) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(plotMeFile);
    }

    public Set<String> getPlotMeWorlds(FileConfiguration plotConfig) {
        return plotConfig.getConfigurationSection("worlds").getKeys(false);
    }

    public void mergeWorldYml(String plugin, FileConfiguration plotConfig) {
        try {
            File genConfig =
                    new File("plugins" + File.separator + plugin + File.separator + "PlotMe-DefaultGenerator" + File.separator + "config.yml");
            if (genConfig.exists()) {
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(genConfig);
                for (String key : yml.getKeys(true)) {
                    if (!plotConfig.contains(key)) {
                        Object value = yml.get(key);
                        if (!(value instanceof MemorySection)) {
                            plotConfig.set(key, value);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateWorldYml(String plugin, String location) {
        try {
            Path path = Paths.get(location);
            File file = new File(location);
            if (!file.exists()) {
                return;
            }
            String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            content = content.replaceAll("PlotMe-DefaultGenerator", "PlotSquared");
            content = content.replaceAll(plugin, "PlotSquared");
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            //ignored
        }
    }

    public boolean run(APlotMeConnector connector) {
        try {
            String dataFolder = getPlotMePath();
            FileConfiguration plotConfig = getPlotMeConfig(dataFolder);
            if (plotConfig == null) {
                return false;
            }

            String version = plotConfig.getString("Version");
            if (version == null) {
                version = plotConfig.getString("version");
            }
            if (!connector.accepts(version)) {
                return false;
            }

            PS.debug("&3Using connector: " + connector.getClass().getCanonicalName());

            Connection connection = connector.getPlotMeConnection(this.plugin, plotConfig, dataFolder);

            if (!connector.isValidConnection(connection)) {
                sendMessage("Cannot connect to PlotMe DB. Conversion process will not continue");
                return false;
            }

            sendMessage(this.plugin + " conversion has started. To disable this, please set 'plotme-convert.enabled' to false in the 'settings.yml'");

            mergeWorldYml(this.plugin, plotConfig);

            sendMessage("Connecting to " + this.plugin + " DB");

            ArrayList<Plot> createdPlots = new ArrayList<>();

            sendMessage("Collecting plot data");

            String dbPrefix = this.plugin.toLowerCase();
            sendMessage(" - " + dbPrefix + "Plots");
            final Set<String> worlds = getPlotMeWorlds(plotConfig);

            if (Settings.CONVERT_PLOTME) {
                sendMessage("Updating bukkit.yml");
                updateWorldYml(this.plugin, "bukkit.yml");
                updateWorldYml(this.plugin, "plugins/Multiverse-Core/worlds.yml");
                for (String world : plotConfig.getConfigurationSection("worlds").getKeys(false)) {
                    sendMessage("Copying config for: " + world);
                    try {
                        String actualWorldName = getWorld(world);
                        connector.copyConfig(plotConfig, world, actualWorldName);
                        PS.get().config.save(PS.get().configFile);
                    } catch (IOException e) {
                        e.printStackTrace();
                        sendMessage("&c-- &lFailed to save configuration for world '" + world
                                + "'\nThis will need to be done using the setup command, or manually");
                    }
                }
            }
            HashMap<String, HashMap<PlotId, Plot>> plots = connector.getPlotMePlots(connection);
            int plotCount = 0;
            for (Entry<String, HashMap<PlotId, Plot>> entry : plots.entrySet()) {
                plotCount += entry.getValue().size();
            }
            if (!Settings.CONVERT_PLOTME) {
                return false;
            }

            sendMessage(" - " + dbPrefix + "Allowed");

            sendMessage("Collected " + plotCount + " plots from PlotMe");
            File plotmeDgFile = new File(dataFolder + File.separator + "PlotMe-DefaultGenerator" + File.separator + "config.yml");
            if (plotmeDgFile.exists()) {
                YamlConfiguration plotmeDgYml = YamlConfiguration.loadConfiguration(plotmeDgFile);
                try {
                    for (String world : plots.keySet()) {
                        String actualWorldName = getWorld(world);
                        String plotMeWorldName = world.toLowerCase();
                        Integer pathwidth = plotmeDgYml.getInt("worlds." + plotMeWorldName + ".PathWidth"); //
                        /*
                         * TODO: dead code
                         * 
                        if (pathwidth == null) {
                            pathwidth = 7;
                        }
                        */
                        PS.get().config.set("worlds." + world + ".road.width", pathwidth);

                        Integer pathheight = plotmeDgYml.getInt("worlds." + plotMeWorldName + ".RoadHeight"); //
                        if (pathheight == 0) {
                            pathheight = 64;
                        }
                        PS.get().config.set("worlds." + world + ".road.height", pathheight);
                        PS.get().config.set("worlds." + world + ".wall.height", pathheight);
                        PS.get().config.set("worlds." + world + ".plot.height", pathheight);
                        Integer plotsize = plotmeDgYml.getInt("worlds." + plotMeWorldName + ".PlotSize"); //
                        if (plotsize == 0) {
                            plotsize = 32;
                        }
                        PS.get().config.set("worlds." + world + ".plot.size", plotsize);
                        String wallblock = plotmeDgYml.getString("worlds." + plotMeWorldName + ".WallBlock", "44"); //
                        PS.get().config.set("worlds." + world + ".wall.block", wallblock);
                        String floor = plotmeDgYml.getString("worlds." + plotMeWorldName + ".PlotFloorBlock", "2"); //
                        PS.get().config.set("worlds." + world + ".plot.floor", Collections.singletonList(floor));
                        String filling = plotmeDgYml.getString("worlds." + plotMeWorldName + ".FillBlock", "3"); //
                        PS.get().config.set("worlds." + world + ".plot.filling", Collections.singletonList(filling));
                        String road = plotmeDgYml.getString("worlds." + plotMeWorldName + ".RoadMainBlock", "5");
                        PS.get().config.set("worlds." + world + ".road.block", road);
                        Integer height = plotmeDgYml.getInt("worlds." + plotMeWorldName + ".RoadHeight"); //
                        if (height == 0) {
                            height = plotmeDgYml.getInt("worlds." + plotMeWorldName + ".GroundHeight"); //
                            if (height == 0) {
                                height = 64;
                            }
                        }
                        PS.get().config.set("worlds." + actualWorldName + ".road.height", height);
                        PS.get().config.set("worlds." + actualWorldName + ".plot.height", height);
                        PS.get().config.set("worlds." + actualWorldName + ".wall.height", height);
                        PS.get().config.save(PS.get().configFile);
                    }
                } catch (IOException ignored) {
                    //ignored
                }
            }
            for (Entry<String, HashMap<PlotId, Plot>> entry : plots.entrySet()) {
                String world = entry.getKey();
                PlotArea area = PS.get().getPlotArea(world, null);
                int duplicate = 0;
                if (area != null) {
                    for (Entry<PlotId, Plot> entry2 : entry.getValue().entrySet()) {
                        if (area.getOwnedPlotAbs(entry2.getKey()) != null) {
                            duplicate++;
                        } else {
                            createdPlots.add(entry2.getValue());
                        }
                    }
                    if (duplicate > 0) {
                        PS.debug("&c[WARNING] Found " + duplicate + " duplicate plots already in DB for world: '" + world
                                + "'. Have you run the converter already?");
                    }
                } else {
                    if (PS.get().plots_tmp != null) {
                        HashMap<PlotId, Plot> map = PS.get().plots_tmp.get(world);
                        if (map != null) {
                            for (Entry<PlotId, Plot> entry2 : entry.getValue().entrySet()) {
                                if (map.containsKey(entry2.getKey())) {
                                    duplicate++;
                                } else {
                                    createdPlots.add(entry2.getValue());
                                }
                            }
                            if (duplicate > 0) {
                                PS.debug("&c[WARNING] Found " + duplicate + " duplicate plots already in DB for world: '" + world
                                        + "'. Have you run the converter already?");
                            }
                            continue;
                        }
                    }
                    createdPlots.addAll(entry.getValue().values());
                }
            }
            sendMessage("Creating plot DB");
            Thread.sleep(1000);
            final AtomicBoolean done = new AtomicBoolean(false);
            DBFunc.createPlotsAndData(createdPlots, new Runnable() {
                @Override
                public void run() {
                    if (done.get()) {
                        done();
                        sendMessage("&aDatabase conversion is now complete!");
                        PS.debug("&c - Stop the server");
                        PS.debug("&c - Disable 'plotme-convert.enabled' and 'plotme-convert.cache-uuids' in the settings.yml");
                        PS.debug("&c - Correct any generator settings that haven't copied to 'settings.yml' properly");
                        PS.debug("&c - Start the server");
                        PS.get().setPlots(DBFunc.getPlots());
                    } else {
                        sendMessage("&cPlease wait until database conversion is complete. You will be notified with instructions when this happens!");
                        done.set(true);
                    }
                }
            });
            sendMessage("Saving configuration...");
            try {
                PS.get().config.save(PS.get().configFile);
            } catch (IOException e) {
                sendMessage(" - &cFailed to save configuration.");
            }
            TaskManager.runTask(new Runnable() {
                @Override
                public void run() {
                    try {
                        boolean mv = false;
                        boolean mw = false;
                        if ((Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null) && Bukkit.getPluginManager().getPlugin("Multiverse-Core")
                                .isEnabled()) {
                            mv = true;
                        } else if ((Bukkit.getPluginManager().getPlugin("MultiWorld") != null) && Bukkit.getPluginManager().getPlugin("MultiWorld")
                                .isEnabled()) {
                            mw = true;
                        }
                        for (String worldname : worlds) {
                            World world = Bukkit.getWorld(getWorld(worldname));
                            if (world == null) {
                                sendMessage("&cInvalid world in PlotMe configuration: " + worldname);
                            }
                            String actualWorldName = world.getName();
                            sendMessage("Reloading generator for world: '" + actualWorldName + "'...");
                            PS.get().removePlotAreas(actualWorldName);
                            if (mv) {
                                // unload world with MV
                                Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), "mv unload " + actualWorldName);
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException ex) {
                                    Thread.currentThread().interrupt();
                                }
                                // load world with MV
                                Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(),
                                        "mv import " + actualWorldName + " normal -g PlotSquared");
                            } else if (mw) {
                                // unload world with MW
                                Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), "mw unload " + actualWorldName);
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException ex) {
                                    Thread.currentThread().interrupt();
                                }
                                // load world with MW
                                Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(),
                                        "mw create " + actualWorldName + " plugin:PlotSquared");
                            } else {
                                // Load using Bukkit API
                                // - User must set generator manually
                                Bukkit.getServer().unloadWorld(world, true);
                                World myworld = WorldCreator.name(actualWorldName).generator(new BukkitPlotGenerator(new HybridGen())).createWorld();
                                myworld.save();
                            }
                        }
                    } catch (CommandException e) {
                        e.printStackTrace();
                    }
                    if (done.get()) {
                        done();
                        sendMessage("&aDatabase conversion is now complete!");
                        PS.debug("&c - Stop the server");
                        PS.debug("&c - Disable 'plotme-convert.enabled' and 'plotme-convert.cache-uuids' in the settings.yml");
                        PS.debug("&c - Correct any generator settings that haven't copied to 'settings.yml' properly");
                        PS.debug("&c - Start the server");
                    } else {
                        sendMessage("&cPlease wait until database conversion is complete. You will be notified with instructions when this happens!");
                        done.set(true);
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            PS.debug("&/end/");
        }
        return true;
    }

    public void done() {
        PS.get().setPlots(DBFunc.getPlots());
    }
}
