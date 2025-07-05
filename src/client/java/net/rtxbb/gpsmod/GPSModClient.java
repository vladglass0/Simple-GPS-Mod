package net.rtxbb.gpsmod;

import net.fabricmc.api.ClientModInitializer;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.GameRenderer;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.util.math.RotationAxis;

public class GPSModClient implements ClientModInitializer {
	// Целевые координаты GPS (null если не установлены)
	public static Double targetX = null;
	public static Double targetY = null;
	public static Double targetZ = null;

	private static final Identifier ARROW_TEXTURE = new Identifier("gps-mod", "arrow.png");

	@Override
	public void onInitializeClient() {
		// Регистрируем клиентскую команду /mpgps x y z
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
				ClientCommandManager.literal("mpgps")
					.then(ClientCommandManager.argument("x", DoubleArgumentType.doubleArg())
						.then(ClientCommandManager.argument("y", DoubleArgumentType.doubleArg())
							.then(ClientCommandManager.argument("z", DoubleArgumentType.doubleArg())
								.executes(ctx -> setTarget(ctx, true))
							)
						)
					)
					.executes(ctx -> clearTarget(ctx))
			);
		});

		// Регистрируем HUD рендер
		HudRenderCallback.EVENT.register((DrawContext drawContext, float tickDelta) -> {
			renderArrow(drawContext, tickDelta);
		});
	}

	private int setTarget(CommandContext<FabricClientCommandSource> ctx, boolean showMsg) {
		targetX = DoubleArgumentType.getDouble(ctx, "x");
		targetY = DoubleArgumentType.getDouble(ctx, "y");
		targetZ = DoubleArgumentType.getDouble(ctx, "z");
		if (showMsg) {
			ctx.getSource().sendFeedback(Text.literal("GPS координаты установлены: " + targetX + ", " + targetY + ", " + targetZ));
		}
		return Command.SINGLE_SUCCESS;
	}

	private int clearTarget(CommandContext<FabricClientCommandSource> ctx) {
		targetX = null;
		targetY = null;
		targetZ = null;
		ctx.getSource().sendFeedback(Text.literal("GPS цель сброшена."));
		return Command.SINGLE_SUCCESS;
	}

	private void renderArrow(DrawContext drawContext, float tickDelta) {
		if (targetX == null || targetY == null || targetZ == null) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options == null) return;

		double px = client.player.getX();
		double pz = client.player.getZ();
		double tx = targetX;
		double tz = targetZ;

		double dx = targetX - px;
		double dz = targetZ - pz;
		double angleToTarget = Math.atan2(dx, dz);
		double playerYaw = Math.toRadians(client.player.getYaw(tickDelta));
		double screenAngle = angleToTarget - playerYaw;

		int screenWidth = client.getWindow().getScaledWidth();
		int screenHeight = client.getWindow().getScaledHeight();
		int arrowSize = 16;
		int cx = screenWidth / 2;
		int cy = screenHeight / 2;
		int radius = Math.min(screenWidth, screenHeight) / 7;
		int ax = (int) (cx + Math.cos(screenAngle) * radius) + arrowSize / 2;
		int ay = (int) (cy + Math.sin(screenAngle) * radius) + arrowSize / 2;

		MatrixStack matrices = drawContext.getMatrices();
		matrices.push();
		matrices.translate(ax + arrowSize / 2.0, ay + arrowSize / 2.0, 0);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotation((float)(screenAngle + Math.PI / 2)));
		matrices.translate(-arrowSize / 2.0, -arrowSize / 2.0, 0);
		drawContext.drawTexture(ARROW_TEXTURE, 0, 0, 0, 0, arrowSize, arrowSize, arrowSize, arrowSize);
		matrices.pop();

		// Выводим расстояние до метки под стрелкой
		double py = client.player.getY();
		double ty = targetY;
		double dist = Math.sqrt((tx - px) * (tx - px) + (ty - py) * (ty - py) + (tz - pz) * (tz - pz));
		String distText = String.format("%.1f м", dist);
		int textWidth = client.textRenderer.getWidth(distText);
		int textX = ax + arrowSize / 2 - textWidth / 2;
		int textY = ay + arrowSize + 4; // немного под стрелкой
		drawContext.drawText(client.textRenderer, distText, textX, textY, 0xFFFFFF, true);
	}
}