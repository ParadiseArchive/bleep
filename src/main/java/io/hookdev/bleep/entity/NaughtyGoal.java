package io.hookdev.bleep.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A bored bleep gets up to something (design doc: naughty, never annoying). Only when it has had nothing to do for a
 * while, more often the lower the bond, never in a fight or in danger, and it never breaks anything: it steals a cookie
 * from a chest (and eats it), hides next to a flower pot, or moves a torch somewhere it likes better.
 */
public class NaughtyGoal extends Goal {
    public enum Trick { COOKIE, POT, TORCH }

    /** How far it looks for mischief, and how long a trick may take before it gives up (ticks). */
    /** How long it drives about with a torch before it puts it down where it is (ticks). */
    private static final int CARRY = 120;
    private static final int REACH = 8, GIVE_UP = 300, HIDE_TICKS = 200, EAT_TICKS = 100;

    private final BleepEntity bleep;
    private @Nullable Trick trick;
    private @Nullable BlockPos target, torchFrom;
    private int ticks, doneTicks;
    private boolean done;
    /** Where it last put a torch it moved. */
    private @Nullable BlockPos movedTo;

    public NaughtyGoal(BleepEntity bleep) {
        this.bleep = bleep;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    /** Starts a trick straight away, if there is something to do it with (for the tests, and the boredom check). */
    public boolean start(Trick wanted) {
        BlockPos found = find(wanted);
        if (found == null) return false;
        trick = wanted;
        target = found;
        torchFrom = null;
        ticks = doneTicks = 0;
        done = false;
        return true;
    }

    /** Picks a trick it can do here, at random. */
    public boolean startAny() {
        List<Trick> tricks = new ArrayList<>(List.of(Trick.values()));
        java.util.Collections.shuffle(tricks, new java.util.Random(bleep.getRandom().nextLong()));
        for (Trick t : tricks) if (start(t)) return true;
        return false;
    }

    public @Nullable BlockPos movedTo() {
        return movedTo;
    }

    public boolean busy() {
        return trick != null;
    }

    @Override
    public boolean canUse() {
        return trick != null && bleep.mayBeNaughty();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        if (trick == Trick.TORCH && torchFrom != null) putTorchBack();
        trick = null;
        bleep.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        if (done) {
            // the cookie gets eaten, the hiding place is enjoyed for a while
            if (++doneTicks >= (trick == Trick.COOKIE ? EAT_TICKS : HIDE_TICKS)) {
                if (trick == Trick.COOKIE) {
                    bleep.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    bleep.feel(Emotion.EXCITED, 40);
                }
                trick = null;
            }
            return;
        }
        if (ticks > GIVE_UP) { // could not get there: never mind (a torch it carries goes down where it is)
            if (torchFrom != null && placeTorch(bleep.blockPosition())) return;
            stop();
            return;
        }
        if (bleep.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(target)) > 2.2 * 2.2) {
            if (ticks % 5 == 1) bleep.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
            return;
        }
        bleep.getNavigation().stop();
        switch (trick) {
            case COOKIE -> stealCookie();
            case POT -> {
                bleep.pinThought("thought.bleep.naughty.pot");
                bleep.feel(Emotion.STUBBORN, HIDE_TICKS);
                done = true;
            }
            case TORCH -> moveTorch();
        }
    }

    private void stealCookie() {
        if (bleep.level().getBlockEntity(target) instanceof Container chest) {
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                if (chest.getItem(slot).is(Items.COOKIE)) {
                    bleep.setItemSlot(EquipmentSlot.MAINHAND, chest.removeItem(slot, 1));
                    chest.setChanged();
                    bleep.pinThought("thought.bleep.naughty.cookie");
                    bleep.feel(Emotion.EXCITED, EAT_TICKS);
                    done = true;
                    return;
                }
            }
        }
        trick = null; // someone ate it first
    }

    /** First it takes the torch, then it drives off and puts it down somewhere else. */
    private void moveTorch() {
        var level = bleep.level();
        if (torchFrom == null) {
            if (!level.getBlockState(target).is(Blocks.TORCH)) {
                trick = null;
                return;
            }
            level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
            torchFrom = target;
            BlockPos spot = newTorchSpot(torchFrom);
            if (spot == null) {
                putTorchBack();
                trick = null;
                return;
            }
            target = spot;
            ticks = GIVE_UP - CARRY;
            return;
        }
        if (!placeTorch(target) && !placeTorch(bleep.blockPosition())) {
            putTorchBack();
            trick = null;
        }
    }

    /** Puts the torch it carries down here, if a torch can stand here. */
    private boolean placeTorch(BlockPos pos) {
        var level = bleep.level();
        if (!level.getBlockState(pos).isAir() || !Blocks.TORCH.defaultBlockState().canSurvive(level, pos)) return false;
        level.setBlock(pos, Blocks.TORCH.defaultBlockState(), 3);
        movedTo = pos.immutable();
        torchFrom = null;
        bleep.pinThought("thought.bleep.naughty.torch");
        bleep.feel(Emotion.STUBBORN, 60);
        done = true;
        doneTicks = HIDE_TICKS - 40;
        return true;
    }

    private void putTorchBack() {
        var level = bleep.level();
        if (torchFrom != null && level.getBlockState(torchFrom).isAir()) level.setBlock(torchFrom, Blocks.TORCH.defaultBlockState(), 3);
        torchFrom = null;
    }

    /** Somewhere three to five blocks away where a torch can stand. */
    private @Nullable BlockPos newTorchSpot(BlockPos from) {
        var level = bleep.level();
        List<BlockPos> spots = new ArrayList<>();
        for (BlockPos spot : BlockPos.betweenClosed(from.offset(-5, -1, -5), from.offset(5, 1, 5))) {
            double distance = spot.distSqr(from);
            if (distance >= 9 && distance <= 25 && level.getBlockState(spot).isAir() && Blocks.TORCH.defaultBlockState().canSurvive(level, spot)) spots.add(spot.immutable());
        }
        // the one nearest to where it stands now: most likely one it can drive to
        return spots.stream().min(java.util.Comparator.comparingDouble(spot -> spot.distSqr(bleep.blockPosition()))).orElse(null);
    }

    /** A place for this trick within reach: a chest with a cookie in it, a flower pot, a standing torch. */
    private @Nullable BlockPos find(Trick wanted) {
        var level = bleep.level();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(bleep.blockPosition().offset(-REACH, -2, -REACH), bleep.blockPosition().offset(REACH, 3, REACH))) {
            if (!matches(wanted, pos)) continue;
            double distance = pos.distSqr(bleep.blockPosition());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }

    private boolean matches(Trick wanted, BlockPos pos) {
        var level = bleep.level();
        BlockState state = level.getBlockState(pos);
        return switch (wanted) {
            case COOKIE -> level.getBlockEntity(pos) instanceof ChestBlockEntity chest && chest.hasAnyMatching(stack -> stack.is(Items.COOKIE));
            case POT -> state.getBlock() instanceof FlowerPotBlock;
            case TORCH -> state.is(Blocks.TORCH);
        };
    }
}
