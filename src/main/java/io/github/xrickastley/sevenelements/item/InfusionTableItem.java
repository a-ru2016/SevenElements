package io.github.xrickastley.sevenelements.item;

import io.github.xrickastley.sevenelements.factory.SevenElementsGameRules;
import io.github.xrickastley.sevenelements.screen.ElementalInfusionScreenHandler;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.TallBlockItem;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Colors;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class InfusionTableItem extends TallBlockItem {
	public InfusionTableItem(Block block, Settings settings) {
		super(block, settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		PlayerEntity player = context.getPlayer();
		if (player != null && !player.isSneaking()) {
			return this.use(context.getWorld(), player, context.getHand()).getResult();
		}
		return super.useOnBlock(context);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack itemStack = user.getStackInHand(hand);
		if (!user.isSneaking()) {
			if (!world.isClient) {
				if (world.getGameRules().getBoolean(SevenElementsGameRules.INFUSION_TABLE)) {
					user.openHandledScreen(new SimpleNamedScreenHandlerFactory(
						(syncId, inventory, player) -> new ElementalInfusionScreenHandler(syncId, inventory, ScreenHandlerContext.EMPTY),
						Text.translatable("container.seven-elements.infusion_table")
					));
				} else {
					user.sendMessage(
						Text.translatable("container.seven-elements.infusion_table.fail_by_gamerule").withColor(Colors.LIGHT_RED)
					);
				}
			}
			return TypedActionResult.consume(itemStack);
		}
		return super.use(world, user, hand);
	}
}
