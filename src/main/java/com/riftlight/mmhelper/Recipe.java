package com.riftlight.mmhelper;

import net.minecraft.item.ItemStack;

import java.util.List;

public class Recipe {
	private List<Ingredient> ingredients;
	private ItemStack[][] grid;

	public Recipe(List<Ingredient> ingredients, ItemStack[][] grid) {
		this.ingredients = ingredients;
		this.grid = grid;
	}

	public List<Ingredient> getIngredients() { return this.ingredients; }
	public ItemStack[][] getGrid() { return grid; }
}
