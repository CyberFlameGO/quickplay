package co.bugg.quickplay.client.command;

import co.bugg.quickplay.Quickplay;
import co.bugg.quickplay.client.gui.config.EditConfiguration;
import co.bugg.quickplay.util.TickDelay;

import java.util.ArrayList;
import java.util.List;

/**
 * Sub command to move the current instance display around
 * TODO this should be removed later and put into the config GUI, but is implemented for testing
 */
public class SubCommandMoveInstance extends ASubCommand {

    /**
     * Constructor
     * @param parent Parent command
     */
    public SubCommandMoveInstance(ASubCommandParent parent) {
        super(
                parent,
                "moveinstance",
                "Move the Instance display around",
                "",
                false,
                false,
                -100.0
        );
    }

    @Override
    public void run(String[] args) {
        new TickDelay(() -> Quickplay.INSTANCE.instanceDisplay.edit(), 1);
    }

    @Override
    public List<String> getTabCompletions(String[] args) {
        return new ArrayList<>();
    }
}
