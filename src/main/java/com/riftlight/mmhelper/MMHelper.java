package com.riftlight.mmhelper;

import com.riftlight.mmhelper.screen.RecipeBrowserScreen;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MMHelper implements ModInitializer {
	public static final String MOD_ID = "mmhelper";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static KeyBinding pinItemKey;
	public static KeyBinding openRecipeBrowserKey;

	@Override
	public void onInitialize() {
		KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of(MOD_ID, "miningmasters"));
		pinItemKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.mmhelper.pin",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_I,
				cat
		));
		// eventually should be merged with above key
		openRecipeBrowserKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.mmhelper.open_recipe_browser",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_G,
				cat
		));

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			MuseumStorage.save();
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openRecipeBrowserKey.wasPressed()) { // frankly i have no clue why it's done this way but this is what people do
				client.setScreen(new RecipeBrowserScreen());
			}
		});
	}
}
