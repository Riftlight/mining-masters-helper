package com.riftlight.mmhelper;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
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
	public static KeyBinding recordCraftKey;

	@Override
	public void onInitialize() {
		KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of(MOD_ID, "miningmasters"));
		pinItemKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.mmhelper.pin",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_I,
				cat
		));
		recordCraftKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.mmhelper.record_craft",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_APOSTROPHE,
				cat
		));

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			MuseumStorage.save();
		});
	}
}
