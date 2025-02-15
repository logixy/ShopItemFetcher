package com.example.sqlitemfetcher;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.eventbus.api.EventPriority;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

@Mod("sqlitemfetcher")
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SQLItemFetcher {

    public static final ForgeConfigSpec CONFIG;
    private static final ForgeConfigSpec.ConfigValue<String> DB_URL;
    private static final ForgeConfigSpec.ConfigValue<String> DB_USER;
    private static final ForgeConfigSpec.ConfigValue<String> DB_PASSWORD;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        DB_URL = builder.comment("Database URL").define("database.url", "jdbc:mysql://localhost:3306/minecraft");
        DB_USER = builder.comment("Database Username").define("database.user", "root");
        DB_PASSWORD = builder.comment("Database Password").define("database.password", "password");
        CONFIG = builder.build();
    }

    public SQLItemFetcher() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CONFIG);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("cart")
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                giveItemsToPlayer(player);
                return Command.SINGLE_SUCCESS;
            }));
    }

    private static void giveItemsToPlayer(ServerPlayer player) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            try (Connection connection = DriverManager.getConnection(DB_URL.get(), DB_USER.get(), DB_PASSWORD.get());
                 PreparedStatement selectStmt = connection.prepareStatement("SELECT item_id, metadata, amount FROM items WHERE uuid = ?");
                 PreparedStatement deleteStmt = connection.prepareStatement("DELETE FROM items WHERE uuid = ?")) {
                
                selectStmt.setString(1, player.getUUID().toString());
                ResultSet rs = selectStmt.executeQuery();
                boolean hasItems = false;
                
                while (rs.next()) {
                    hasItems = true;
                    int itemId = rs.getInt("item_id");
                    int metadata = rs.getInt("metadata");
                    int amount = rs.getInt("amount");
                    ItemStack stack = new ItemStack(Item.byId(itemId), amount);
                    stack.setDamageValue(metadata);
                    player.getInventory().add(stack);
                }
                
                if (hasItems) {
                    deleteStmt.setString(1, player.getUUID().toString());
                    deleteStmt.executeUpdate();
                    player.sendSystemMessage(Component.literal("Вы получили предметы из базы данных, они удалены из хранилища."));
                } else {
                    player.sendSystemMessage(Component.literal("У вас нет доступных предметов."));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            player.sendSystemMessage(Component.literal("Ошибка при получении предметов."));
        }
    }
}
