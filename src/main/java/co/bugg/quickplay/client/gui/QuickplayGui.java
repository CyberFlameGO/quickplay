package co.bugg.quickplay.client.gui;

import co.bugg.quickplay.Quickplay;
import co.bugg.quickplay.Reference;
import com.google.common.collect.Lists;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class QuickplayGui extends GuiScreen {

    /**
     * Current vertical pixel index that the scrollable content is at at {@link #scrollFrameTop}
     */
    public int scrollPixel = 0;
    /**
     * Multiplier for the number of pixels the screen should scroll per call to {@link #mouseScrolled(int)}
     */
    public double scrollMultiplier = 2;
    /**
     * Number of ms between "scroll frames"
     * Absolute value is taken to avoid negatives.
     */
    public int scrollDelay = 2;
    /**
     * How many pixels high the content that can be scrolled is
     */
    public int scrollContentHeight = 0;
    /**
     * Vertical Y padding on the scrollable region for scrolling.
     *
     * @see #scrollFrameTop
     * @see #scrollFrameBottom
     */
    public int scrollYPadding = 30;
    /**
     * Top of the frame for scrollable content
     * When scrolling up, content SHOULD stop at this pixel
     *
     * If content is not drawn at this line to begin with, unexpected results may come.
     *
     * Default <code>0</code>, as set in {@link #initGui()}
     */
    public int scrollFrameTop;
    /**
     * Bottom of the frame for scrollable content
     * When scrolling down, content SHOULD stop at this pixel
     *
     * If {@link #calcScrollHeight()} is overridden, unexpected results may come.
     *
     * Default <code>height</code>, as set in {@link #initGui()}
     */
    public int scrollFrameBottom;
    /**
     * The list of all components that should be rendered
     */
    public List<QuickplayGuiComponent> componentList = new ArrayList<>();
    /**
     * The opacity of this screen
     */
    public float opacity = 0;
    /**
     * The amount that {@link #opacity} should be increased by every opacity frame on fade in
     * Absolute value calculated to avoid negative values.
     *
     * @see #fadeIn()
     */
    public float opacityPerFadeFrame = 0.05f;
    /**
     * The number of milliseconds between {@link #opacity} being increased by {@link #opacityPerFadeFrame} on fade in
     * Absolute value calculated to avoid negative values.
     *
     * @see #fadeIn()
     */
    public int opacityFrameDelay = 10;
    /**
     * Y location of the mouse when the mouse was last clicked
     * Used for scrolling via dragging
     *
     * @see #mouseClicked(int, int, int)
     * @see #mouseScrolled(int)
     * @see #mouseClickMove(int, int, int, long)
     */
    int lastMouseY = 0;
    /**
     * The currently open right-click context menu
     * <code>null</code> if none open
     *
     * Use {@link #closeContextMenu()} to close the context menu instead of just
     * setting this to <code>null</code>, otherwise the context menu will not close.
     */
    public QuickplayGuiContextMenu contextMenu = null;
    /**
     * Whether the GUI blurred background shader needs to be removed when the GUI closes
     * Assigned on GUI opening, otherwise if the user opens the GUI, the setting changes, and then closes the GUI,
     * the shader is applied until the user restarts the game, removes the shader manually, or re-enables the setting.
     */
    public boolean disableShaderOnGuiClose = Quickplay.INSTANCE.settings.blurGuiBackgrounds;

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if(disableShaderOnGuiClose) {
            // Stop using shaders
            Minecraft.getMinecraft().entityRenderer.stopUseShader();
        }

        // Show HUD again
        mc.gameSettings.hideGUI = false;
    }

    @Override
    public void initGui() {
        closeContextMenu();
        componentList.clear();

        scrollPixel = 0;

        super.initGui();
        if(Quickplay.INSTANCE.settings.fadeInGuis && opacity < 1)
            fadeIn();
        else opacity = 1;

        // Hide HUD (health & scoreboard & such)
        mc.gameSettings.hideGUI = true;

        // Load the blur background shader
        if(Quickplay.INSTANCE.settings.blurGuiBackgrounds) {

            // This method isn't public in some versions of Forge seemingly.
            // Reflection is used just in case
            Method loadShaderMethod = null;
            try {
                loadShaderMethod = EntityRenderer.class.getDeclaredMethod("loadShader", ResourceLocation.class);
            } catch (NoSuchMethodException e) {
                try {
                    loadShaderMethod = EntityRenderer.class.getDeclaredMethod("func_175069_a", ResourceLocation.class);
                } catch (NoSuchMethodException e1) {
                    e1.printStackTrace();
                    Quickplay.INSTANCE.sendExceptionRequest(e);
                }
            }

            if(loadShaderMethod != null) {
                loadShaderMethod.setAccessible(true);
                try {
                    loadShaderMethod.invoke(Minecraft.getMinecraft().entityRenderer, new ResourceLocation(Reference.MOD_ID, "shaders/quickplay_gui.json"));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                    Quickplay.INSTANCE.sendExceptionRequest(e);
                }
            }
        }

        setScrollingValues();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        for (QuickplayGuiComponent component : componentList) {
            component.draw(this, mouseX, mouseY, opacity);
        }
    }

    @Override
    public void drawDefaultBackground() {
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        // Prepend opacity to 24-bit color
        drawRect(0, 0, width, height, 0x000000 | ((int) (opacity * 0.5 * 255) << 24));
        // drawRect disables blend (Grr!)
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    @Override
    protected void drawHoveringText(List<String> textLines, int x, int y, FontRenderer font) {
        if(textLines.size() > 0) {
            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_BLEND);

            int textXMargins = 4;
            int boxMargins = 10;
            int textYMargins = 2;
            int textWidth = 0;

            // Calculate the max width of the text
            for(String line : textLines) {
                if(font.getStringWidth(line) > textWidth)
                    textWidth = font.getStringWidth(line);
            }

            boolean sidesSwapped = false;
            if(x > width / 2) {
                // Move the text over to the other side
                x -= textWidth + textXMargins * 2;
                // Subtract margins between the box and the cursor
                x -= boxMargins;
                // Side of the screen/mouse the text is rendered on is swapped
                // This is used for line wrapping
                sidesSwapped = true;
            } else {
                // Add margins between the box and the cursor
                x += boxMargins;
            }

            // Wrap all the lines if necessary
            if(x + textWidth + textXMargins * 2 + boxMargins > width || (sidesSwapped && x < boxMargins)) {
                final List<String> allWrappedLines = new ArrayList<>();
                int wrappedTextWidth = 0;
                for(String line : textLines) {
                    final int wrapWidth = sidesSwapped ? x + textWidth + textXMargins : width - x - boxMargins - textXMargins;
                    final List<String> wrappedLine = font.listFormattedStringToWidth(line, wrapWidth);

                    for(String wrappedFragment : wrappedLine) {
                        final int wrappedFragmentWidth = font.getStringWidth(wrappedFragment);
                        if(wrappedFragmentWidth > wrappedTextWidth)
                            wrappedTextWidth = wrappedFragmentWidth;

                        allWrappedLines.add(wrappedFragment);
                    }
                }
                // Recalcuate x if sides swapped
                if(sidesSwapped) {
                    x += textWidth - wrappedTextWidth;
                }

                textWidth = wrappedTextWidth;
                textLines = allWrappedLines;

            }
            // Calculate how high the tooltip should be
            int tooltipHeight = textLines.size() * (font.FONT_HEIGHT + textYMargins) + textYMargins * 2;

            // Move up if falling off bottom of screen
            if(y + tooltipHeight > height)
                y -= tooltipHeight;

            // Draw background
            drawRect(x, y, x + textWidth + textXMargins, y + tooltipHeight, (int) (opacity * 0.5 * 255) << 24);
            GL11.glEnable(GL11.GL_BLEND);

            // Draw text
            int currentLineY = y + textYMargins;
            for(String line : textLines) {
                drawString(font, line, x + textXMargins, currentLineY, Quickplay.INSTANCE.settings.secondaryColor.getColor().getRGB() & 0xFFFFFF | (int) (opacity * 255) << 24);
                currentLineY += font.FONT_HEIGHT + textYMargins;
            }

            GL11.glDisable(GL11.GL_BLEND);
            GL11.glPopMatrix();
        }
    }

    /**
     * Closes the currently open context menu, if there is one open.
     */
    public void closeContextMenu() {
        if(contextMenu != null) {
            if(componentList.contains(contextMenu))
                componentList.remove(contextMenu);
            contextMenu = null;
        }
    }

    /**
     * Fades this GUI in to 100% opacity if applicable
     *
     * Does NOT set opacity to 0 before fading, as that is sometimes
     * undesired. Set opacity to 0 before calling this if you want this.
     *
     * @see #opacityPerFadeFrame
     * @see #opacityFrameDelay
     */
    public void fadeIn() {
        if(opacity < 1)
            Quickplay.INSTANCE.threadPool.submit(() -> {
                while(opacity < 1) {
                    opacity+= Math.abs(opacityPerFadeFrame);
                    try {
                        Thread.sleep(Math.abs(opacityFrameDelay));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int distance;
        if((distance = Mouse.getDWheel()) != 0) {
            // Divide the distance by 10 as "120" px is way too much
            mouseScrolled(distance / 10);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if(mouseButton == 0)
            // Go through components in reverse order in order to process top elements first
            // Creates new copy of arraylist to avoid a ConcurrentModificationException caused by mouseClicked or componentClicked
            for(QuickplayGuiComponent component : new ArrayList<>(Lists.reverse(componentList))) {
                if(component.mouseHovering(this, mouseX, mouseY)) {
                    if(component.mouseClicked(this, mouseX, mouseY, mouseButton))
                        break;
                    componentClicked(component);
                }
            }

        closeContextMenu();
        // lastMouseY is used for dragging scrolling
        lastMouseY = mouseY;
    }

    /**
     * fun little easter egg in the mod
     * Cache variable for the user's last typed characters
     */
    private String trisTribute = "";
    /**
     * Keyword to trigger the easter egg
     */
    private final String magicWord = "TRIS";
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);

        for(QuickplayGuiComponent component : componentList) {
            if(component.keyTyped(typedChar, keyCode))
                return;
        }

        if(keyCode == 1 || keyCode == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(null);
        }

        /*
         * EASTER EGG
         */
        // add character to cache
        trisTribute += typedChar;
        // If cache matches magic word
        if(trisTribute.toUpperCase().endsWith(magicWord.toUpperCase())) {
            // Abra kadabra! Open sesame!
            // Load the fancy shader
            Minecraft.getMinecraft().entityRenderer.loadShader(new ResourceLocation(Reference.MOD_ID, "shaders/quickplay_rainbow_gui.json"));
            // Make it so the shader goes bye bye when the GUI closes
            disableShaderOnGuiClose = true;
            // Make a bunch of dumb noises
            Quickplay.INSTANCE.threadPool.submit(() -> {
                while(Minecraft.getMinecraft().currentScreen == this) {
                    mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("mob.chicken.hurt"), 1.0F));
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
        }
        // /tribute
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        final int mouseYMovement = lastMouseY - mouseY;
        lastMouseY = mouseY;
        // Scroll should be the same direction the mouse is moving
        if(mouseYMovement != 0) mouseScrolled(mouseYMovement * -1);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        for(QuickplayGuiComponent component : componentList)
            component.mouseReleased(this, mouseX, mouseY);
    }

    /**
     * Default calculation for top of the screen's scrolling limit
     * Finds the gui component with the lowest Y value (highest on screen)
     * @return the Y value of the highest component on the user's screen minus {@link #scrollYPadding}
     */
    public int calcScrollHeight() {
        if(componentList.size() > 0) {
            // component with the highest Y value
            QuickplayGuiComponent lowestComponent = null;
            // component with has the lowest Y value
            QuickplayGuiComponent highestComponent = null;
            for (QuickplayGuiComponent component : componentList) {
                if (component.scrollable && (highestComponent == null || highestComponent.y > component.y))
                    highestComponent = component;
                else if (component.scrollable && (lowestComponent == null || lowestComponent.y < component.y))
                    lowestComponent = component;
            }

            if(highestComponent != null && lowestComponent != null)
                return lowestComponent.y - highestComponent.y + lowestComponent.height;
            else
                return 0;
        } else {
            return 0;
        }
    }

    /**
     * Sets the GUI's scrolling values to determine
     * where the user can scroll & such
     */
    public void setScrollingValues() {

        // Default scrollable area Y padding. Can be changed
        scrollYPadding = 30;
        // Calculate the height of the scrollable content
        scrollContentHeight = calcScrollHeight();
        // Top & bottom of thee scroll frame
        scrollFrameBottom = height - scrollYPadding;
        scrollFrameTop = scrollYPadding;
    }

    /**
     * Called whenever the mouse is scrolled or the screen is dragged
     * @param distance Distance to scroll
     */
    public void mouseScrolled(int distance) {
        if(distance != 0) {
            // Scroll is animated so we run on a thread
            Quickplay.INSTANCE.threadPool.submit(() -> {
                final boolean scrollingUp = distance > 0;

                // Loop for the number of pixels scrolled
                for (int i = 0; i < Math.abs(distance * scrollMultiplier); i++) {

                    // If scrolling down & the height of the content (basically the bottom) has passed scrollFrameBottom
                    if ((!scrollingUp && scrollContentHeight > scrollPixel + (scrollFrameBottom - scrollFrameTop)) ||
                        // OR if scrolling up & the content is back to it's original position
                        (scrollingUp && 0 < scrollPixel)) {

                        if(scrollingUp)
                            scrollPixel--;
                        else
                            scrollPixel++;

                        try {
                            Thread.sleep(Math.abs(scrollDelay));
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            break;
                        }
                    } else {
                        // Already reached the bottom/top, so stop trying to scroll
                        break;
                    }
                }

            });
        }
    }

    /**
     * Called whenever a component in {@link #componentList} is clicked
     * @param component Component that is clicked
     */
    public void componentClicked(QuickplayGuiComponent component) {
        // Play clicky sound
        mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
    }
}
