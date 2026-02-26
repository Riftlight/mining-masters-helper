package com.riftlight.mmhelper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class MuseumStorage {
	private static Map<String, Boolean> museum; // Set of Strings of the names of the items in the museum
	private static final File FILE = new File("config/mmhelper_museum.json");
	private static final Gson GSON = new Gson();

	static {
		load();
	}

	public static void load() {
		try (Reader reader = new FileReader(FILE)) {
			museum = GSON.fromJson(reader, new TypeToken<Map<String, Boolean>>(){}.getType());
		} catch (Exception e) {
			e.printStackTrace();
			museum = new HashMap<String, Boolean>();
		}
	}

	public static void save() { // registered for CLIENT_STOPPING
		try {
			FILE.getParentFile().mkdirs();
			try (Writer writer = new FileWriter(FILE)) {
				GSON.toJson(museum, writer);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void clear() {
		museum.clear();
	}

	// Should only be receiving items from the collection section of the menu
	// todo make it auto record items put in museum, changes ^
	public static void add(ItemStack element) {
		boolean enh = isEnhancedMuseumEntry(element);
		String name = element.getName().getString();
		museum.put(name, enh);
	}

	// Should only be checking for in-game inv items
	public static boolean contains(ItemStack element) {
		boolean enh = isEnhancedItem(element);
		String name = element.getName().getString();
		return museum.containsKey(name) && (museum.get(name) || museum.get(name) == enh); // if it's stored as enhanced (true), it's also stored as regular (false)
	}

	private static boolean isEnhancedMuseumEntry(ItemStack stack) {
		LoreComponent lore = stack.get(DataComponentTypes.LORE);
		if (lore == null) return false;
		Text line = lore.lines().getLast();
		return (line.getString().equals("☑ Enhanced!"));
	}

	private static boolean isEnhancedItem(ItemStack stack) {
		LoreComponent lore = stack.get(DataComponentTypes.LORE);
		if (lore == null) return false;
		Text line = lore.lines().getLast();
		return line.getString().matches("^. [A-Za-z]+ . Material");
	}
}
