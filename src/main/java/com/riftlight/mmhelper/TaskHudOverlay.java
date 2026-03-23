package com.riftlight.mmhelper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.FileReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.*;

public class TaskHudOverlay implements ModInitializer {

	private static class IngredientEntry {
		String name;
		int amount;
		String description;
		boolean playerHas;

		IngredientEntry(String name, int amount, String description) {
			this.name = name;
			this.amount = amount;
			this.description = description;
		}
	}

	private static ItemStack targetItem;
	private static Map<String, String> importedDescriptions;
	private static List<IngredientEntry> ingredients = new ArrayList<>();

	static {
		Gson gson = new Gson();
		// LOAD INGREDIENT DESCRIPTIONS
		Type ingredientType = new TypeToken<Map<String, String>>(){}.getType();
		try (Reader reader = new FileReader("config/mmhelper_ingredients.json")) {
			importedDescriptions = gson.fromJson(reader, ingredientType);
		} catch (Exception e) {
			e.printStackTrace();
			importedDescriptions = new HashMap<>();
		}
	}

	// TODO it would probably be nicer to transfer all initialization logic to MMHelper
	@Override
	public void onInitialize() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MMHelper.MOD_ID, "before_chat"), TaskHudOverlay::renderList);

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof AbstractContainerScreen<?> handledScreen)
				ScreenEvents.afterRender(screen).register((screen1, drawContext, mouseX, mouseY, tickDelta) -> {
					renderGrid(drawContext, handledScreen);
				});
		});
	}

	private static void renderList(GuiGraphics context, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();

		if (client == null || targetItem == null) return;
		int x = client.getWindow().getGuiScaledWidth();

		context.drawString(client.font, targetItem.getHoverName(), x - client.font.width(targetItem.getHoverName()) - 5, 10, 0xFF00FF00, true);

		Recipe recipe = RecipeStorage.getRecipe(targetItem.getHoverName().getString());
		if (recipe == null)
			return;

		if (client.screen != null && client.screen.getTitle().getString().equals("Crafting Menu")) return;

		int currY = 23;
		for (IngredientEntry ingredient : ingredients) {
			ingredient.playerHas = playerHasItem(ingredient.name, ingredient.amount);
		}

		ingredients.sort((a, b) -> {
			if (a.playerHas == b.playerHas) return Integer.compare(b.amount, a.amount);
			return a.playerHas ? 1 : -1; // Put the already achieved items at the bottom
		});

		for (IngredientEntry ingredient : ingredients) {
			boolean owned = ingredient.playerHas;

			// Color is green if missing, gray if already made
			int color = owned ? 0xFF777777 : 0xFF00FF00;

			Component text = Component.literal(ingredient.name + " x" + ingredient.amount);
			if (owned) text = text.copy().withStyle(s -> s.withStrikethrough(true));
			context.drawString(client.font, text, x - client.font.width(text) - 5, currY, color, true);
			currY += 10;

			if (ingredient.description == null || ingredient.playerHas) continue;
			// Draw description smaller
			float scale = 0.6f;
			context.pose().pushMatrix();
			context.pose().scale(scale, scale);
			int descX = (int) ((x / scale - client.font.width(ingredient.description) - 5));
			int descY = (int) (currY / scale);

			context.drawString(client.font, ingredient.description, descX, descY, 0xFFAAAAAA, true);

			context.pose().popMatrix();
			currY += (int) (10 * scale);
		}

	}

	private static void renderGrid(GuiGraphics context, AbstractContainerScreen<?> screen) {
		Minecraft client = Minecraft.getInstance();

		if (client == null || targetItem == null) return;

		Recipe recipe = RecipeStorage.getRecipe(targetItem.getHoverName().getString());
		if (recipe == null) return;
		int x = client.getWindow().getGuiScaledWidth();

		if (client.screen != null && client.screen.getTitle().getString().equals("Crafting Menu")) {
			int xStart = x - 20;
			for (int row = 0; row < 3; row++) {
				for (int col = 0; col < 3; col++) {
					ItemStack item = recipe.getGrid()[row][2 - col]; // 2-col because we're rendering right-to-left, xStart is on the rightmost edge and we subtract as we go
					int slotX = xStart - col * 18;
					int slotY = 23 + row * 18;

					context.blitSprite(
							RenderPipelines.GUI_TEXTURED,
							Identifier.withDefaultNamespace("container/slot"),
							slotX - 1,
							slotY - 1,
							18, // 18 because each slot is 16x16px and 2px dividers between each
							18
					);
					if (item == null) continue;
					context.renderItem(item, slotX, slotY);
				}
			}
		}
	}

	public static void setItem(ItemStack newItem) {
		targetItem = newItem;
		ingredients.clear();

		String rawName = newItem.getHoverName().getString();
		System.out.println("rawName: " + rawName);
		Recipe recipe = RecipeStorage.getRecipe(rawName);

		if (recipe == null) {
			Minecraft.getInstance().player.displayClientMessage(Component.literal("Could not retrieve a recipe for that item!"), false);
			return;
		}

		for (Ingredient ing : recipe.getIngredients()) {
			String desc = importedDescriptions.get(ing.getName());
			ingredients.add(new IngredientEntry(ing.getName(), ing.getAmount(), desc));
		}

		ingredients.sort((a, b) -> Integer.compare(b.amount, a.amount));
	}

	private static boolean playerHasItem(String ingredientName, int requiredAmount) {
		Minecraft client = Minecraft.getInstance();
		int total = 0;
		for (ItemStack stack : client.player.getInventory().getNonEquipmentItems()) {
			if (stack.getHoverName().getString().equalsIgnoreCase(ingredientName)) {
				total += stack.getCount();
				if (total >= requiredAmount) return true;
			}
		}
		return false;
	}
}