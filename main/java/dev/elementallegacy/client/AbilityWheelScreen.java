package dev.elementallegacy.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.elementallegacy.ability.ElementalAbility;
import dev.elementallegacy.element.Element;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class AbilityWheelScreen extends Screen {
    private final int shortcutKey;
    private final String shortcutName;
    private final Element element;
    private final List<ElementalAbility> abilities;
    private int selectedIndex;
    private boolean finished;

    public AbilityWheelScreen(int shortcutKey) {
        super(Component.literal("Roda de técnicas"));
        this.shortcutKey = shortcutKey;
        this.shortcutName = shortcutKey == InputConstants.KEY_Z ? "Z" : "X";
        this.element = Element.byId(ElementalClient.getLocalElement()).orElse(null);
        this.abilities = element == null ? List.of() : ElementalAbility.forElement(element);
        String selected = ElementalClient.getBoundAbility(shortcutName);
        for (int i = 0; i < abilities.size(); i++) {
            if (abilities.get(i).id().equals(selected)) {
                selectedIndex = i;
                break;
            }
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        updateSelection(mouseX, mouseY);
    }

    private void updateSelection(double mouseX, double mouseY) {
        if (abilities.isEmpty()) return;
        double centerX = width / 2.0;
        double centerY = height / 2.0;
        double radiusX = Math.max(1.0, Math.min(width * 0.31, width / 2.0 - 58.0));
        double radiusY = Math.max(1.0, Math.min(height * 0.31, height / 2.0 - 25.0));
        double dx = (mouseX - centerX) / radiusX;
        double dy = (mouseY - centerY) / radiusY;
        if (dx * dx + dy * dy < 0.12) return;
        double angle = Math.atan2(dy, dx);
        double relative = angle + Math.PI / 2.0;
        int index = (int) Math.floor((relative + Math.PI / 6.0) / (Math.PI / 3.0));
        selectedIndex = Math.floorMod(index, abilities.size());
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == shortcutKey) {
            commitSelection();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            closeWithoutSaving();
            return true;
        }
        return super.keyPressed(event);
    }

    private void commitSelection() {
        if (finished) return;
        finished = true;
        if (!abilities.isEmpty()) {
            ElementalClient.bindShortcut(shortcutName, abilities.get(selectedIndex).id());
        }
        Minecraft.getInstance().gui.setScreen(null);
    }

    private void closeWithoutSaving() {
        if (finished) return;
        finished = true;
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    public void onClose() {
        closeWithoutSaving();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0xB800050B);

        if (abilities.isEmpty()) {
            graphics.centeredText(font, "NENHUMA TÉCNICA DISPONÍVEL", width / 2, height / 2, 0xFFFFFFFF);
            return;
        }

        int centerX = width / 2;
        int centerY = height / 2;
        int tileWidth = Math.min(124, Math.max(94, width / 5));
        int tileHeight = 34;
        double radiusX = Math.max(54.0, Math.min(width * 0.31, width / 2.0 - tileWidth / 2.0 - 8.0));
        double radiusY = Math.max(48.0, Math.min(height * 0.31, height / 2.0 - tileHeight / 2.0 - 8.0));

        // Outer wheel ring and six radial spokes.
        for (int i = 0; i < 96; i++) {
            double angle = Math.PI * 2.0 * i / 96.0;
            int x = centerX + (int) Math.round(Math.cos(angle) * radiusX * 0.60);
            int y = centerY + (int) Math.round(Math.sin(angle) * radiusY * 0.60);
            graphics.fill(x - 1, y - 1, x + 2, y + 2, 0x886E879B);
        }

        for (int i = 0; i < abilities.size(); i++) {
            double angle = -Math.PI / 2.0 + i * Math.PI / 3.0;
            int tileCenterX = centerX + (int) Math.round(Math.cos(angle) * radiusX);
            int tileCenterY = centerY + (int) Math.round(Math.sin(angle) * radiusY);
            int color = i == selectedIndex ? elementColor(element) : 0x665D7489;
            int raySteps = 24;
            for (int step = 1; step <= raySteps; step++) {
                double t = step / (double) raySteps;
                int x = centerX + (int) Math.round((tileCenterX - centerX) * t * 0.72);
                int y = centerY + (int) Math.round((tileCenterY - centerY) * t * 0.72);
                graphics.fill(x - 1, y - 1, x + 2, y + 2, i == selectedIndex ? 0xCCB9D8EA : 0x66566B80);
            }

            int left = tileCenterX - tileWidth / 2;
            int top = tileCenterY - tileHeight / 2;
            int fill = i == selectedIndex ? 0xF02B3D4D : 0xD0101A25;
            graphics.fill(left, top, left + tileWidth, top + tileHeight, fill);
            graphics.fill(left, top, left + tileWidth, top + 2, color);
            graphics.fill(left, top + tileHeight - 2, left + tileWidth, top + tileHeight, color);
            graphics.fill(left, top, left + 2, top + tileHeight, color);
            graphics.fill(left + tileWidth - 2, top, left + tileWidth, top + tileHeight, color);

            String label = fit(abilities.get(i).displayName(), tileWidth - 12);
            graphics.centeredText(font, (i + 1) + "  " + label, tileCenterX, tileCenterY - 5,
                    i == selectedIndex ? 0xFFFFFFFF : 0xFFD5DEE8);
            String cost = abilities.get(i).energyCost() + " energia";
            graphics.centeredText(font, cost, tileCenterX, tileCenterY + 8,
                    i == selectedIndex ? 0xFFB8D7F0 : 0xFF93A2B1);
        }

        // Central hub.
        int hubWidth = Math.min(164, width - 24);
        int hubHeight = 58;
        graphics.fill(centerX - hubWidth / 2, centerY - hubHeight / 2,
                centerX + hubWidth / 2, centerY + hubHeight / 2, 0xF20B121B);
        graphics.fill(centerX - hubWidth / 2, centerY - hubHeight / 2,
                centerX + hubWidth / 2, centerY - hubHeight / 2 + 2, elementColor(element));
        graphics.centeredText(font, "ATALHO " + shortcutName + "  •  " + element.displayName().toUpperCase(),
                centerX, centerY - 18, 0xFFCAD8E6);
        ElementalAbility selected = abilities.get(selectedIndex);
        graphics.centeredText(font, fit(selected.displayName(), hubWidth - 14),
                centerX, centerY - 4, 0xFFFFFFFF);
        graphics.centeredText(font, selected.energyCost() + " energia  |  " +
                Math.round(selected.damage()) + " dano", centerX, centerY + 10, 0xFFB7C6D6);

        graphics.centeredText(font, "MOVA O MOUSE PARA ESCOLHER  •  SOLTE " + shortcutName + " PARA SALVAR",
                centerX, height - 16, 0xFFE3EAF0);
    }

    private String fit(String value, int maxWidth) {
        String result = value;
        while (font.width(result) > maxWidth && result.length() > 4) {
            result = result.substring(0, result.length() - 2) + "…";
        }
        return result;
    }

    private static int elementColor(Element element) {
        if (element == null) return 0xFFB6C8D8;
        return switch (element) {
            case EMBER -> 0xFFFF873D;
            case TIDE -> 0xFF48C8F0;
            case STONE -> 0xFFD1B17B;
            case GALE -> 0xFF8CE3CF;
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}