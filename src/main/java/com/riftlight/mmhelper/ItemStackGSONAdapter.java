package com.riftlight.mmhelper;

import com.google.gson.*;

import com.mojang.serialization.JsonOps;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.*;
import net.minecraft.util.Identifier;

import java.lang.reflect.Type;
import java.util.List;

public class ItemStackGSONAdapter implements JsonSerializer<ItemStack>, JsonDeserializer<ItemStack> {

	private Text findLeaf(Text text) {
		List<Text> siblings = text.getSiblings();

		// Using the literal string because getString() on the basic object concatenates children
		String direct = text.getContent() instanceof PlainTextContent.Literal lit ? lit.string() : null;

		if (direct != null && !direct.isEmpty() && siblings.isEmpty()) {
			return text;
		}

		// Filter out empty/styling-only siblings and recurse into real ones
		List<Text> meaningful = siblings.stream()
				.filter(s -> !s.getString().isEmpty())
				.toList();

		if (meaningful.size() == 1) {
			Text child = meaningful.getFirst();
			Text found = findLeaf(child);
			// Inherit color from parent if child has none
			TextColor color = text.getStyle().getColor();
			if (found != null && found.getStyle().getColor() == null && color != null) {
				return Text.literal(found.getString()).setStyle(Style.EMPTY.withColor(color));
			}
			return found;
		}

		return null;
	}

	private JsonElement simplifyText(Text text) {
		if (text == null) return JsonNull.INSTANCE;

		Text leaf = findLeaf(text);
		if (leaf != null) {
			String content = leaf.getString();
			TextColor color = leaf.getStyle().getColor();
			if (content != null && !content.isEmpty()) {
				if (color != null) {
					JsonObject simplified = new JsonObject();
					simplified.addProperty("color", color.getName());
					simplified.addProperty("text", content);
					return simplified;
				} else {
					return new JsonPrimitive(content);
				}
			}
		}
		// Couldn't do nice simplification, fully serialize
		JsonObject obj = new JsonObject();
		String content = text.getString();
		if (!content.isEmpty()) obj.addProperty("text", content);

		TextColor color = text.getStyle().getColor();
		if (color != null) obj.addProperty("color", color.getName());

		Style style = text.getStyle();
		if (Boolean.TRUE.equals(style.isBold())) obj.addProperty("bold", true);
		if (Boolean.TRUE.equals(style.isItalic())) obj.addProperty("italic", true);
		if (Boolean.TRUE.equals(style.isUnderlined())) obj.addProperty("underlined", true);
		if (Boolean.TRUE.equals(style.isStrikethrough())) obj.addProperty("strikethrough", true);
		if (Boolean.TRUE.equals(style.isObfuscated())) obj.addProperty("obfuscated", true);

		List<Text> siblings = text.getSiblings();
		if (!siblings.isEmpty()) {
			JsonArray extraArray = new JsonArray();
			for (Text sibling : siblings)
				extraArray.add(simplifyText(sibling));
			obj.add("extra", extraArray);
		}

		return obj;
	}

	private Text reconstructText(JsonElement element) {
		if (element == null || element.isJsonNull()) return Text.empty();

		if (element.isJsonPrimitive())
			return Text.literal(element.getAsString());

		JsonObject obj = element.getAsJsonObject();
		MutableText text;

		if (obj.has("text"))
			text = Text.literal(obj.get("text").getAsString());
		else
			text = Text.empty();

		Style style = Style.EMPTY
				.withColor(parseColor(obj))
				.withBold(obj.has("bold") && obj.get("bold").getAsBoolean() ? true : null)
				.withItalic(obj.has("italic") && obj.get("italic").getAsBoolean() ? true : null)
				.withUnderline(obj.has("underlined") && obj.get("underlined").getAsBoolean() ? true : null)
				.withStrikethrough(obj.has("strikethrough") && obj.get("strikethrough").getAsBoolean() ? true : null)
				.withObfuscated(obj.has("obfuscated") && obj.get("obfuscated").getAsBoolean() ? true : null);

		text.setStyle(style);

		if (obj.has("extra") && obj.get("extra").isJsonArray()) {
			JsonArray extraArray = obj.getAsJsonArray("extra");
			for (JsonElement extraElement : extraArray) {
				text.append(reconstructText(extraElement));
			}
		}

		return text;
	}

	private TextColor parseColor(JsonObject obj) {
		if (!obj.has("color")) return null;

		JsonElement colorElement = obj.get("color");
		if (colorElement.isJsonPrimitive()) {
			String colorStr = colorElement.getAsString();
			try {
				return TextColor.parse(colorStr).result().orElse(null);
			} catch (Exception ignored) {}
		}
		return null;
	}

	@Override
	public JsonElement serialize(ItemStack stack, Type typeOfSrc, JsonSerializationContext context) {
		if (stack == null || stack.isEmpty()) return JsonNull.INSTANCE;

		JsonObject obj = new JsonObject();
		Identifier id = Registries.ITEM.getId(stack.getItem());
		obj.addProperty("item", id.toString());

		// Serialize custom name
		if (stack.getCustomName() != null) {
			JsonElement simplifiedName = simplifyText(stack.getCustomName());
			if (!simplifiedName.isJsonNull())
				obj.add("name", simplifiedName);
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
			Text text = reconstructText(obj.get("name"));
			if (text != null && !text.getString().isEmpty()) {
				stack.set(DataComponentTypes.CUSTOM_NAME, text);
			}
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