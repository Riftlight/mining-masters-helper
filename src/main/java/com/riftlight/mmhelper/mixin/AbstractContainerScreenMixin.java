package com.riftlight.mmhelper.mixin;

import com.riftlight.mmhelper.*;
import com.riftlight.mmhelper.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;

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

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
	// Only auto-record once per screen instance
	@Unique
	private boolean recorded = false;

	// Museum slot background changer
	@Inject(method = "renderSlot", at = @At("HEAD"))
	private void onDrawSlot(GuiGraphics context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		Minecraft client = Minecraft.getInstance();

		if (!screen.getTitle().getString().contains("Museum")) return;
		if (!(slot.container instanceof Inventory)) return;

		ItemStack stack = slot.getItem();
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
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) return false;
		Component line = lore.lines().get(lore.lines().size()-2);
		return line.getString().contains("Museumable");
	}

	// Attempt to auto-record on every render until successful. Methods like onInit do not yet have slots, and capturing the packet or using drawSlot like above would be annoyingly fractured
	@Inject(method = "render", at = @At("HEAD"))
	private void onRender(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (recorded) return;

		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		if (!screen.getTitle().getString().startsWith("Viewing Recipe #")) return;

		if (tryRecordRecipe(screen))
			recorded = true;
	}

	@Unique
	private boolean tryRecordRecipe(AbstractContainerScreen<?> screen) {
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

		if (!recipeEmpty && resultSlot != null && resultSlot.hasItem()) {
			ItemStack resultItem = resultSlot.getItem();
			String resultName = resultItem.getHoverName().getString();

			RecipeStorage.saveRecipe(resultName, recipe);

			System.out.println("Auto-saved recipe for " + resultName);
			return true;
		}
		return false; // slots not ready yet, retry next frame
	}

	// When the user hovers a recipe and pins it
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	public void onKeyPressed(KeyEvent key, CallbackInfoReturnable<Boolean> cir) {
		if (key.input() == KeyBindingHelper.getBoundKeyOf(MMHelper.pinItemKey).getValue()) {
			AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
			Minecraft client = Minecraft.getInstance();

			double mouseX = client.mouseHandler.xpos() * client.getWindow().getGuiScaledWidth() / (double)client.getWindow().getScreenWidth();
			double mouseY = client.mouseHandler.ypos() * client.getWindow().getGuiScaledHeight() / (double)client.getWindow().getScreenHeight();

			Slot hoveredSlot = ((AbstractContainerScreenAccessor) screen).invokeGetHoveredSlot(mouseX, mouseY);
			if (hoveredSlot != null && hoveredSlot.hasItem()) {
				ItemStack stack = hoveredSlot.getItem();

				TaskHudOverlay.setItem(stack);
				cir.setReturnValue(true); // Mark as handled
			}
		}
	}

	private Slot getSlotAt(AbstractContainerScreen<?> screen, int gridX, int gridY) {
		// Convert (1,1) top-left into zero-based
		int x = gridX - 1;
		int y = gridY - 1;

		// Assuming row-major left-to-right, top-to-bottom
		int index = y * 9 + x; // 9 is the width of the menu
		if (index >= 0 && index < screen.getMenu().slots.size()) {
			return screen.getMenu().slots.get(index);
		}
		return null;
	}

	private Recipe captureRecipe(AbstractContainerScreen<?> screen) {
		List<Ingredient> ingredients = new ArrayList<>();
		Map<String, Integer> totals = new HashMap<>();
		ItemStack[][] grid = new ItemStack[3][3];

		ItemStack result = getSlotAt(screen, 7, 3).getItem();

		for (int y = 0; y < 3; y++) {
			for (int x = 0; x < 3; x++) {
				Slot slot = getSlotAt(screen, x + 3, y + 2);
				if (slot != null) {
					ItemStack item = slot.getItem().copy();
					String name = item.getHoverName().getString();
					boolean isNothing = item.is(Items.LIGHT_GRAY_STAINED_GLASS_PANE);
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
