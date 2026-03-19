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
	private enum MuseumState {
		SUBMITTED,
		ENHANCED,
		ASCENDED
	}

	private static Map<String, MuseumState> museum; // Map of Strings of the names of museumed items : being submitted / enhanced / ascended
	private static final File FILE = new File("config/mmhelper_museum.json");
	private static final Gson GSON = new Gson();

	static {
		load();
	}

	public static void load() {
		try (Reader reader = new FileReader(FILE)) {
			museum = GSON.fromJson(reader, new TypeToken<Map<String, MuseumState>>(){}.getType());
		} catch (Exception e) {
			e.printStackTrace();
			museum = new HashMap<>();
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

	// todo use flint to check tags and make this not rely on lore

	// Should only be receiving items from the collection section of the menu
	// todo make it auto record items put in museum, changes ^
	public static void add(ItemStack element) {
		MuseumState type = entryType(element);
		String name = element.getName().getString();
		museum.put(name, type);
	}

	// Should only be checking for in-game inv items
	public static boolean contains(ItemStack element) {
		MuseumState elementType = itemType(element);
		String name = element.getName().getString();

		if (!museum.containsKey(name))
			return false;

		MuseumState storedState = museum.get(name);

		return switch (storedState) {
			case ASCENDED -> true;
			case ENHANCED -> elementType != MuseumState.ASCENDED;
			case SUBMITTED -> elementType == MuseumState.SUBMITTED;
		};
	}

	private static MuseumState entryType(ItemStack stack) {
		LoreComponent lore = stack.get(DataComponentTypes.LORE);
		if (lore == null) return MuseumState.SUBMITTED; // never should happen

		Text line = lore.lines().getLast();
		if (line.getString().equals("☑ Enhanced!"))
			return MuseumState.ENHANCED;
		if (line.getString().equals("Ω Ascended!"))
			return MuseumState.ASCENDED;
		return MuseumState.SUBMITTED;
	}

	private static MuseumState itemType(ItemStack stack) {
		LoreComponent lore = stack.get(DataComponentTypes.LORE);
		if (lore == null) return MuseumState.SUBMITTED; // never should happen

		String line = lore.lines().getLast().getString();
		// this logic could be simplified but it's easier to edit in case it's changed in the future
		if (line.matches("^(?:⚓ )?. [A-Za-z]+ . Material"))
			return MuseumState.ENHANCED;
		if (line.matches("^(?:⚓ )?Ω . [A-Za-z]+ . Material"))
			return MuseumState.ASCENDED;
		return MuseumState.SUBMITTED;
	}
}
