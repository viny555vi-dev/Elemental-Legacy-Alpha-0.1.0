package dev.elementallegacy.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.elementallegacy.ability.ElementalAbility;
import dev.elementallegacy.ability.ElementalAbilityService;
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
                .then(Commands.literal("sync")
                        .executes(context -> sync(context.getSource())))
                .then(Commands.literal("choose")
                        .then(Commands.argument("element", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    builder.suggest("tide"); builder.suggest("stone"); builder.suggest("ember"); builder.suggest("gale");
                                    return builder.buildFuture();
                                })
                                .executes(context -> choose(context.getSource(), StringArgumentType.getString(context, "element")))))
                .then(Commands.literal("bind")
                        .then(Commands.argument("slot", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    builder.suggest("z"); builder.suggest("x");
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("ability", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            ServerPlayer player = context.getSource().getPlayer();
                                            if (player != null) {
                                                PlayerProgress progress = ElementalWorldData.get(context.getSource().getServer()).get(player.getUUID());
                                                Element.byId(progress.elementId()).ifPresent(element ->
                                                        ElementalAbility.forElement(element).forEach(ability -> builder.suggest(ability.id())));
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> bind(context.getSource(),
                                                StringArgumentType.getString(context, "slot"),
                                                StringArgumentType.getString(context, "ability"))))))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("skills").executes(context -> skills(context.getSource())))
                .then(Commands.literal("select")
                        .then(Commands.argument("ability", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player != null) {
                                        PlayerProgress progress = ElementalWorldData.get(context.getSource().getServer()).get(player.getUUID());
                                        Element.byId(progress.elementId()).ifPresent(element ->
                                                ElementalAbility.forElement(element).forEach(ability -> builder.suggest(ability.id())));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> select(context.getSource(), StringArgumentType.getString(context, "ability")))))
                .then(Commands.literal("use")
                        .executes(context -> useSelected(context.getSource()))
                        .then(Commands.argument("ability", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player != null) {
                                        PlayerProgress progress = ElementalWorldData.get(context.getSource().getServer()).get(player.getUUID());
                                        Element.byId(progress.elementId()).ifPresent(element ->
                                                ElementalAbility.forElement(element).forEach(ability -> builder.suggest(ability.id())));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> use(context.getSource(), StringArgumentType.getString(context, "ability")))))
        );
    }

    private static int sync(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerProgress progress = ElementalWorldData.get(source.getServer()).get(player.getUUID());
        String response;
        if (progress.hasChosenElement()) {
            Element element = Element.byId(progress.elementId()).orElse(null);
            String zAbility = resolveButtonAbility(progress, element, "z");
            String xAbility = resolveButtonAbility(progress, element, "x");
            response = "ELEMENTAL_SYNC|chosen|" + progress.elementId() + "|" + zAbility + "|" + xAbility;
        } else {
            response = "ELEMENTAL_SYNC|unchosen";
        }
        source.sendSuccess(() -> Component.literal(response), false);
        return 1;
    }

    private static String resolveButtonAbility(PlayerProgress progress, Element element, String slot) {
        if (element == null) return "";
        String stored = "z".equals(slot) ? progress.selectedZAbility() : progress.selectedXAbility();
        if (stored.isBlank() && "z".equals(slot)) stored = progress.selectedAbility();
        ElementalAbility selected = ElementalAbility.byId(stored).orElse(null);
        if (selected != null && selected.element() == element) return selected.id();
        var abilities = ElementalAbility.forElement(element);
        if (abilities.isEmpty()) return "";
        int defaultIndex = "z".equals(slot) ? 0 : 1;
        return abilities.get(Math.min(defaultIndex, abilities.size() - 1)).id();
    }

    private static int bind(CommandSourceStack source, String requestedSlot, String requestedAbility)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String slot = requestedSlot.toLowerCase(java.util.Locale.ROOT);
        if (!slot.equals("z") && !slot.equals("x")) {
            source.sendFailure(Component.literal("Atalho inválido. Use z ou x."));
            return 0;
        }
        PlayerProgress progress = ElementalWorldData.get(source.getServer()).get(player.getUUID());
        ElementalAbility ability = ElementalAbility.byId(requestedAbility).orElse(null);
        if (!progress.hasChosenElement() || ability == null
                || !ability.element().id().equals(progress.elementId())) {
            source.sendFailure(Component.literal("Técnica inválida ou incompatível com seu elemento."));
            return 0;
        }
        ElementalWorldData.get(source.getServer()).put(player.getUUID(),
                progress.withButtonAbility(slot, ability.id()));
        source.sendSuccess(() -> Component.literal("Atalho " + slot.toUpperCase(java.util.Locale.ROOT)
                + " configurado: " + ability.displayName() + "."), false);
        return 1;
    }

    private static int choose(CommandSourceStack source, String requested) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Element element = Element.byId(requested).orElse(null);
        if (element == null) {
            source.sendFailure(Component.literal("Elemento inválido. Escolha: tide, stone, ember ou gale."));
            return 0;
        }
        ElementalWorldData data = ElementalWorldData.get(source.getServer());
        if (!data.choose(player.getUUID(), element.id())) {
            source.sendFailure(Component.literal("Seu elemento já foi escolhido neste mundo e não pode ser trocado."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Seu caminho foi definido: " + element.displayName() + ". Esta escolha é permanente neste mundo."), false);
        return 1;
    }

    private static int status(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerProgress progress = ElementalWorldData.get(source.getServer()).get(player.getUUID());
        if (!progress.hasChosenElement()) {
            source.sendSuccess(() -> Component.literal("Você ainda não escolheu um caminho. Use /elemental choose <tide|stone|ember|gale>."), false);
            return 1;
        }
        Element element = Element.byId(progress.elementId()).orElseThrow();
        int required = progress.level() >= PlayerProgress.MAX_LEVEL ? 0 : PlayerProgress.xpToNextLevel(progress.level());
        source.sendSuccess(() -> Component.literal("Caminho: " + element.displayName() + " | Nível: " + progress.level() + "/50 | XP: " + progress.xp() + "/" + required + " | Energia: " + Math.round(progress.energy()) + "/100"), false);
        return 1;
    }

    private static int skills(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerProgress progress = ElementalWorldData.get(source.getServer()).get(player.getUUID());
        Element element = Element.byId(progress.elementId()).orElse(null);
        if (element == null) {
            source.sendFailure(Component.literal("Escolha seu elemento primeiro com /elemental choose <tide|stone|ember|gale>."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Técnicas de " + element.displayName() + ":"), false);
        for (ElementalAbility ability : ElementalAbility.forElement(element)) {
            source.sendSuccess(() -> Component.literal("- " + ability.id() + " — " + ability.displayName()
                    + " | custo " + ability.energyCost() + " energia | recarga " + (ability.cooldownTicks() / 20)
                    + "s | dano " + ability.damage() + " | " + ability.description()), false);
        }
        source.sendSuccess(() -> Component.literal("Use /elemental select <id> e depois /elemental use, ou /elemental use <id>."), false);
        return 1;
    }

    private static int select(CommandSourceStack source, String requested) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerProgress progress = ElementalWorldData.get(source.getServer()).get(player.getUUID());
        ElementalAbility ability = ElementalAbility.byId(requested).orElse(null);
        if (ability == null || !progress.hasChosenElement() || !ability.element().id().equals(progress.elementId())) {
            source.sendFailure(Component.literal("Técnica inválida ou incompatível com seu elemento. Use /elemental skills."));
            return 0;
        }
        ElementalWorldData.get(source.getServer()).put(player.getUUID(), progress.withSelectedAbility(ability.id()));
        source.sendSuccess(() -> Component.literal("Técnica selecionada: " + ability.displayName() + ". Use /elemental use para executá-la."), false);
        return 1;
    }

    private static int useSelected(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerProgress progress = ElementalWorldData.get(source.getServer()).get(player.getUUID());
        if (progress.selectedAbility().isBlank()) {
            source.sendFailure(Component.literal("Nenhuma técnica selecionada. Use /elemental skills e /elemental select <id>."));
            return 0;
        }
        return use(source, progress.selectedAbility());
    }

    private static int use(CommandSourceStack source, String requested) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ElementalAbility ability = ElementalAbility.byId(requested).orElse(null);
        if (ability == null) {
            source.sendFailure(Component.literal("Técnica desconhecida. Consulte /elemental skills."));
            return 0;
        }
        String result = ElementalAbilityService.use(player, ability);
        boolean success = result.startsWith(ability.displayName() + " executada");
        if (success) source.sendSuccess(() -> Component.literal(result), false);
        else source.sendFailure(Component.literal(result));
        return success ? 1 : 0;
    }
}
