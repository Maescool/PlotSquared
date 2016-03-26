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
package com.intellectualcrafters.plot.commands;

import com.intellectualcrafters.plot.PS;
import com.intellectualcrafters.plot.config.C;
import com.intellectualcrafters.plot.object.ConsolePlayer;
import com.intellectualcrafters.plot.object.Location;
import com.intellectualcrafters.plot.object.Plot;
import com.intellectualcrafters.plot.object.PlotArea;
import com.intellectualcrafters.plot.object.PlotPlayer;
import com.intellectualcrafters.plot.object.RunnableVal2;
import com.intellectualcrafters.plot.object.RunnableVal3;
import com.intellectualcrafters.plot.util.CmdConfirm;
import com.intellectualcrafters.plot.util.EconHandler;
import com.intellectualcrafters.plot.util.Permissions;
import com.plotsquared.general.commands.Command;
import com.plotsquared.general.commands.CommandDeclaration;
import java.util.Arrays;

/**
 * PlotSquared command class.
 */
@CommandDeclaration(
        command = "plot",
        aliases = {"plots", "p", "plotsquared", "plot2", "p2", "ps", "2", "plotme", "plotz", "ap"})
public class MainCommand extends Command {
    
    private static MainCommand instance;
    public Help help;
    public Toggle toggle;

    private MainCommand() {
        super(null, true);
        instance = this;
    }

    public static MainCommand getInstance() {
        if (instance == null) {
            instance = new MainCommand();
            new Buy();
            new Save();
            new Load();
            new Confirm();
            new Template();
            new Download();
            new Update();
            new Template();
            new Setup();
            new Area();
            new DebugSaveTest();
            new DebugLoadTest();
            new CreateRoadSchematic();
            new DebugAllowUnsafe();
            new RegenAllRoads();
            new Claim();
            new Auto();
            new Visit();
            new Set();
            new Clear();
            new Delete();
            new Trust();
            new Add();
            new Deny();
            new Untrust();
            new Remove();
            new Undeny();
            new Info();
            new ListCmd();
            new Debug();
            new SchematicCmd();
            new PluginCmd();
            new Purge();
            new Reload();
            new Merge();
            new DebugPaste();
            new Unlink();
            new Kick();
            new Rate();
            new DebugClaimTest();
            new Inbox();
            new Comment();
            new Database();
            new Swap();
            new Music();
            new DebugRoadRegen();
            new Trust();
            new DebugExec();
            new FlagCmd();
            new Target();
            new DebugFixFlags();
            new Move();
            new Condense();
            new Condense();
            new Copy();
            new Chat();
            new Trim();
            new Done();
            new Continue();
            new BO3();
            new Middle();
            new Grant();
            // Set commands
            new Owner();
            new Desc();
            new Biome();
            new Alias();
            new SetHome();
            new Cluster();
            // Referenced commands
            instance.toggle = new Toggle();
            instance.help = new Help(instance);
        }
        return instance;
    }

    @Deprecated
    /**
     * @Deprecated legacy
     */
    public void addCommand(SubCommand command) {
        PS.debug("Command registration is now done during instantiation");
    }

    public static boolean onCommand(final PlotPlayer player, String... args) {
        if (args.length >= 1 && args[0].contains(":")) {
            String[] split2 = args[0].split(":");
            if (split2.length == 2) {
                // Ref: c:v, this will push value to the last spot in the array
                // ex. /p h:2 SomeUsername
                // > /p h SomeUsername 2
                String[] tmp = new String[args.length + 1];
                tmp[0] = split2[0];
                tmp[args.length] = split2[1];
                if (args.length > 2) {
                    System.arraycopy(args, 1, tmp, 1, args.length - 1);
                }
                args = tmp;
            }
        }
        getInstance().execute(player, args, new RunnableVal3<Command, Runnable, Runnable>() {
            @Override
            public void run(final Command cmd, final Runnable success, final Runnable failure) {
                if (cmd.hasConfirmation(player) ) {
                    CmdConfirm.addPending(player, "/plot area create pos2 (Creates world)", new Runnable() {
                        @Override
                        public void run() {
                            if (EconHandler.manager != null) {
                                PlotArea area = player.getApplicablePlotArea();
                                if (area != null) {
                                    Double price = area.PRICES.get(cmd.getId());
                                    if (price != null && EconHandler.manager.getMoney(player) < price) {
                                        failure.run();
                                        return;
                                    }
                                }
                            }
                            success.run();
                        }
                    });
                    return;
                }
                if (EconHandler.manager != null) {
                    PlotArea area = player.getApplicablePlotArea();
                    if (area != null) {
                        Double price = area.PRICES.get(cmd.getId());
                        if (price != null && EconHandler.manager.getMoney(player) < price) {
                            failure.run();
                            return;
                        }
                    }
                }
                success.run();
            }
        }, new RunnableVal2<Command, CommandResult>() {
            @Override
            public void run(Command cmd, CommandResult result) {
                // Post command stuff!?
            }
        });
        // Always true
        return true;
    }

    @Override
    public void execute(PlotPlayer player, String[] args, RunnableVal3<Command, Runnable, Runnable> confirm, RunnableVal2<Command, CommandResult> whenDone) {
        // Clear perm caching //
        player.deleteMeta("perm");
        // Optional command scope //
        Location loc = null;
        Plot plot = null;
        boolean tp = false;
        if (args.length >= 2) {
            PlotArea area = player.getApplicablePlotArea();
            Plot newPlot = Plot.fromString(area, args[0]);
            if (newPlot != null && (ConsolePlayer.isConsole(player) || newPlot.getArea().equals(area) || Permissions.hasPermission(player, C.PERMISSION_ADMIN)) && !newPlot.isDenied(player.getUUID())) {
                // Save meta
                loc = player.getMeta("location");
                plot = player.getMeta("lastplot");
                tp = true;
                // Set loc
                player.setMeta("location", newPlot.getBottomAbs());
                player.setMeta("lastplot", newPlot);
                // Trim command
                args = Arrays.copyOfRange(args, 1, args.length);
            }
        }
        super.execute(player, args, confirm, whenDone);
        // Reset command scope //
        if (tp) {
            if (loc == null) {
                player.deleteMeta("location");
            } else {
                player.setMeta("location", loc);
            }
            if (plot == null) {
                player.deleteMeta("lastplot");
            } else {
                player.setMeta("lastplot", plot);
            }
        }
    }

    @Override
    public boolean canExecute(PlotPlayer player, boolean message) {
        return true;
    }
}
