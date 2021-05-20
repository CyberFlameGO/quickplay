package co.bugg.quickplay.client.gui.config;

import co.bugg.quickplay.Quickplay;
import co.bugg.quickplay.client.QuickplayKeybind;
import co.bugg.quickplay.client.gui.QuickplayGui;
import co.bugg.quickplay.client.gui.components.QuickplayGuiButton;
import co.bugg.quickplay.client.gui.components.QuickplayGuiComponent;
import co.bugg.quickplay.client.gui.components.QuickplayGuiContextMenu;
import co.bugg.quickplay.client.gui.components.QuickplayGuiString;
import co.bugg.quickplay.config.ConfigKeybinds;
import co.bugg.quickplay.elements.Button;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Quickplay GUI for editing the list of keybinds known to the basic Quickplay settings
 */
public class QuickplayGuiKeybinds extends QuickplayGui {

    /**
     * Y position that the buttons for each keybind start at
     */
    public int topOfButtons;
    /**
     * The width of each button in the GUI
     */
    public int buttonWidth = 200;
    /**
     * The height of each button in the GUI
     */
    public int buttonHeight = 20;
    /**
     * The margins between each button on the GUI
     */
    public int buttonMargins = 3;
    /**
     * How wide the "Reset" button on the screen is
     */
    public int resetButtonWidth = 90;
    /**
     * The display text of the reset button
     */
    public final String resetButtonText = Quickplay.INSTANCE.elementController.translate("quickplay.keybinds.reset");
    /**
     * The color of keybinds on buttons when they are not being edited
     */
    public final EnumChatFormatting keybindColor = EnumChatFormatting.YELLOW;
    /**
     * The color of keybinds when the keybind is currently selected & being edited
     */
    public final EnumChatFormatting keybindEditingColor = EnumChatFormatting.GOLD;
    /**
     * The separating characters between a keybind's name and the key it's mapped to
     */
    public final String keybindNameSeparator = " : ";
    /**
     * Characters that are prepended to the keybind button display string when it is being edited
     */
    public final String keybindPrependedEditingText = "> ";
    /**
     * Characters that are appended to the keybind button display string when it is being edited
     */
    public final String keybindAppendedEditingText = " <";
    /**
     * The GUI component for the keybind that is currently selected and being edited
     */
    public QuickplayGuiComponent selectedComponent = null;
    /**
     * Whether a popup telling the client that the key they tried to assign is already taken
     * This is set to true whenever the user tries to bind a key that's already set to something else
     * It disappears shortly afterwards by setting this back to false.
     */
    public boolean drawTakenPopup;

    @Override
    public void initGui() {
        super.initGui();

        topOfButtons = (int) (height * 0.1);

        int buttonId = 0;

        // Header
        this.componentList.add(new QuickplayGuiString(null, buttonId, width / 2,
                topOfButtons + (buttonHeight + buttonMargins) * buttonId++, buttonWidth, buttonHeight,
                Quickplay.INSTANCE.elementController.translate("quickplay.keybinds.title"), true, true));
        // Subheader
        this.componentList.add(new QuickplayGuiString(null, buttonId, width / 2,
                topOfButtons + (buttonHeight + buttonMargins) * buttonId++, buttonWidth, buttonHeight,
                Quickplay.INSTANCE.elementController.translate("quickplay.keybinds.subtitle"), true, true, true));

        for(QuickplayKeybind keybind : Quickplay.INSTANCE.keybinds.keybinds) {
            if(keybind == null || keybind.target == null) {
                continue;
            }
            final Button button = Quickplay.INSTANCE.elementController.getButton(keybind.target);
            if(button == null) {
                continue;
            }
            final QuickplayGuiComponent component = new QuickplayGuiButton(keybind, buttonId, width / 2 - buttonWidth / 2,
                    topOfButtons + (buttonHeight + buttonMargins) * buttonId++, buttonWidth, buttonHeight,
                    Quickplay.INSTANCE.elementController.translate(button.translationKey), true);
            formatComponentString(component, false);
            this.componentList.add(component);
        }

        // Reset button
        this.componentList.add(new QuickplayGuiButton(null, buttonId, width - buttonMargins - resetButtonWidth,
                height - buttonMargins - buttonHeight, resetButtonWidth, buttonHeight, resetButtonText, false));

        setScrollingValues();
    }

    @Override
    public void setScrollingValues() {
        super.setScrollingValues();
        // TODO there's a weird bug here that causes items to fall off the screen on large screens. Resolved it
        //  temporarily by increasing scrollContentMargins but that can make things look silly depending on screen size
        scrollContentMargins = (int) (height * 0.15);
        // Apply this change by recalculating scroll height
        scrollContentHeight = calcScrollHeight();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();

        drawDefaultBackground();

        if(Quickplay.INSTANCE.isEnabled) {
            super.drawScreen(mouseX, mouseY, partialTicks);

            if (drawTakenPopup) {
                final List<String> hoverText = new ArrayList<>();
                hoverText.add(Quickplay.INSTANCE.elementController.translate("quickplay.gui.keybinds.taken"));
                drawHoveringText(hoverText, mouseX, mouseY);
            }

            drawScrollbar(width / 2 + buttonWidth / 2 + 3);
        } else {
            // Quickplay is disabled, draw error message
            this.drawCenteredString(this.fontRendererObj,
                    Quickplay.INSTANCE.elementController.translate("quickplay.disabled", Quickplay.INSTANCE.disabledReason),
                    this.width / 2, this.height / 2, 0xffffff);
        }

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        for(QuickplayGuiComponent component : componentList) {
            if(mouseButton == 1 && component.origin instanceof QuickplayKeybind && component.mouseHovering(this, mouseX, mouseY)) {
                final QuickplayKeybind keybind = (QuickplayKeybind) component.origin;
                final String trueStr = Quickplay.INSTANCE.elementController.translate("quickplay.config.gui.true");
                final String falseStr = Quickplay.INSTANCE.elementController.translate("quickplay.config.gui.false");
                contextMenu = new QuickplayGuiContextMenu(
                    Arrays.asList(
                        Quickplay.INSTANCE.elementController.translate("quickplay.gui.keybinds.delete"),
                        Quickplay.INSTANCE.elementController.translate("quickplay.gui.keybinds.requireHolding", keybind.requiresPressTimer ? trueStr : falseStr)
                    ), component, -1, mouseX, mouseY) {

                    @Override
                    public void optionSelected(int index) {
                        switch(index) {
                            case 0:
                                Quickplay.INSTANCE.keybinds.keybinds.remove(keybind);
                                Quickplay.INSTANCE.unregisterEventHandler(keybind);
                                try {
                                    Quickplay.INSTANCE.keybinds.save();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    Quickplay.INSTANCE.sendExceptionRequest(e);
                                }

                                initGui();
                                break;
                            case 1:
                                keybind.requiresPressTimer = !keybind.requiresPressTimer;
                                try {
                                    Quickplay.INSTANCE.keybinds.save();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    Quickplay.INSTANCE.sendExceptionRequest(e);
                                }
                                break;
                        }
                    }
                };
                this.addComponent(contextMenu);
                break;
            }
        }
    }

    @Override
    public void componentClicked(QuickplayGuiComponent component) {
        super.componentClicked(component);
        if(component.origin instanceof QuickplayKeybind) {
            if(selectedComponent != null) {
                formatComponentString(selectedComponent, false);
            }
            selectedComponent = component;
            formatComponentString(component, true);
        } else if(component.displayString.equals(resetButtonText)) {
            try {
                // Unsubscribe all keybinds
                for(QuickplayKeybind keybind : Quickplay.INSTANCE.keybinds.keybinds)
                    Quickplay.INSTANCE.unregisterEventHandler(keybind);

                // Create a new keybind list
                Quickplay.INSTANCE.keybinds = new ConfigKeybinds(true);
                Quickplay.INSTANCE.keybinds.save();
                initGui();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        closeContextMenu();
        if(selectedComponent != null) {
            final QuickplayKeybind keybind = (QuickplayKeybind) selectedComponent.origin;
            if(Quickplay.INSTANCE.keybinds.keybinds.stream().anyMatch(keybind1 -> keybind1.key == keyCode && keybind != keybind1)) {
                // Key is already taken so cancel, draw a popup telling them, and hide it in 3 seconds
                drawTakenPopup = true;
                Quickplay.INSTANCE.threadPool.submit(() -> {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    drawTakenPopup = false;
                });
            } else {
                if (keyCode == Keyboard.KEY_ESCAPE) {
                    keybind.key = Keyboard.KEY_NONE;
                } else {
                    keybind.key = keyCode;
                    // Send analytical data to Google
                    if (Quickplay.INSTANCE.usageStats != null && Quickplay.INSTANCE.usageStats.statsToken != null &&
                            Quickplay.INSTANCE.usageStats.sendUsageStats && Quickplay.INSTANCE.ga != null) {
                        Quickplay.INSTANCE.threadPool.submit(() -> {
                            try {
                                Quickplay.INSTANCE.ga.createEvent("Keybinds", "Keybind Changed")
                                        .setEventLabel(keybind.target + " : " + keybind.key)
                                        .send();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                    }
                }
            }

            try {
                formatComponentString(selectedComponent, false);
            } catch(IllegalArgumentException e) {
                e.printStackTrace();
                Quickplay.INSTANCE.sendExceptionRequest(e);
            }
            selectedComponent = null;
            Quickplay.INSTANCE.keybinds.save();
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }

    /**
     * Format the display string for the given component & keybind
     * @param component Component to format
     * @param selected Whether this component is currently selected/being edited or not
     * @throws IllegalArgumentException when the component provided's origin isn't a QuickplayKeybind
     */
    public void formatComponentString(QuickplayGuiComponent component, boolean selected) {
        if(component.origin instanceof QuickplayKeybind) {
            final QuickplayKeybind keybind = (QuickplayKeybind) component.origin;
            final Button button = Quickplay.INSTANCE.elementController.getButton(keybind.target);
            final String title = button == null ? "null" : Quickplay.INSTANCE.elementController.translate(button.translationKey);
            if(selected) {
                component.displayString = keybindPrependedEditingText + title + keybindNameSeparator +
                        keybindEditingColor + Keyboard.getKeyName(keybind.key) + EnumChatFormatting.RESET +
                        keybindAppendedEditingText;
            } else {
                component.displayString = title + keybindNameSeparator + keybindColor +
                        Keyboard.getKeyName(keybind.key) + EnumChatFormatting.RESET;
            }
        } else {
            throw new IllegalArgumentException("The GUI component provided does not have a QuickplayKeybind as it's origin!");
        }
    }
}
