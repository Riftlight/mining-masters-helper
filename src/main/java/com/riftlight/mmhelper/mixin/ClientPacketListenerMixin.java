package com.riftlight.mmhelper.mixin;

import com.riftlight.mmhelper.MuseumStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Unique private String currentMuseumPage = null;
	@Unique private boolean pageProcessed = false;
	@Unique private final Set<Integer> receivedSlots = new HashSet<>();
	@Unique private static final int TOTAL_GRID_SLOTS = 28;

	// This may not be entirely necessary, as in theory slot update is only when an individual slot is updated and not a whole inv sent, but theres little point in not having it
	@Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
	private void onSlotUpdate(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
		handleSlotUpdate(packet.getContainerId(), packet.getSlot(), packet.getItem());
	}

	@Inject(method = "handleContainerContent", at = @At("TAIL"))
	private void onInventory(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
		int syncId = packet.containerId();
		List<ItemStack> contents = packet.items();
		for (int slot = 0; slot < contents.size(); slot++) {
			handleSlotUpdate(syncId, slot, contents.get(slot));
		}
	}

	@Unique
	private void handleSlotUpdate(int syncId, int slotId, ItemStack stack) {
		Minecraft client = Minecraft.getInstance();
		if (!(client.screen instanceof AbstractContainerScreen<?> handled) ||
				!client.screen.getTitle().getString().startsWith("Museum Page")) return;

		String pageTitle = client.screen.getTitle().getString();

		// Reset when page changes
		if (!pageTitle.equals(currentMuseumPage)) {
			currentMuseumPage = pageTitle;
			receivedSlots.clear();
			pageProcessed = false;
		}

		// Already processed this page, dont process further
		if (pageProcessed) return;

		// Only process packets belonging to this screen
		if (syncId != handled.getMenu().containerId) return;

		if (isInGrid(slotId) && !stack.isEmpty()) {
			receivedSlots.add(slotId);
			if (receivedSlots.size() >= TOTAL_GRID_SLOTS) {
				pageProcessed = true; // prevent multiple triggers
				client.schedule(() -> processMuseumPage(handled));
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
	private void processMuseumPage(AbstractContainerScreen<?> screen) {
		for (int y = 2; y <= 5; y++) {
			for (int x = 2; x <= 8; x++) {
				int index = (y - 1) * 9 + (x - 1);
				ItemStack stack = screen.getMenu().slots.get(index).getItem();
				Component name = stack.getHoverName();

				if (hasObfuscatedStyle(name)) continue;
				System.out.println("past obf check");
				MuseumStorage.add(stack);
			}
		}
	}

	@Unique
	private boolean hasObfuscatedStyle(Component text) {
		return hasObfuscatedStyle(text, false);
	}
	@Unique
	private boolean hasObfuscatedStyle(Component text, boolean currentObfuscated) {
		Style style = text.getStyle();
		if (style.isObfuscated()) return true;

		for (Component sibling : text.getSiblings()) {
			if (hasObfuscatedStyle(sibling)) return true;
		}

		return false;
	}

}