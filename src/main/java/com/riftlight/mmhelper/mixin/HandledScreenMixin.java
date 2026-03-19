package com.riftlight.mmhelper.mixin;

import com.riftlight.mmhelper.*;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(HandledScreen.class)
public class HandledScreenMixin {
	// Only auto-record once per screen instance
	@Unique
	private boolean recorded = false;

	// Museum slot background changer
	@Inject(method = "drawSlot", at = @At("HEAD"))
	private void onDrawSlot(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
		MinecraftClient client = MinecraftClient.getInstance();

		if (!screen.getTitle().getString().contains("Museum")) return;
		if (!(slot.inventory instanceof PlayerInventory)) return;

		ItemStack stack = slot.getStack();
		if (stack.isEmpty()) return;
		if (!hasMuseumableLore(stack)) return;

		if (MuseumStorage.contains(stack)) return;

		// draw background rectangle
		int slotX = slot.x;
		int slotY = slot.y;

		context.fill(slotX, slotY, slotX + 16, slotY + 16, 0x60d81d1d);
	}

	@Unique
	private boolean hasMuseumableLore(ItemStack stack) {
		LoreComponent lore = stack.get(DataComponentTypes.LORE);
		if (lore == null) return false;
		Text line = lore.lines().get(lore.lines().size()-2);
		return line.getString().contains("Museumable");
	}

	// Attempt to auto-record on every render until successful. Methods like onInit do not yet have slots, and capturing the packet or using drawSlot like above would be annoyingly fractured
	@Inject(method = "render", at = @At("HEAD"))
	private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (recorded) return;

		HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
		if (!screen.getTitle().getString().startsWith("Viewing Recipe #")) return;

		if (tryRecordRecipe(screen))
			recorded = true;
	}

	@Unique
	private boolean tryRecordRecipe(HandledScreen<?> screen) {
		Slot resultSlot = getSlotAt(screen, 7, 3);
		if (resultSlot == null)
			return false;
		Recipe recipe = captureRecipe(screen);

		boolean recipeEmpty = true;
		outer: for (ItemStack[] row : recipe.getGrid())
			for (ItemStack i : row)
				if (i != null) {
					recipeEmpty = false;
					break outer;
				}

		if (!recipeEmpty && resultSlot != null && resultSlot.hasStack()) {
			ItemStack resultItem = resultSlot.getStack();
			String resultName = resultItem.getName().getString();

			RecipeStorage.saveRecipe(resultName, recipe);

			System.out.println("Auto-saved recipe for " + resultName);
			return true;
		}
		return false; // slots not ready yet, retry next frame
	}

	// When the user hovers a recipe and pins it
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	public void onKeyPressed(KeyInput key, CallbackInfoReturnable<Boolean> cir) {
		if (key.getKeycode() == KeyBindingHelper.getBoundKeyOf(MMHelper.pinItemKey).getCode()) {
			HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
			MinecraftClient client = MinecraftClient.getInstance();

			double mouseX = client.mouse.getX() * client.getWindow().getScaledWidth() / (double)client.getWindow().getWidth();
			double mouseY = client.mouse.getY() * client.getWindow().getScaledHeight() / (double)client.getWindow().getHeight();

			Slot hoveredSlot = ((HandledScreenAccessor) screen).invokeGetSlotAt(mouseX, mouseY);
			if (hoveredSlot != null && hoveredSlot.hasStack()) {
				ItemStack stack = hoveredSlot.getStack();

				TaskHudOverlay.setItem(stack);
				cir.setReturnValue(true); // Mark as handled
			}
		}
	}

	private Slot getSlotAt(HandledScreen<?> screen, int gridX, int gridY) {
		// Convert (1,1) top-left into zero-based
		int x = gridX - 1;
		int y = gridY - 1;

		// Assuming row-major left-to-right, top-to-bottom
		int index = y * 9 + x; // 9 is the width of the menu
		if (index >= 0 && index < screen.getScreenHandler().slots.size()) {
			return screen.getScreenHandler().slots.get(index);
		}
		return null;
	}

	private Recipe captureRecipe(HandledScreen<?> screen) {
		List<Ingredient> ingredients = new ArrayList<>();
		Map<String, Integer> totals = new HashMap<>();
		ItemStack[][] grid = new ItemStack[3][3];

		ItemStack result = getSlotAt(screen, 7, 3).getStack();

		for (int y = 0; y < 3; y++) {
			for (int x = 0; x < 3; x++) {
				Slot slot = getSlotAt(screen, x + 3, y + 2);
				if (slot != null) {
					ItemStack item = slot.getStack().copy();
					String name = item.getName().getString();
					boolean isNothing = item.isOf(Items.LIGHT_GRAY_STAINED_GLASS_PANE);
					grid[y][x] = isNothing ? null : item;
					if (!name.isEmpty() && !isNothing)
						totals.put(name, totals.getOrDefault(name, 0) + 1);
				} else {
					grid[y][x] = null;
				}
			}
		}

		for (Map.Entry<String, Integer> e : totals.entrySet()) {
			ingredients.add(new Ingredient(e.getKey(), e.getValue()));
		}

		return new Recipe(result, ingredients, grid);
	}
}
