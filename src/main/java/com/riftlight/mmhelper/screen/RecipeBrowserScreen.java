package com.riftlight.mmhelper.screen;

import com.riftlight.mmhelper.Ingredient;
import com.riftlight.mmhelper.Recipe;
import com.riftlight.mmhelper.RecipeStorage;
import com.riftlight.mmhelper.TaskHudOverlay;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RecipeBrowserScreen extends Screen {
	// this could be and probably should be made dynamic
	private static final int RECIPE_SIZE = 60; // size of each recipe ""card"" thing
	private static final int GRID_COLS = 8;
	private static final int GRID_PADDING = 10;

	private List<Map.Entry<String, Recipe>> filteredRecipes = new ArrayList<>();
	private int scrollOffset = 0;
	private int maxScrollOffset = 0;

	public RecipeBrowserScreen() {
		super(Text.literal("Recipe Browser"));

	}

	@Override
	protected void init() {
		super.init();

		// Search box
		int searchWidth = 200;
		TextFieldWidget searchBox = new TextFieldWidget(
				textRenderer,
				(width - searchWidth) / 2,
				GRID_PADDING,
				searchWidth,
				20,
				Text.literal("Search recipes...")
		);
		searchBox.setChangedListener(this::onSearchChanged);
		this.addDrawableChild(searchBox);

		// Close button
		this.addDrawableChild(
				ButtonWidget.builder(
						Text.literal("Close"),
						button -> this.close()
				)
				.dimensions(width - 60, 5, 50, 20)
				.build()
		);

		refreshFilter("");
		updateScrollBounds();
	}

	private void onSearchChanged(String text) {
		refreshFilter(text);
		this.scrollOffset = 0;
		updateScrollBounds();
	}

	private void refreshFilter(String query) {
		String lowerQuery = query.toLowerCase();
		this.filteredRecipes = RecipeStorage.getAllRecipes().entrySet().stream()
				.filter(
						entry -> query.isEmpty() ||
								entry.getKey().toLowerCase().contains(lowerQuery) ||
								entry.getValue().matchesSearch(query)
				)
				.collect(Collectors.toList());
		updateScrollBounds();
	}

	private void updateScrollBounds() {
		int rows = (filteredRecipes.size() + GRID_COLS - 1) / GRID_COLS;
		int contentHeight = rows * (RECIPE_SIZE + GRID_PADDING);
		int visibleHeight = height - 80; // consider search bar and margins
		maxScrollOffset = Math.max(0, contentHeight - visibleHeight);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		// title
		context.drawCenteredTextWithShadow(textRenderer, "Saved Recipes", width / 2, 40, 0xFFFFFF);

		// scroll indicator if necessary
		if (maxScrollOffset > 0)
			renderScrollBar(context);

		// draw recipe grid
		int startX = GRID_PADDING;
		int startY = 70; // below search

		// scissor to clip recipes
		context.enableScissor(0, startY - 5, width, height - 5);

		for (int i = 0; i < filteredRecipes.size(); i++) {
			int row = i / GRID_COLS;
			int col = i % GRID_COLS;

			int x = startX + col * (RECIPE_SIZE + GRID_PADDING);
			int y = startY + row * (RECIPE_SIZE + GRID_PADDING) - scrollOffset;

			// render only if visible
			if (y > startY - RECIPE_SIZE - GRID_PADDING && y < height)
				renderRecipeCard(context, filteredRecipes.get(i), x, y, mouseX, mouseY);
		}

		context.disableScissor();

		// Tooltips for hovered recipes
		for (int i = 0; i < filteredRecipes.size(); i++) {
			int row = i / GRID_COLS;
			int col = i % GRID_COLS;

			int x = startX + col * (RECIPE_SIZE + GRID_PADDING);
			int y = startY + row * (RECIPE_SIZE + GRID_PADDING) - scrollOffset;

			if (mouseX >= x && mouseX <= (x + RECIPE_SIZE) &&
				mouseY >= y && mouseY <= (y + RECIPE_SIZE) &&
			y > startY - RECIPE_SIZE - GRID_PADDING && y < height) {
				renderTooltip(context, filteredRecipes.get(i), mouseX, mouseY);
				break;
			}
		}
	}

	private void renderRecipeCard(DrawContext context, Map.Entry<String, Recipe> entry, int x, int y, int mouseX, int mouseY) {
		String itemName = entry.getKey();
		Recipe recipe = entry.getValue();

		// draw card background
		boolean hovered = mouseX >= x && mouseX <= (x + RECIPE_SIZE) && mouseY >= y && mouseY <= (y + RECIPE_SIZE);
		int backgroundColor = hovered ? 0x80FFFFFF : 0x80000000;
		context.fill(x, y, x + RECIPE_SIZE, y + RECIPE_SIZE, backgroundColor);

		// border manually because apparently drawBorder was removed thanks obama
		int borderColor = 0xFFAAAAAA;
		context.fill(x, y, x + RECIPE_SIZE, y + 1, borderColor); // top
		context.fill(x, y + RECIPE_SIZE - 1, x + RECIPE_SIZE, y + RECIPE_SIZE, borderColor); // bottom
		context.fill(x, y, x + 1, y + RECIPE_SIZE, borderColor); // left
		context.fill(x + RECIPE_SIZE - 1, y, x + RECIPE_SIZE, y + RECIPE_SIZE, borderColor); // right

		// Item name at top of card
		renderCardTitle(context, itemName, x, y, RECIPE_SIZE, 0.6f);

		// draw full recipe grid in the card
		ItemStack[][] grid = recipe.getGrid();
		int cellSize = (int) (RECIPE_SIZE / 3.75);
		int gridStartX = x + (RECIPE_SIZE - (3 * cellSize)) / 2;
		int gridStartY = RECIPE_SIZE/12 + y + (RECIPE_SIZE - (3 * cellSize)) / 2;

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				ItemStack stack = grid[row][col];
				if (stack != null && !stack.isEmpty()) {
					int slotX = gridStartX + col * cellSize;
					int slotY = gridStartY + row * cellSize;

					context.drawItem(stack, slotX, slotY);
					context.drawStackOverlay(textRenderer, stack, slotX, slotY);
				}
			}
		}
	}

	private void renderCardTitle(DrawContext context, String text, int cardX, int cardY, int cardWidth, float scale) {
		context.getMatrices().pushMatrix();

		int maxLineWidth = (int) (cardWidth * 0.9f / scale);

		String[] words = text.split(" ");
		List<String> lines = new ArrayList<>();
		StringBuilder currentLine = new StringBuilder();

		// build lines that fit within max width
		for (String word : words) {
			String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
			int testWidth = textRenderer.getWidth(testLine);

			if (testWidth <= maxLineWidth) {
				currentLine = new StringBuilder(testLine);
			} else {
				if (!currentLine.isEmpty()) {
					lines.add(currentLine.toString());
					currentLine = new StringBuilder(word);
				} else {
					// word itself is too long, truncate
					lines.add(truncateText(word, maxLineWidth, textRenderer));
				}
			}
		}
		if (!currentLine.isEmpty()) {
			lines.add(currentLine.toString());
		}

		if (lines.size() > 2) {
			String lastLine = lines.get(1) + "...";
			// if adding ... makes it too long, truncate further
			while (textRenderer.getWidth(lastLine) > maxLineWidth && lastLine.length() > 3) {
				lastLine = lastLine.substring(0, lastLine.length() - 4) + "...";
			}
			lines = lines.subList(0, 2);
			lines.set(1, lastLine);
		}

		int lineHeight = textRenderer.fontHeight;

		// Draw each line centered
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			int lineWidth = textRenderer.getWidth(line);

			context.getMatrices().pushMatrix();
			// Move to center of card, then to vertical position for this line
			context.getMatrices().translate(
					cardX + cardWidth / 2f,
					cardY + i * lineHeight * scale + (lineHeight * scale) / 2f
			);
			context.getMatrices().scale(scale, scale);

			// Draw centered at (0,0)
			context.drawText(textRenderer, Text.literal(line), -lineWidth / 2, 0, 0xFFFFFFFF, true);

			context.getMatrices().popMatrix();
		}

		context.getMatrices().popMatrix();
	}

	private String truncateText(String text, int maxWidth, TextRenderer textRenderer) {
		if (textRenderer.getWidth(text) <= maxWidth) {
			return text;
		}

		String truncated = text;
		while (textRenderer.getWidth(truncated + "...") > maxWidth && truncated.length() > 1) {
			truncated = truncated.substring(0, truncated.length() - 1);
		}
		return truncated + "...";
	}

	private void renderTooltip(DrawContext context, Map.Entry<String, Recipe> entry, int mouseX, int mouseY) {
		List<Text> tooltip = new ArrayList<>();
		tooltip.add(Text.literal(entry.getKey()).formatted(Formatting.GOLD, Formatting.BOLD));
		tooltip.add(Text.literal("Ingredients:").formatted(Formatting.GRAY));

		for (Ingredient ing : entry.getValue().getIngredients()) {
			tooltip.add(Text.literal("  • " +
					ing.getName() +
					(ing.getAmount() > 1 ? " x" + ing.getAmount() : ""))
				.formatted(Formatting.WHITE)
			);
		}

		context.drawTooltip(textRenderer, tooltip, mouseX, mouseY);
	}

	private void renderScrollBar(DrawContext context) {
		int barX = width-8;
		int barY = 70;
		int barHeight = height - 80;

		// draw whole scroll track
		context.fill(barX, barY, barX + 4, barY + barHeight, 0x40FFFFFF);

		// draw scroll thumb
		float progress = scrollOffset / (float) maxScrollOffset;
		int thumbY = barY + (int) (progress * (barHeight - 30));
		context.fill(barX, thumbY, barX + 4, thumbY + 30,0xCCFFFFFF);
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		// Pin clicked recipe
		int startX = GRID_PADDING;
		int startY = 70;

		for (int i = 0; i < filteredRecipes.size(); i++) {
			int row = i / GRID_COLS;
			int col = i % GRID_COLS;
			int x = startX + col * (RECIPE_SIZE + GRID_PADDING);
			int y = startY + row * (RECIPE_SIZE + GRID_PADDING) - scrollOffset;

			if (y > startY - RECIPE_SIZE - GRID_PADDING && y < height) {
				if (click.x() >= x && click.x() <= x + RECIPE_SIZE && click.y() >= y && click.y() <= y + RECIPE_SIZE) {
					String clickedKey = filteredRecipes.get(i).getKey();
					ItemStack t = RecipeStorage.getRecipe(clickedKey).getResult();
					TaskHudOverlay.setItem(t);
					this.close();
					return true;
				}
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (!filteredRecipes.isEmpty()) {
			scrollOffset = Math.max(0, Math.min(maxScrollOffset, scrollOffset - (int) (verticalAmount * 20)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
			this.close();
			return true;
		}
		return super.keyPressed(input);
	}
}
