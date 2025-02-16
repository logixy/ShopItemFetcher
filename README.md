# ShopItemFetcher
A small mod that allows you to add/receive items from the database.
To support MySQL, install an additional-https://www.curseforge.com/minecraft/mc-mods/Mysql-jdbc


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