package com.thebestlogin.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.thebestlogin.server.TheBestLoginServer;

import java.util.concurrent.CompletableFuture;

public final class TheBestLoginCommands {
    private TheBestLoginCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerGuiAliases(dispatcher);
        dispatcher.register(buildTheBestLoginRoot());
        dispatcher.register(buildTheBestLoginChatRoot());
    }

    private static void registerGuiAliases(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("login")
                .requires(TheBestLoginCommands::isPlayerSource)
                .executes(context -> TheBestLoginServer.get().openLoginPromptCommand(context.getSource().getPlayerOrException())));
        dispatcher.register(Commands.literal("log")
                .requires(TheBestLoginCommands::isPlayerSource)
                .executes(context -> TheBestLoginServer.get().openLoginPromptCommand(context.getSource().getPlayerOrException())));
        dispatcher.register(Commands.literal("register")
                .requires(TheBestLoginCommands::isPlayerSource)
                .executes(context -> TheBestLoginServer.get().openRegisterPromptCommand(context.getSource().getPlayerOrException())));
        dispatcher.register(Commands.literal("reg")
                .requires(TheBestLoginCommands::isPlayerSource)
                .executes(context -> TheBestLoginServer.get().openRegisterPromptCommand(context.getSource().getPlayerOrException())));
        dispatcher.register(Commands.literal("changepassword")
                .requires(TheBestLoginCommands::isPlayerSource)
                .executes(context -> TheBestLoginServer.get().openChangePasswordPromptCommand(context.getSource().getPlayerOrException())));
        dispatcher.register(Commands.literal("cp")
                .requires(TheBestLoginCommands::isPlayerSource)
                .executes(context -> TheBestLoginServer.get().openChangePasswordPromptCommand(context.getSource().getPlayerOrException())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildTheBestLoginRoot() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("thebestlogin")
                .executes(context -> executeTheBestLoginRoot(context.getSource()));

        root.then(Commands.literal("login")
                .requires(TheBestLoginCommands::isPlayerSource)
                .executes(context -> TheBestLoginServer.get().openLoginPromptCommand(context.getSource().getPlayerOrException())));
        root.then(Commands.literal("log")
                .requires(TheBestLoginCommands::isPlayerSource)
                .executes(context -> TheBestLoginServer.get().openLoginPromptCommand(context.getSource().getPlayerOrException())));
        root.then(Commands.literal("register")
                .requires(TheBestLoginCommands::isPlayerSource)
                .executes(context -> TheBestLoginServer.get().openRegisterPromptCommand(context.getSource().getPlayerOrException())));
        root.then(Commands.literal("reg")
                .requires(TheBestLoginCommands::isPlayerSource)
                .executes(context -> TheBestLoginServer.get().openRegisterPromptCommand(context.getSource().getPlayerOrException())));
        root.then(Commands.literal("changepassword")
                .requires(TheBestLoginCommands::isPlayerSource)
                .executes(context -> TheBestLoginServer.get().openChangePasswordPromptCommand(context.getSource().getPlayerOrException())));
        root.then(Commands.literal("cp")
                .requires(TheBestLoginCommands::isPlayerSource)
                .executes(context -> TheBestLoginServer.get().openChangePasswordPromptCommand(context.getSource().getPlayerOrException())));
        root.then(Commands.literal("unregister")
                .requires(source -> source.hasPermission(4))
                .then(Commands.argument("nickname", StringArgumentType.word())
                        .suggests(TheBestLoginCommands::suggestRegisteredNicknames)
                        .executes(context -> TheBestLoginServer.get().unregister(
                                context.getSource(),
                                StringArgumentType.getString(context, "nickname")
                        ))));
        root.then(Commands.literal("changepasswordnick")
                .requires(source -> source.hasPermission(4))
                .then(Commands.argument("nickname", StringArgumentType.word())
                        .suggests(TheBestLoginCommands::suggestRegisteredNicknames)
                        .then(Commands.argument("password", StringArgumentType.word())
                                .executes(context -> TheBestLoginServer.get().adminChangePassword(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "nickname"),
                                        StringArgumentType.getString(context, "password")
                                )))));
        root.then(Commands.literal("reload")
                .requires(source -> source.hasPermission(4))
                .executes(context -> TheBestLoginServer.get().reload(context.getSource())));
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildTheBestLoginChatRoot() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("thebestloginchat")
                .executes(context -> showChatUsage(context.getSource()));

        root.then(Commands.literal("login")
                .requires(TheBestLoginCommands::isPlayerSource)
                .then(Commands.argument("password", StringArgumentType.word())
                        .executes(context -> TheBestLoginServer.get().chatLoginCommand(
                                context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "password")
                        ))));
        root.then(Commands.literal("log")
                .requires(TheBestLoginCommands::isPlayerSource)
                .then(Commands.argument("password", StringArgumentType.word())
                        .executes(context -> TheBestLoginServer.get().chatLoginCommand(
                                context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "password")
                        ))));
        root.then(Commands.literal("register")
                .requires(TheBestLoginCommands::isPlayerSource)
                .then(Commands.argument("password", StringArgumentType.word())
                        .then(Commands.argument("confirmation", StringArgumentType.word())
                                .executes(context -> TheBestLoginServer.get().chatRegisterCommand(
                                        context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "password"),
                                        StringArgumentType.getString(context, "confirmation")
                                )))));
        root.then(Commands.literal("reg")
                .requires(TheBestLoginCommands::isPlayerSource)
                .then(Commands.argument("password", StringArgumentType.word())
                        .then(Commands.argument("confirmation", StringArgumentType.word())
                                .executes(context -> TheBestLoginServer.get().chatRegisterCommand(
                                        context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "password"),
                                        StringArgumentType.getString(context, "confirmation")
                                )))));
        root.then(Commands.literal("changepassword")
                .requires(TheBestLoginCommands::isPlayerSource)
                .then(Commands.argument("old_password", StringArgumentType.word())
                        .then(Commands.argument("new_password", StringArgumentType.word())
                                .then(Commands.argument("confirmation", StringArgumentType.word())
                                        .executes(context -> TheBestLoginServer.get().chatChangePasswordCommand(
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "old_password"),
                                                StringArgumentType.getString(context, "new_password"),
                                                StringArgumentType.getString(context, "confirmation")
                                        ))))));
        root.then(Commands.literal("cp")
                .requires(TheBestLoginCommands::isPlayerSource)
                .then(Commands.argument("old_password", StringArgumentType.word())
                        .then(Commands.argument("new_password", StringArgumentType.word())
                                .then(Commands.argument("confirmation", StringArgumentType.word())
                                        .executes(context -> TheBestLoginServer.get().chatChangePasswordCommand(
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "old_password"),
                                                StringArgumentType.getString(context, "new_password"),
                                                StringArgumentType.getString(context, "confirmation")
                                        ))))));
        return root;
    }

    public static boolean isTheBestLoginCommand(String rawCommand) {
        String trimmed = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        int separator = trimmed.indexOf(' ');
        String root = separator >= 0 ? trimmed.substring(0, separator) : trimmed;
        return root.equals("thebestlogin")
                || root.equals("thebestloginchat")
                || root.equals("login")
                || root.equals("log")
                || root.equals("register")
                || root.equals("reg")
                || root.equals("changepassword")
                || root.equals("cp");
    }

    private static int executeTheBestLoginRoot(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return TheBestLoginServer.get().openDefaultPromptCommand(player);
        }
        return showUsage(source);
    }

    private static int showUsage(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("GUI-команды: /thebestlogin, /login, /log, /register, /reg, /changepassword, /cp. Чат-режим: /thebestloginchat login <пароль>, /thebestloginchat register <пароль> <повтор>, /thebestloginchat changepassword <старый> <новый> <повтор>. Админ-команды: /thebestlogin unregister <ник>, /thebestlogin changepasswordnick <ник> <пароль>, /thebestlogin reload."), false);
        return 1;
    }

    private static int showChatUsage(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Чат-режим: /thebestloginchat login <пароль>, /thebestloginchat register <пароль> <повтор>, /thebestloginchat changepassword <старый> <новый> <повтор>."), false);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestRegisteredNicknames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(TheBestLoginServer.get().getRegisteredNicknames(), builder);
    }

    private static boolean isPlayerSource(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer;
    }
}
