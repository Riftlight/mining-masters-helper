package com.riftlight.mmhelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class IngredientJsonGenner {
	public static void main(String[] args) {
		try {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();

			// Load recipes
			Type recipeType = new TypeToken<Map<String, String[][]>>(){}.getType();
			Map<String, String[][]> recipes;
			try (Reader reader = new FileReader("config/mmhelper_recipes.json")) {
				recipes = gson.fromJson(reader, recipeType);
			}

			if (recipes == null) recipes = new HashMap<>();

			// Build ingredients map
			Map<String, String> ingredients = new HashMap<>();
			Set<String> recipeKeys = recipes.keySet(); // all craftable items

			for (String[][] recipeGrid : recipes.values()) {
				for (String[] row : recipeGrid) {

						if (!recipeKeys.contains(row[0])) {
							ingredients.putIfAbsent(row[0], "This is an ingredient. hopefully.");
					}
				}
			}

			// Save ingredients JSON
			File outFile = new File("config/mmhelper_ingredients.json");
			outFile.getParentFile().mkdirs();
			try (Writer writer = new FileWriter(outFile)) {
				gson.toJson(ingredients, writer);
			}

			System.out.println("Ingredients JSON saved with " + ingredients.size() + " entries.");

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
