package com.example.autocrystal;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class AutoCrystalClient implements ClientModInitializer {
	public static final String MOD_ID = "autocrystal";
	/** Тиков между действиями (20 тиков = 1 сек). Больше число = медленнее. */
	private static final int DELAY_TICKS = 4;
	private static final double REACH = 4.4;
	private static final double MIN_DIST = 2.0, MAX_DIST = 3.2;

	private static final KeyBinding.Category CATEGORY =
			KeyBinding.Category.create(Identifier.of(MOD_ID, "controls"));
	private static KeyBinding toggleKey;
	private static boolean on;
	private static int wait;

	@Override
	public void onInitializeClient() {
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.autocrystal.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(c -> {
			while (toggleKey.wasPressed()) {
				if (c.currentScreen == null && c.player != null) {
					on = !on;
					wait = 0;
					c.player.sendMessage(Text.literal("[AutoCrystal] " + (on ? "ВКЛ" : "ВЫКЛ")), true);
				}
			}
			if (on) tick(c);
		});
	}

	private static void tick(MinecraftClient c) {
		ClientPlayerEntity p = c.player;
		if (p == null || c.world == null || c.interactionManager == null) { on = false; return; }
		if (c.currentScreen != null) return;
		if (wait > 0) { wait--; return; }

		Vec3d eye = p.getEyePos();
		List<EndCrystalEntity> crystals = c.world.getEntitiesByClass(EndCrystalEntity.class,
				p.getBoundingBox().expand(5), e -> e.squaredDistanceTo(eye) <= REACH * REACH);
		if (!crystals.isEmpty()) {
			c.interactionManager.attackEntity(p, crystals.get(0));
			p.swingHand(Hand.MAIN_HAND);
			wait = DELAY_TICKS;
			return;
		}
		int slot = -1;
		for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(Items.END_CRYSTAL)) { slot = i; break; }
		if (slot < 0) { on = false; p.sendMessage(Text.literal("[AutoCrystal] Нет кристаллов в хотбаре — стоп"), false); return; }
		BlockPos target = null;
		double best = Double.MAX_VALUE;
		BlockPos base = p.getBlockPos();
		for (int dx = -4; dx <= 4; dx++) for (int dy = -2; dy <= 1; dy++) for (int dz = -4; dz <= 4; dz++) {
			BlockPos b = base.add(dx, dy, dz);
			var st = c.world.getBlockState(b);
			if (!st.isOf(Blocks.OBSIDIAN) && !st.isOf(Blocks.BEDROCK)) continue;
			if (!c.world.getBlockState(b.up()).isAir() || !c.world.getBlockState(b.up(2)).isAir()) continue;
			double hd = Math.hypot(b.getX() + 0.5 - p.getX(), b.getZ() + 0.5 - p.getZ());
			if (hd < MIN_DIST || hd > MAX_DIST) continue;
			if (eye.squaredDistanceTo(Vec3d.ofCenter(b)) > REACH * REACH) continue;
			if (!c.world.getOtherEntities(null, new Box(b.up())).isEmpty()) continue;
			double score = Math.abs(hd - 2.5);
			if (score < best) { best = score; target = b; }
		}
		if (target == null) { wait = DELAY_TICKS; return; }
		p.getInventory().setSelectedSlot(slot);
		Vec3d hit = Vec3d.ofCenter(target).add(0, 0.5, 0);
		c.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, target, false));
		p.swingHand(Hand.MAIN_HAND);
		wait = DELAY_TICKS;
	}
}
