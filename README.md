# SQLItemFetcher Mod for Minecraft
A small mod that allows you to add/receive items from the database.
To support MySQL, install an additional-https://www.curseforge.com/minecraft/mc-mods/Mysql-jdbc

## Overview

`SQLItemFetcher` is a Minecraft mod that allows players to fetch items from a custom in-game store or cart system stored in a MySQL database. The mod adds commands that enable players to receive items they have previously purchased or added to their shopping cart. It uses a database to store item data and integrates directly with the Minecraft inventory system.

## Features

- **Database Integration**: The mod stores items and shopping cart information in a MySQL database.
- **Commands**:
  - `/cart`: Allows players to fetch items from their shopping cart stored in the database.
  - `/addshop <item_name>`: Enables server operators to add items to the shop list. Items can be added by either specifying the item name or by using the item the player is holding in their hand.

## Configuration

The mod comes with configurable settings for the MySQL connection, allowing you to define:
- **Database URL** (`database.url`): The URL to your MySQL server.
- **Database Username** (`database.user`): The username for accessing the MySQL database.
- **Database Password** (`database.password`): The password for accessing the MySQL database.
- **Server Name** (`server.name`): The name of the server, used to differentiate between different game worlds.

These settings can be configured through the mod's configuration file.

### Default Configuration

```toml
[database]
url = "jdbc:mysql://localhost:3306/minecraft"
user = "root"
password = "password"

[server]
name = "default"

## How to Use
### 1. Add Items to Shop

Server operators can add items to the shop by using the /addshop command. This command requires an item in the player's hand. Optionally, you can provide an item name as a parameter.

    Command Syntax: /addshop <item_name>
        If no item name is provided, the item held by the player will be used.
        Example: /addshop diamond_sword

If the player is holding an item, it will be saved to the database along with its NBT (Named Binary Tag) data, making it possible to recover the exact item later.
### 2. Fetch Items from Your Cart

Players can fetch the items they have saved in their shopping cart with the /cart command. This command retrieves items stored in the database and adds them to the player's inventory, if space allows. If the inventory is full, the remaining items stay in the cart for later retrieval.

    Command Syntax: /cart

### 3. Configuration

You can configure the database settings (URL, username, password, server name) in the config.toml file located in the mod's config folder. These settings are used to connect to your MySQL database and store/retrieve item data.
Technical Details

    Database Schema: The mod assumes a MySQL database with two tables: shop_list (stores the items) and shop_carts (stores player cart information).

## Technical Details

- Database Schema: The mod assumes a MySQL database with two tables: shop_list (stores the items) and shop_carts (stores player cart information).

## SQL Schema Example

```sql
CREATE TABLE `shop_carts` (
 `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
 `UUID` varchar(60) NOT NULL,
 `lot` int(11) NOT NULL,
 `count` int(11) NOT NULL,
 `servname` varchar(20) NOT NULL,
 PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=792 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci
CREATE TABLE `shop_list` (
 `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
 `json` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
 `name` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
 `servname` varchar(20) NOT NULL,
 PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=119 DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci
```

- Item Data Storage: The mod stores the items in the `shop_list` table in SNBT format. The `shop_carts` table contains the items that are associated with a player's cart.


## Troubleshooting

   - Connection Issues: Make sure your MySQL database is up and running, and check that the connection details in the config file are correct.
   - Inventory Full: If your inventory is full, some items may not be added. Clear up some space or try again.