package com.cordraconis.registre;

import com.cordraconis.CorDraconis;
import com.cordraconis.forge.ForgeRuniqueMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/**
 * Types de menus (interfaces avec des emplacements d'objets).
 *
 * NOTION — menu vs écran : le MENU (côté serveur) gère les emplacements et la logique ;
 * l'ÉCRAN (côté client) ne fait que l'afficher. Les deux sont reliés par ce MenuType.
 */
public final class ModMenus {

	public static final MenuType<ForgeRuniqueMenu> FORGE_RUNIQUE = Registry.register(
		BuiltInRegistries.MENU,
		CorDraconis.id("forge_runique"),
		new MenuType<>(ForgeRuniqueMenu::new, FeatureFlags.VANILLA_SET)
	);

	private ModMenus() {
	}

	public static void initialiser() {
	}
}
