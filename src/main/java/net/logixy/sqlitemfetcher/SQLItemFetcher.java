package net.logixy.sqlitemfetcher;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.eventbus.api.EventPriority;

import java.sql.*;

import net.minecraft.nbt.TagParser;
import net.minecraft.nbt.NbtUtils;

@Mod("sqlitemfetcher")
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SQLItemFetcher {

    public static final ForgeConfigSpec CONFIG;
    private static final ForgeConfigSpec.ConfigValue<String> DB_URL;
    private static final ForgeConfigSpec.ConfigValue<String> DB_USER;
    private static final ForgeConfigSpec.ConfigValue<String> DB_PASSWORD;
    private static final ForgeConfigSpec.ConfigValue<String> SERVNAME;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        DB_URL = builder.comment("Database URL").define("database.url", "jdbc:mysql://localhost:3306/minecraft");
        DB_USER = builder.comment("Database Username").define("database.user", "root");
        DB_PASSWORD = builder.comment("Database Password").define("database.password", "password");
        SERVNAME = builder.comment("Server Name").define("server.name", "default");
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
            })
        );

        dispatcher.register(
            LiteralArgumentBuilder.<CommandSourceStack>literal("addshop")
                .requires(source -> source.hasPermission(2)) // Только для операторов
                .then(
                    RequiredArgumentBuilder.<CommandSourceStack, String>argument("item_name", StringArgumentType.string()) // Теперь поддерживает кириллицу
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String itemName = StringArgumentType.getString(context, "item_name");
                            addItemToShopList(player, itemName);
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    addItemToShopList(player, null);
                    return Command.SINGLE_SUCCESS;
                })
        );
    }


    private static void addItemToShopList(ServerPlayer player, String itemName) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            ItemStack heldItem = player.getMainHandItem();
            if (heldItem.isEmpty()) {
                player.sendSystemMessage(Component.literal("Вы должны держать предмет в руке."));
                return;
            }

            Item item = heldItem.getItem();
            String itemNameToUse = (itemName != null) ? itemName : item.getDescriptionId();

            CompoundTag nbt = heldItem.save(new CompoundTag()); // Получаем NBT предмета
            String snbt = NbtUtils.toPrettyComponent(nbt).getString(); // Преобразуем в SNBT

            try (Connection connection = DriverManager.getConnection(DB_URL.get(), DB_USER.get(), DB_PASSWORD.get());
                PreparedStatement stmt = connection.prepareStatement("INSERT INTO shop_list (json, name, servname) VALUES (?, ?, ?)")) {
                stmt.setString(1, snbt); // Сохраняем SNBT в базу
                stmt.setString(2, itemNameToUse);
                stmt.setString(3, SERVNAME.get());
                stmt.executeUpdate();
                player.sendSystemMessage(Component.literal("Предмет успешно добавлен в магазин с именем: " + itemNameToUse));
            }
        } catch (Exception e) {
            e.printStackTrace();
            player.sendSystemMessage(Component.literal("Ошибка при добавлении предмета в магазин."));
        }
    }
    
    private static void giveItemsToPlayer(ServerPlayer player) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
    
            try (Connection connection = DriverManager.getConnection(DB_URL.get(), DB_USER.get(), DB_PASSWORD.get());
                 PreparedStatement selectStmt = connection.prepareStatement(
                     "SELECT shop_carts.id, shop_list.json, shop_carts.count FROM shop_carts " +
                     "JOIN shop_list ON shop_carts.lot = shop_list.id " +
                     "WHERE shop_carts.UUID = ? AND shop_carts.servname = ?")) {
                
                selectStmt.setString(1, player.getUUID().toString());
                selectStmt.setString(2, SERVNAME.get());
                ResultSet rs = selectStmt.executeQuery();
                boolean hasItems = false;
                boolean inventoryFull = false;
    
                while (rs.next()) {
                    hasItems = true;
                    int cartId = rs.getInt("id");
                    String snbt = rs.getString("json");
                    int count = rs.getInt("count");
    
                    CompoundTag nbt = TagParser.parseTag(snbt); // Восстанавливаем NBT-данные
                    ItemStack stack = ItemStack.of(nbt);
                    int maxStackSize = stack.getMaxStackSize(); // Макс. размер стака
    
                    // Проверяем свободные слоты в инвентаре
                    int freeSlots = getFreeSlots(player);
                    if (freeSlots == 0) {
                        inventoryFull = true;
                        continue; // Переходим к следующему предмету, если инвентарь заполнен
                    }
    
                    int givenAmount = 0; // Сколько предметов реально выдано
    
                    if (maxStackSize == 1) {
                        // Нестекаемые предметы раздаются поштучно
                        for (int i = 0; i < count && freeSlots > 0; i++) {
                            ItemStack singleItem = stack.copy();
                            singleItem.setCount(1);
                            player.getInventory().add(singleItem);
                            givenAmount++;
                            freeSlots--;
                        }
                    } else {
                        // Стэкаемые предметы выдаются полными стаками
                        while (count > 0 && freeSlots > 0) {
                            int giveAmount = Math.min(count, maxStackSize);
                            ItemStack giveStack = stack.copy();
                            giveStack.setCount(giveAmount);
                            player.getInventory().add(giveStack);
                            givenAmount += giveAmount;
                            count -= giveAmount;
                            freeSlots--;
                        }
                    }
    
                    int remainingAmount = count - givenAmount; // Сколько предметов осталось
    
                    if (remainingAmount > 0) {
                        // Если что-то осталось, обновляем shop_carts
                        try (PreparedStatement updateStmt = connection.prepareStatement(
                                "UPDATE shop_carts SET count = ? WHERE id = ?")) {
                            updateStmt.setInt(1, remainingAmount);
                            updateStmt.setInt(2, cartId);
                            updateStmt.executeUpdate();
                        }
                    } else {
                        // Если всё выдали, удаляем запись из shop_carts
                        try (PreparedStatement deleteStmt = connection.prepareStatement(
                                "DELETE FROM shop_carts WHERE id = ?")) {
                            deleteStmt.setInt(1, cartId);
                            deleteStmt.executeUpdate();
                        }
                    }
                }
    
                if (inventoryFull) {
                    player.sendSystemMessage(Component.literal("Ваш инвентарь переполнен, часть предметов осталась в корзине!"));
                } else if (hasItems) {
                    player.sendSystemMessage(Component.literal("Вы получили предметы из корзины."));
                } else {
                    player.sendSystemMessage(Component.literal("У вас нет доступных предметов."));
                }
    
            }
        } catch (Exception e) {
            e.printStackTrace();
            player.sendSystemMessage(Component.literal("Ошибка при получении предметов."));
        }
    }

    private static int getFreeSlots(ServerPlayer player) {
        int freeSlots = 0;
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            if (player.getInventory().items.get(i).isEmpty()) {
                freeSlots++;
            }
        }
        return freeSlots;
    }
    
}
