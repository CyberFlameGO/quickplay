package co.bugg.quickplay;

import co.bugg.quickplay.actions.serverbound.ServerJoinedAction;
import co.bugg.quickplay.actions.serverbound.ServerLeftAction;
import co.bugg.quickplay.client.dailyreward.DailyRewardInitiator;
import co.bugg.quickplay.client.gui.InstanceDisplay;
import co.bugg.quickplay.client.gui.QuickplayGuiScreen;
import co.bugg.quickplay.client.gui.config.QuickplayGuiUsageStats;
import co.bugg.quickplay.elements.ElementController;
import co.bugg.quickplay.elements.Screen;
import co.bugg.quickplay.util.*;
import co.bugg.quickplay.wrappers.chat.ChatStyleWrapper;
import co.bugg.quickplay.wrappers.chat.Formatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Main event handler for Quickplay
 */
public class QuickplayEventHandler {

    /**
     * Runnable tasks scheduled to be ran in the main thread
     * This is mainly used for things that need Minecraft's OpenGL context
     * All items in this list are called before a frame is rendered
     */
    public static ArrayList<Runnable> mainThreadScheduledTasks = new ArrayList<>();

    @SubscribeEvent
    public void onJoin(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        Quickplay.INSTANCE.detectCurrentServers();
        Quickplay.INSTANCE.threadPool.submit(() -> {
            try {
                // Metadata is currently unused, however it's available in the spec for the future.
                String ip = ServerChecker.getCurrentIP();
                if(ip == null) {
                    ip = "";
                }
                Quickplay.INSTANCE.socket.sendAction(new ServerJoinedAction(ip, null));
            } catch (ServerUnavailableException e) {
                e.printStackTrace();
                Quickplay.INSTANCE.minecraft.sendLocalMessage(new Message(
                        new QuickplayChatComponentTranslation("quickplay.failedToConnect")
                                .setStyle(new ChatStyleWrapper().apply(Formatting.RED))));
                // Failed to connect to Quickplay backend -- Load gamelist from cache
                try {
                    Quickplay.INSTANCE.elementController = ElementController.loadCache();
                } catch (FileNotFoundException fileNotFoundException) {
                    Quickplay.LOGGER.warning("Failed to load cached elements.");
                    fileNotFoundException.printStackTrace();
                }
            }
        });
    }

    @SubscribeEvent
    public void onLeave(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Quickplay.INSTANCE.threadPool.submit(() -> {
            try {
                Quickplay.INSTANCE.socket.sendAction(new ServerLeftAction());
            } catch (ServerUnavailableException e) {
                e.printStackTrace();
            }
        });
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent event) {
        if(Quickplay.INSTANCE.isOnHypixel() && event.type == RenderGameOverlayEvent.ElementType.TEXT) {
            // Only render overlay if there is no other GUI open at the moment or if the GUI is chat (assuming proper settings)
            if(Quickplay.INSTANCE.settings.displayInstance && (Minecraft.getMinecraft().currentScreen == null ||
                    (Quickplay.INSTANCE.settings.displayInstanceWithChatOpen && (Minecraft.getMinecraft().currentScreen instanceof GuiChat)))) {
                InstanceDisplay instanceDisplay = Quickplay.INSTANCE.instanceDisplay;
                instanceDisplay.render(instanceDisplay.getxRatio(), instanceDisplay.getyRatio(), Quickplay.INSTANCE.settings.instanceOpacity);
            }
        }
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent event) {
        // handle any runnables that need to be ran with OpenGL context
        if(event.phase == TickEvent.Phase.START && !mainThreadScheduledTasks.isEmpty()) {
            // TODO optimize
            for(Runnable runnable : (ArrayList<Runnable>) mainThreadScheduledTasks.clone()) {
                runnable.run();
                mainThreadScheduledTasks.remove(runnable);
            }
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        // Prompt the user for usage stats setting every time they join a world until they select an
        // option (at which point promptUserForUsageStats is set to false & ConfigUsageStats is created)
        if(Quickplay.INSTANCE.promptUserForUsageStats) {
            new TickDelay(() -> Minecraft.getMinecraft().displayGuiScreen(new QuickplayGuiUsageStats()), 20);
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if(Quickplay.INSTANCE.isOnHypixel() && Quickplay.INSTANCE.settings.ingameDailyReward && Quickplay.INSTANCE.isPremiumClient) {
            // Get & verify link from chat
            try {
                final Pattern pattern = Pattern.compile(Quickplay.INSTANCE.elementController.getRegularExpression("dailyReward").value);
                final Matcher matcher = pattern.matcher(event.message.getUnformattedText());
                if (matcher.find()) {
                    Quickplay.INSTANCE.threadPool.submit(() -> {
                        new DailyRewardInitiator(matcher.group(1));

                        // Send analytical data
                        if(Quickplay.INSTANCE.ga != null) {
                            try {
                                Quickplay.INSTANCE.ga.createEvent("Daily Reward", "Open Daily Reward")
                                        .setEventLabel(event.message.getFormattedText())
                                        .send();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            } catch(PatternSyntaxException e) {
                Quickplay.LOGGER.warning("Daily reward regex invalid!");
                Quickplay.INSTANCE.sendExceptionRequest(e);
                e.printStackTrace();
            }
        }
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        // If the user's in a Hypixel lobby and interacts with their compass, open the Quickplay main menu GUI if
        // they have that setting enabled.
        if(Quickplay.INSTANCE.settings.mainMenuHypixelCompass && Quickplay.INSTANCE.isOnHypixel() &&
                Minecraft.getMinecraft().thePlayer != null && Minecraft.getMinecraft().thePlayer.getCurrentEquippedItem() != null &&
                Minecraft.getMinecraft().thePlayer.getCurrentEquippedItem().getUnlocalizedName().equals("item.compass") &&
                Quickplay.INSTANCE.hypixelInstanceWatcher.getCurrentLocation() != null &&
                Quickplay.INSTANCE.hypixelInstanceWatcher.getCurrentLocation().server.contains("lobby")
        ) {
            Screen mainScreen = Quickplay.INSTANCE.elementController.getScreen("MAIN");
            if(mainScreen == null) {
                return;
            }
            event.setCanceled(true);
            Minecraft.getMinecraft().displayGuiScreen(new QuickplayGuiScreen(mainScreen));
        }
    }

    @SubscribeEvent
    public void onGuiRenderEvent(GuiOpenEvent event) {
        // If the user's on Hypixel and they open a GUI which has the title "Game Menu", open the Quickplay main menu
        // GUI if they have that setting enabled. This is a fallback for onPlayerInteract, as there are other ways to
        // open the GUI, however onPlayerInteract looks and behaves much cleaner.
        if(Quickplay.INSTANCE.settings.mainMenuHypixelCompass && Quickplay.INSTANCE.isOnHypixel()
                && event.gui instanceof GuiChest) {
            try {
                // lowerChestInventory needs to be made available through reflection in order to get the display name.
                GuiChest chest = ((GuiChest) event.gui);
                Class<? extends GuiChest> chestClass = chest.getClass();
                String fieldName = (boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment") ? "lowerChestInventory" : "field_147015_w";
                Field lowerChestInventoryField = chestClass.getDeclaredField(fieldName);
                lowerChestInventoryField.setAccessible(true);
                IInventory inventory = (IInventory) lowerChestInventoryField.get(chest);

                String menuTitleRegex = Quickplay.INSTANCE.elementController.getRegularExpression("compassTitle").value;
                Pattern p = Pattern.compile(menuTitleRegex);
                if(p.matcher(inventory.getDisplayName().getUnformattedText()).find()) {
                    new TickDelay(() -> {
                        Minecraft.getMinecraft().thePlayer.closeScreen();
                        Minecraft.getMinecraft().displayGuiScreen(
                                new QuickplayGuiScreen(Quickplay.INSTANCE.elementController.getScreen("MAIN")));
                    }, 1);
                }

            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
                Quickplay.INSTANCE.sendExceptionRequest(e);
            } catch (PatternSyntaxException e) {
                Quickplay.LOGGER.warning("Compass title regex invalid!");
                Quickplay.INSTANCE.sendExceptionRequest(e);
                e.printStackTrace();
            }
        }
    }
}
