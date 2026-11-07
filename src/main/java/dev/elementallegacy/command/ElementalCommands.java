package dev.elementallegacy.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.elementallegacy.data.ElementalWorldData;
import dev.elementallegacy.data.PlayerProgress;
import dev.elementallegacy.element.Element;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class ElementalCommands {
    private ElementalCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("elemental")
                .then(Commands.literal("choose")
                        .then(Commands.argument("element", StringArgumentType.word())
                                .executes(context -> choose(context.getSource(), StringArgumentType.getString(context, "element")))))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
        );
    }

    private static int choose(CommandSourceStack source, String requested) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Element element = Element.byId(requested).orElse(null);
        if (element == null) {
            source.sendFailure(Component.literal("Elemento inválido. Escolha: tide, stone, ember ou gale."));
            return 0;
        }
        ElementalWorldData data = ElementalWorldData.get(player.getServer());
        if (!data.choose(player.getUUID(), element.id())) {
            source.sendFailure(Component.literal("Seu elemento já foi escolhido neste mundo e não pode ser trocado."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Seu caminho foi definido: " + element.displayName() + ". Esta escolha é permanente neste mundo."), false);
        return 1;
    }

    private static int status(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerProgress progress = ElementalWorldData.get(player.getServer()).get(player.getUUID());
        if (!progress.hasChosenElement()) {
            source.sendSuccess(() -> Component.literal("Você ainda não escolheu um caminho. Use /elemental choose <tide|stone|ember|gale>."), false);
            return 1;
        }
        Element element = Element.byId(progress.elementId()).orElseThrow();
        int required = progress.level() >= PlayerProgress.MAX_LEVEL ? 0 : PlayerProgress.xpToNextLevel(progress.level());
        source.sendSuccess(() -> Component.literal("Caminho: " + element.displayName() + " | Nível: " + progress.level() + "/50 | XP: " + progress.xp() + "/" + required + " | Energia: " + Math.round(progress.energy()) + "/100"), false);
        return 1;
    }
}
