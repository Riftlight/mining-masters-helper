package com.riftlight.mmhelper;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.JsonOps;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

import java.lang.reflect.Type;
import java.util.Optional;
import java.util.UUID;

public class ItemStackGSONAdapter implements JsonSerializer<ItemStack>, JsonDeserializer<ItemStack> {
	@Override
	public JsonElement serialize(ItemStack stack, Type typeOfSrc, JsonSerializationContext context) {
		if (stack == null || stack.isEmpty()) return JsonNull.INSTANCE;

		JsonObject obj = new JsonObject();
		Identifier id = Registries.ITEM.getId(stack.getItem());
		obj.addProperty("item", id.toString());

		// Serialize custom name
		if (stack.getCustomName() != null) {
			TextCodecs.CODEC.encodeStart(JsonOps.INSTANCE, stack.getCustomName())
					.resultOrPartial(error -> {
						MMHelper.LOGGER.error("Failed to serialize Text: {}", error);
					})
					.ifPresent(nameJson -> {
						obj.add("name", nameJson);
					});
		}

		// Serialize profile component with its codec
		ProfileComponent comp = stack.get(DataComponentTypes.PROFILE);
		if (id.equals(Identifier.of("minecraft:player_head")) && comp != null) {
			ProfileComponent.CODEC.encodeStart(JsonOps.INSTANCE, comp)
					.resultOrPartial(error -> {
						MMHelper.LOGGER.error("Failed to serialize ProfileComponent: {}", error);
					})
					.ifPresent(profileJson -> {
						obj.add("profile", profileJson);
					});
		}

		return obj;
	}

	@Override
	public ItemStack deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
		if (json == null || json.isJsonNull()) return ItemStack.EMPTY;
		JsonObject obj = json.getAsJsonObject();

		String itemId = obj.get("item").getAsString();
		Item item = Registries.ITEM.get(Identifier.of(itemId));
		ItemStack stack = new ItemStack(item);

		// Deserialize custom name with TextCodecs
		if (obj.has("name") && !obj.get("name").isJsonNull()) {
			TextCodecs.CODEC.parse(JsonOps.INSTANCE, obj.get("name"))
					.resultOrPartial(error -> {
						MMHelper.LOGGER.error("Failed to deserialize Text: {}", error);
					})
					.ifPresent(text -> {
						stack.set(DataComponentTypes.CUSTOM_NAME, text);
					});
		}

		// Deserialize profile component with its codec
		if (obj.has("profile") && !obj.get("profile").isJsonNull()) {
			ProfileComponent.CODEC.parse(JsonOps.INSTANCE, obj.get("profile"))
					.resultOrPartial(error -> {
						MMHelper.LOGGER.error("Failed to deserialize ProfileComponent: {}", error);
					})
					.ifPresent(profileComp -> {
						stack.set(DataComponentTypes.PROFILE, profileComp);
					});
		}

		return stack;
	}
}