package com.riftlight.mmhelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.item.ItemStack;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class RecipeStorage {
	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.registerTypeAdapter(ItemStack.class, new ItemStackGSONAdapter())
			.create();
	private static final File FILE = new File("config/mmhelper_recipes.json");
	private static Map<String, Recipe> RECIPES;

	static {
		load();
	}

	private static void load() {
		if (FILE.exists()) {
			try (Reader reader = new FileReader(FILE)) {
				Type type = new TypeToken<Map<String, Recipe>>(){}.getType();
				RECIPES = GSON.fromJson(reader, type);
			} catch (IOException e) {
				e.printStackTrace();
				RECIPES = new HashMap<>();
			}
		} else {
			RECIPES = new HashMap<>();
		}
	}

	public static void saveRecipe(String keyItem, Recipe recipe) {
		RECIPES.put(keyItem, recipe);
		save();
	}

	private static void save() {
		try {
			FILE.getParentFile().mkdirs();
			try (Writer writer = new FileWriter(FILE)) {
				GSON.toJson(RECIPES, writer);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static Recipe getRecipe(String keyItem) {
		return RECIPES.get(keyItem);
	}
}
