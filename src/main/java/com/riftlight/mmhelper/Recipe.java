package com.riftlight.mmhelper;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public class Recipe {
	private ItemStack resultItem;
	private List<Ingredient> ingredients;
	private ItemStack[][] grid;

	public Recipe(ItemStack resultItem, List<Ingredient> ingredients, ItemStack[][] grid) {
		this.resultItem = resultItem;
		this.ingredients = ingredients;
		this.grid = grid;
	}

	public List<Ingredient> getIngredients() { return this.ingredients; }
	public ItemStack[][] getGrid() { return grid; }
	public ItemStack getResult() { return this.resultItem; }

	public boolean matchesSearch(String query) {
		String lowerQuery = query.toLowerCase();
		for (Ingredient i : ingredients) {
			if (i.getName().toLowerCase().contains(lowerQuery)) return true;
		}
		return false;
	}
}
