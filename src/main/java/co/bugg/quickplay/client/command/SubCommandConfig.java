package co.bugg.quickplay.client.command;

import co.bugg.quickplay.Quickplay;
import co.bugg.quickplay.client.gui.config.QuickplayGuiEditConfig;
import co.bugg.quickplay.util.TickDelay;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Sub command to open the config GUI
 */
public class SubCommandConfig extends ACommand {

    /**
     * Constructor
     * @param parent Parent command
     */
    public SubCommandConfig(ACommand parent) {
        super(
                parent,
                Arrays.asList("config", "settings"),
                Quickplay.INSTANCE.elementController.translate("quickplay.commands.quickplay.config.help"),
                "",
                true,
                true,
                99.9,
                false,
                parent == null ? 0 : parent.getDepth() + 1
        );
    }

    @Override
    public void run(String[] args) {
        new TickDelay(() -> Minecraft.getMinecraft().displayGuiScreen(
                new QuickplayGuiEditConfig(Quickplay.INSTANCE.settings)), 1);
    }

    @Override
    public List<String> getTabCompletions(String[] args) {
        return new ArrayList<>();
    }
}
