package dev.elementallegacy.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.elementallegacy.ElementalLegacy;
import dev.elementallegacy.ability.ElementalAbility;
import dev.elementallegacy.element.Element;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = ElementalLegacy.MOD_ID, value = Dist.CLIENT)
public final class ElementalClient {
    private static final KeyMapping POWER_Z = new KeyMapping(
            "key.elemental_legacy.power_z", InputConstants.KEY_Z, KeyMapping.Category.MISC);
    private static final KeyMapping POWER_X = new KeyMapping(
            "key.elemental_legacy.power_x", InputConstants.KEY_X, KeyMapping.Category.MISC);
    private static final KeyMapping OPEN_SELECTION = new KeyMapping(
            "key.elemental_legacy.open_selection", InputConstants.KEY_R, KeyMapping.Category.MISC);

    private static final String SYNC_PREFIX = "ELEMENTAL_SYNC|";
    private static final int HOLD_TICKS_TO_OPEN_WHEEL = 5;

    private static String localElement = "";
    private static String selectedZAbility = "";
    private static String selectedXAbility = "";
    private static boolean openedForConnection;
    private static boolean awaitingSync;
    private static int pendingShortcutKey = -1;
    private static int pendingShortcutTick;

    private ElementalClient() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(POWER_Z);
        event.register(POWER_X);
        event.register(OPEN_SELECTION);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        localElement = "";
        selectedZAbility = "";
        selectedXAbility = "";
        openedForConnection = false;
        awaitingSync = false;
        pendingShortcutKey = -1;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;

        if (!openedForConnection) {
            if (minecraft.gui.screen() != null) return;
            openedForConnection = true;
            awaitingSync = true;
            minecraft.player.connection.sendCommand("elemental sync");
            return;
        }

        if (awaitingSync || minecraft.gui.screen() != null) return;

        if (OPEN_SELECTION.consumeClick()) {
            pendingShortcutKey = -1;
            minecraft.gui.setScreen(new ElementalSkillScreen());
            return;
        }

        if (localElement.isBlank()) {
            POWER_Z.consumeClick();
            POWER_X.consumeClick();
            return;
        }

        // A press starts a short grace window. Releasing before the threshold casts;
        // holding beyond it opens the radial assignment wheel.
        if (pendingShortcutKey == -1) {
            if (POWER_Z.consumeClick()) {
                pendingShortcutKey = InputConstants.KEY_Z;
                pendingShortcutTick = minecraft.player.tickCount;
            } else if (POWER_X.consumeClick()) {
                pendingShortcutKey = InputConstants.KEY_X;
                pendingShortcutTick = minecraft.player.tickCount;
            }
        }

        if (pendingShortcutKey == InputConstants.KEY_Z || pendingShortcutKey == InputConstants.KEY_X) {
            KeyMapping mapping = pendingShortcutKey == InputConstants.KEY_Z ? POWER_Z : POWER_X;
            if (mapping.isDown()) {
                if (minecraft.player.tickCount - pendingShortcutTick >= HOLD_TICKS_TO_OPEN_WHEEL) {
                    int keyToOpen = pendingShortcutKey;
                    pendingShortcutKey = -1;
                    minecraft.gui.setScreen(new AbilityWheelScreen(keyToOpen));
                }
            } else {
                String abilityId = getBoundAbility(pendingShortcutKey == InputConstants.KEY_Z ? "Z" : "X");
                pendingShortcutKey = -1;
                useAbility(abilityId);
            }
        }
    }

    @SubscribeEvent
    public static void onSystemChat(ClientChatReceivedEvent.System event) {
        String message = event.getMessage().getString();
        if (!message.startsWith(SYNC_PREFIX)) return;
        event.setCanceled(true);

        String[] parts = message.split("\\|", -1);
        Minecraft minecraft = Minecraft.getInstance();
        awaitingSync = false;

        if (parts.length >= 3 && parts[1].equals("chosen")) {
            String id = parts[2];
            if (Element.byId(id).isPresent()) {
                localElement = id;
                selectedZAbility = parts.length > 3 ? parts[3] : "";
                selectedXAbility = parts.length > 4 ? parts[4] : "";
                applyDefaultBindings();
                return;
            }
        }

        if (parts.length >= 2 && parts[1].equals("unchosen")) {
            localElement = "";
            selectedZAbility = "";
            selectedXAbility = "";
            if (!Boolean.getBoolean("elemental.vfx.smoke")
                    && minecraft.player != null && minecraft.level != null && minecraft.gui.screen() == null) {
                minecraft.gui.setScreen(new ElementSelectionScreen());
            }
            return;
        }

        localElement = "";
        selectedZAbility = "";
        selectedXAbility = "";
        if (minecraft.player != null && minecraft.level != null && minecraft.gui.screen() == null) {
            minecraft.gui.setScreen(new ElementSelectionScreen());
        }
    }

    private static void applyDefaultBindings() {
        Element element = Element.byId(localElement).orElse(null);
        if (element == null) return;
        var abilities = ElementalAbility.forElement(element);
        if (abilities.isEmpty()) return;
        if (ElementalAbility.byId(selectedZAbility).filter(a -> a.element() == element).isEmpty()) {
            selectedZAbility = abilities.get(0).id();
        }
        if (ElementalAbility.byId(selectedXAbility).filter(a -> a.element() == element).isEmpty()) {
            selectedXAbility = abilities.get(Math.min(1, abilities.size() - 1)).id();
        }
    }

    public static String getLocalElement() {
        return localElement;
    }

    public static void setLocalElement(String element) {
        localElement = element;
        applyDefaultBindings();
    }

    public static String getBoundAbility(String shortcut) {
        applyDefaultBindings();
        return "Z".equalsIgnoreCase(shortcut) ? selectedZAbility : selectedXAbility;
    }

    public static void bindShortcut(String shortcut, String abilityId) {
        if ("Z".equalsIgnoreCase(shortcut)) selectedZAbility = abilityId;
        else selectedXAbility = abilityId;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.connection.sendCommand("elemental bind " + shortcut.toLowerCase(java.util.Locale.ROOT) + " " + abilityId);
        }
    }

    private static void useAbility(String abilityId) {
        if (Minecraft.getInstance().player == null || abilityId == null || abilityId.isBlank()) return;
        Minecraft.getInstance().player.connection.sendCommand("elemental use " + abilityId);
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || localElement.isBlank()) return;

        applyDefaultBindings();
        var graphics = event.getGuiGraphics();
        int x = 10;
        int y = 10;
        graphics.fill(x - 4, y - 4, x + 238, y + 61, 0x990D1420);
        graphics.text(minecraft.font, "ELEMENTAL LEGACY", x, y, 0xFFE9D8, false);
        String elementName = Element.byId(localElement).map(Element::displayName).orElse(localElement);
        graphics.text(minecraft.font, Component.literal("Elemento: " + elementName), x, y + 13, 0xFFFFFFFF, false);
        String zName = ElementalAbility.byId(selectedZAbility).map(ElementalAbility::displayName).orElse("Técnica 1");
        String xName = ElementalAbility.byId(selectedXAbility).map(ElementalAbility::displayName).orElse("Técnica 2");
        graphics.text(minecraft.font, Component.literal("[Z] " + zName), x, y + 27, 0xFFFFD18D, false);
        graphics.text(minecraft.font, Component.literal("[X] " + xName + "   [R] Técnicas"), x, y + 41, 0xFF9EDCF4, false);
    }
}