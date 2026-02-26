package com.riftlight.mmhelper.mixin;

import com.riftlight.mmhelper.MuseumStorage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
	@Unique private String currentMuseumPage = null;
	@Unique private boolean pageProcessed = false;
	@Unique private final Set<Integer> receivedSlots = new HashSet<>();
	@Unique private static final int TOTAL_GRID_SLOTS = 28;

	// This may not be entirely necessary, as in theory slot update is only when an individual slot is updated and not a whole inv sent, but theres little point in not having it
	@Inject(method = "onScreenHandlerSlotUpdate", at = @At("TAIL"))
	private void onSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
		handleSlotUpdate(packet.getSyncId(), packet.getSlot(), packet.getStack());
	}

	@Inject(method = "onInventory", at = @At("TAIL"))
	private void onInventory(InventoryS2CPacket packet, CallbackInfo ci) {
		int syncId = packet.syncId();
		List<ItemStack> contents = packet.contents();
		for (int slot = 0; slot < contents.size(); slot++) {
			handleSlotUpdate(syncId, slot, contents.get(slot));
		}
	}

	@Unique
	private void handleSlotUpdate(int syncId, int slotId, ItemStack stack) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!(client.currentScreen instanceof HandledScreen<?> handled) ||
				!client.currentScreen.getTitle().getString().startsWith("Museum Page")) return;

		String pageTitle = client.currentScreen.getTitle().getString();

		// Reset when page changes
		if (!pageTitle.equals(currentMuseumPage)) {
			currentMuseumPage = pageTitle;
			receivedSlots.clear();
			pageProcessed = false;
		}

		// Already processed this page, dont process further
		if (pageProcessed) return;

		// Only process packets belonging to this screen
		if (syncId != handled.getScreenHandler().syncId) return;

		if (isInGrid(slotId) && !stack.isEmpty()) {
			receivedSlots.add(slotId);
			if (receivedSlots.size() >= TOTAL_GRID_SLOTS) {
				pageProcessed = true; // prevent multiple triggers
				client.send(() -> processMuseumPage(handled));
			}
		}
	}

	@Unique
	private boolean isInGrid(int slot) {
		return (slot >= 9 && slot <= 15) ||
				(slot >= 18 && slot <= 24) ||
				(slot >= 27 && slot <= 33) ||
				(slot >= 36 && slot <= 42);
	}

	@Unique
	private void processMuseumPage(HandledScreen<?> screen) {
		for (int y = 2; y <= 5; y++) {
			for (int x = 2; x <= 8; x++) {
				int index = (y - 1) * 9 + (x - 1);
				ItemStack stack = screen.getScreenHandler().slots.get(index).getStack();
				Text name = stack.getName();

				if (hasObfuscatedStyle(name)) continue;
				System.out.println("past obf check");
				MuseumStorage.add(stack);
			}
		}
	}

	@Unique
	private boolean hasObfuscatedStyle(Text text) {
		return hasObfuscatedStyle(text, false);
	}
	@Unique
	private boolean hasObfuscatedStyle(Text text, boolean currentObfuscated) {
		Style style = text.getStyle();
		if (style.isObfuscated()) return true;

		for (Text sibling : text.getSiblings()) {
			if (hasObfuscatedStyle(sibling)) return true;
		}

		return false;
	}

}