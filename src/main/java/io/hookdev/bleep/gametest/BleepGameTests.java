package io.hookdev.bleep.gametest;

import io.hookdev.bleep.Bleep;
import io.hookdev.bleep.BleepContent;
import io.hookdev.bleep.block.HomebaseBlock;
import io.hookdev.bleep.entity.Activity;
import io.hookdev.bleep.entity.BleepEntity;
import io.hookdev.bleep.entity.Emotion;
import io.hookdev.bleep.network.BleepCommand;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * In-world tests, run with {@code ./gradlew runGameTestServer} (or {@code /test runall} in a dev client).
 * They all use the empty "bench" structure (7 wide, 3 deep, standing on the ground at y 0) and set up their own bleep.
 */
public final class BleepGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, Bleep.MODID);
    /** How long each test may take, in ticks. */
    private static final Map<String, Integer> MAX_TICKS = new LinkedHashMap<>();

    static {
        test("a_bleep_never_dies", BleepGameTests::aBleepNeverDies, 20);
        test("damage_drains_the_battery", BleepGameTests::damageDrainsTheBattery, 20);
        test("an_empty_bleep_sleeps_and_charges", BleepGameTests::anEmptyBleepSleepsAndCharges, 40);
        test("a_low_battery_makes_it_tired", BleepGameTests::aLowBatteryMakesItTired, 20);
        test("petting_makes_it_happy", BleepGameTests::pettingMakesItHappy, 20);
        test("only_the_owner_gives_orders", BleepGameTests::onlyTheOwnerGivesOrders, 20);
        test("placing_a_homebase_moves_your_bleep_in", BleepGameTests::placingAHomebaseMovesYourBleepIn, 20);
        test("go_home_drives_it_home", BleepGameTests::goHomeDrivesItHome, 200);
        test("an_empty_bleep_goes_home_to_charge", BleepGameTests::anEmptyBleepGoesHomeToCharge, 100);
        test("the_homebase_lights_up_while_it_charges", BleepGameTests::theHomebaseLightsUpWhileItCharges, 60);
        test("a_broken_homebase_leaves_it_homeless", BleepGameTests::aBrokenHomebaseLeavesItHomeless, 60);
        test("a_name_always_shows", BleepGameTests::aNameAlwaysShows, 20);
        test("the_core_boots_a_bleep", BleepGameTests::theCoreBootsABleep, 100);
    }

    private static final BlockPos SPOT = new BlockPos(3, 0, 1), HOME = new BlockPos(0, 0, 1), FAR = new BlockPos(6, 0, 1);

    private BleepGameTests() {}

    private static void test(String name, Consumer<GameTestHelper> function, int maxTicks) {
        FUNCTIONS.register(name, () -> function);
        MAX_TICKS.put(name, maxTicks);
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(BleepGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Bleep.id("default"));
        for (var function : FUNCTIONS.getEntries()) {
            event.registerTest(function.getId(), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, function.getId()),
                    new TestData<>(environment, Bleep.id("bench"), MAX_TICKS.get(function.getId().getPath()), 0, true)));
        }
    }

    /** A bleep that belongs to a test player. */
    private static BleepEntity bleep(GameTestHelper helper, Player owner) {
        BleepEntity bleep = helper.spawnWithNoFreeWill(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(owner);
        return bleep;
    }

    private static void aBleepNeverDies(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        bleep.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1000);
        helper.assertTrue(bleep.isAlive(), "a bleep should never die");
        helper.assertTrue(bleep.getHealth() == bleep.getMaxHealth(), "a bleep has no health to lose");
        helper.assertTrue(bleep.asleep(), "a huge hit should empty the battery, and the bleep switches off");
        helper.succeed();
    }

    private static void damageDrainsTheBattery(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        bleep.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 3);
        float expected = BleepEntity.FULL - 3 * BleepEntity.DAMAGE_COST;
        helper.assertTrue(Math.abs(bleep.battery() - expected) < 0.01f, "a hit of 3 should cost 6%, battery is " + bleep.battery());
        helper.succeedWhen(() -> helper.assertTrue(bleep.emotion() == Emotion.SURPRISED, "a hit should surprise it"));
    }

    private static void anEmptyBleepSleepsAndCharges(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        bleep.setBattery(0);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(bleep.emotion() == Emotion.ASLEEP, "an empty bleep should be asleep, it shows " + bleep.emotion());
            helper.assertTrue(bleep.battery() > 0.5f, "it should charge itself while it sleeps, battery is " + bleep.battery());
            helper.succeed();
        });
    }

    private static void aLowBatteryMakesItTired(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        bleep.setBattery(10);
        helper.succeedWhen(() -> helper.assertTrue(bleep.emotion() == Emotion.TIRED, "below 20% it should look tired, it shows " + bleep.emotion()));
    }

    private static void pettingMakesItHappy(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        owner.setShiftKeyDown(true);
        bleep.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertFalse(bleep.isOrderedToSit(), "petting should not tell it to stay");
        helper.succeedWhen(() -> helper.assertTrue(bleep.emotion() == Emotion.EXCITED, "petting should make it excited, it shows " + bleep.emotion()));
    }

    /** A test player standing in the middle of the bench. */
    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(3.5, 0, 1.5));
        player.snapTo(at.x, at.y, at.z);
        return player;
    }

    /** A homebase in the bench, facing east, without anyone living in it yet. */
    private static BlockPos homebase(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, BleepContent.HOMEBASE.get().defaultBlockState().setValue(HomebaseBlock.FACING, Direction.EAST));
        return helper.absolutePos(pos);
    }

    private static void onlyTheOwnerGivesOrders(GameTestHelper helper) {
        Player owner = player(helper), stranger = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        helper.assertFalse(BleepCommand.obey(stranger, bleep, BleepCommand.STAY), "a bleep only listens to its owner");
        helper.assertFalse(bleep.isOrderedToSit(), "a stranger should not make it stay");
        helper.assertTrue(BleepCommand.obey(owner, bleep, BleepCommand.STAY) && bleep.isOrderedToSit(), "Stay should make it stay");
        helper.assertTrue(BleepCommand.obey(owner, bleep, BleepCommand.FOLLOW) && !bleep.isOrderedToSit(), "Follow should make it follow");
        helper.assertFalse(BleepCommand.obey(owner, bleep, BleepCommand.GO_HOME), "a bleep without a homebase cannot go home");
        helper.succeed();
    }

    private static void placingAHomebaseMovesYourBleepIn(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        BlockPos home = homebase(helper, HOME);
        BleepContent.HOMEBASE.get().setPlacedBy(helper.getLevel(), home, helper.getLevel().getBlockState(home), owner, ItemStack.EMPTY);
        helper.assertTrue(GlobalPos.of(helper.getLevel().dimension(), home).equals(bleep.home()), "the bleep should move into the new homebase");
        helper.assertTrue(HomebaseBlock.resident(helper.getLevel(), home) == bleep, "the homebase should know who lives there");
        helper.succeed();
    }

    private static void goHomeDrivesItHome(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), FAR);
        bleep.tame(owner);
        BlockPos home = homebase(helper, HOME);
        bleep.setHome(GlobalPos.of(helper.getLevel().dimension(), home));
        helper.assertTrue(BleepCommand.obey(owner, bleep, BleepCommand.GO_HOME), "with a homebase, Go Home should work");
        helper.succeedWhen(() -> {
            helper.assertTrue(bleep.onHome(), "it should drive onto its homebase");
            helper.assertTrue(bleep.isOrderedToSit(), "and stay there");
        });
    }

    private static void anEmptyBleepGoesHomeToCharge(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        BlockPos home = homebase(helper, HOME);
        bleep.setHome(GlobalPos.of(helper.getLevel().dimension(), home));
        bleep.setBattery(0);
        helper.runAfterDelay(BleepEntity.GOES_HOME_AFTER + 10, () -> {
            helper.assertTrue(bleep.onHome(), "an empty bleep should go home");
            helper.assertTrue(bleep.asleep(), "and charge there asleep, until it is full");
            helper.assertTrue(bleep.activity() == Activity.CHARGING, "its screen should say it is charging, it says " + bleep.activity());
            helper.assertTrue(bleep.battery() > 0.5f, "it should charge at home, battery is " + bleep.battery());
            helper.succeed();
        });
    }

    private static void theHomebaseLightsUpWhileItCharges(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        BlockPos home = homebase(helper, HOME);
        bleep.setHome(GlobalPos.of(helper.getLevel().dimension(), home));
        bleep.setBattery(50);
        bleep.dock();
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(bleep.battery() > 50.5f, "a docked bleep should charge, battery is " + bleep.battery());
            helper.assertTrue(helper.getLevel().getBlockState(home).getValue(HomebaseBlock.CHARGING), "the light should be on while it charges");
            bleep.setBattery(BleepEntity.FULL);
            helper.runAfterDelay(5, () -> {
                helper.assertFalse(helper.getLevel().getBlockState(home).getValue(HomebaseBlock.CHARGING), "the light should go out when it is full");
                helper.assertTrue(bleep.activity() == Activity.AT_HOME, "full at home it should be resting, it says " + bleep.activity());
                helper.succeed();
            });
        });
    }

    private static void aBrokenHomebaseLeavesItHomeless(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        BlockPos home = homebase(helper, HOME);
        bleep.setHome(GlobalPos.of(helper.getLevel().dimension(), home));
        helper.setBlock(HOME, Blocks.AIR);
        helper.succeedWhen(() -> {
            helper.assertTrue(bleep.home() == null, "without its homebase it should be homeless");
            helper.assertTrue(bleep.emotion() == Emotion.SAD, "and a little sad about it, it shows " + bleep.emotion());
        });
    }

    private static void aNameAlwaysShows(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        bleep.setCustomName(Component.literal("Bolt"));
        helper.assertTrue(bleep.isCustomNameVisible(), "a bleep's name should always show, like a player's");
        helper.succeed();
    }

    private static void theCoreBootsABleep(GameTestHelper helper) {
        Player player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BleepContent.BLEEP_CORE.get()));
        BlockPos floor = helper.absolutePos(SPOT.below());
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        BleepContent.BLEEP_CORE.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the core should go into the bleep");
        BleepEntity bleep = helper.findOneEntity(BleepContent.BLEEP.get());
        helper.assertTrue(bleep.isOwnedBy(player), "the bleep should belong to whoever built it");
        helper.assertTrue(bleep.emotion() == Emotion.BOOT || bleep.tickCount == 0, "its screen should start up");
        helper.succeedWhen(() -> helper.assertTrue(bleep.emotion() == Emotion.EXCITED, "after starting up it should be excited, it shows " + bleep.emotion()));
    }
}
