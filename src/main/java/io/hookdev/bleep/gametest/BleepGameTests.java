package io.hookdev.bleep.gametest;

import io.hookdev.bleep.Bleep;
import io.hookdev.bleep.BleepContent;
import io.hookdev.bleep.block.HomebaseBlock;
import io.hookdev.bleep.entity.Accessory;
import io.hookdev.bleep.entity.Activity;
import io.hookdev.bleep.entity.BleepEntity;
import io.hookdev.bleep.entity.Bond;
import io.hookdev.bleep.entity.Display;
import io.hookdev.bleep.entity.Emotion;
import io.hookdev.bleep.entity.NaughtyGoal;
import io.hookdev.bleep.entity.Obsession;
import io.hookdev.bleep.item.BleepCoreItem;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        test("petting_raises_the_bond_five_times_a_day", BleepGameTests::pettingRaisesTheBondFiveTimesADay, 20);
        test("treats_and_the_favourite_raise_the_bond", BleepGameTests::treatsAndTheFavouriteRaiseTheBond, 20);
        test("hitting_it_or_switching_it_off_lowers_the_bond", BleepGameTests::hittingItOrSwitchingItOffLowersTheBond, 20);
        test("a_new_level_is_celebrated", BleepGameTests::aNewLevelIsCelebrated, 20);
        test("it_dawdles_then_does_it_anyway", BleepGameTests::itDawdlesThenDoesItAnyway, 100);
        test("in_danger_it_always_listens", BleepGameTests::inDangerItAlwaysListens, 20);
        test("a_new_mob_gets_a_thought", BleepGameTests::aNewMobGetsAThought, 120);
        test("it_brings_its_favourite_to_its_owner", BleepGameTests::itBringsItsFavouriteToItsOwner, 300);
        test("it_fetches_a_thrown_music_disc", BleepGameTests::itFetchesAThrownMusicDisc, 400);
        test("a_name_always_shows", BleepGameTests::aNameAlwaysShows, 20);
        test("a_torch_needs_a_friend", BleepGameTests::aTorchNeedsAFriend, 20);
        test("its_torch_lights_the_way", BleepGameTests::itsTorchLightsTheWay, 40);
        test("it_fights_for_its_owner", BleepGameTests::itFightsForItsOwner, 100);
        test("a_buddy_brings_what_it_finds", BleepGameTests::aBuddyBringsWhatItFinds, 300);
        test("a_bored_bleep_steals_a_cookie", BleepGameTests::aBoredBleepStealsACookie, 300);
        test("a_bored_bleep_moves_a_torch", BleepGameTests::aBoredBleepMovesATorch, 400);
        test("redstone_charges_it", BleepGameTests::redstoneChargesIt, 20);
        test("redstone_wakes_an_empty_bleep", BleepGameTests::redstoneWakesAnEmptyBleep, 40);
        test("a_power_bank_tops_it_up", BleepGameTests::aPowerBankTopsItUp, 20);
        test("lightning_overcharges_it", BleepGameTests::lightningOverchargesIt, 40);
        test("dye_paints_it", BleepGameTests::dyePaintsIt, 20);
        test("it_wears_a_hat_and_a_sticker", BleepGameTests::itWearsAHatAndASticker, 20);
        test("its_name_scrolls_across", BleepGameTests::itsNameScrollsAcross, 20);
        test("an_arrow_points_home", BleepGameTests::anArrowPointsHome, 40);
        test("the_core_needs_its_parts", BleepGameTests::theCoreNeedsItsParts, 20);
        test("the_core_puts_a_bleep_together", BleepGameTests::theCorePutsABleepTogether, 200);
        test("the_core_turns_up_in_chests", BleepGameTests::partsTurnUpInChests, 20);
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
        helper.assertFalse(BleepCommand.obey(stranger, bleep, BleepCommand.STAY, 1), "a bleep only listens to its owner");
        helper.assertFalse(bleep.isOrderedToSit(), "a stranger should not make it stay");
        helper.assertTrue(BleepCommand.obey(owner, bleep, BleepCommand.STAY, 1) && bleep.isOrderedToSit(), "Stay should make it stay");
        helper.assertTrue(BleepCommand.obey(owner, bleep, BleepCommand.FOLLOW, 1) && !bleep.isOrderedToSit(), "Follow should make it follow");
        helper.assertFalse(BleepCommand.obey(owner, bleep, BleepCommand.GO_HOME, 1), "a bleep without a homebase cannot go home");
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
        helper.assertTrue(BleepCommand.obey(owner, bleep, BleepCommand.GO_HOME, 1), "with a homebase, Go Home should work");
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

    /** Its favourite item made the compass, so the tests know what it is. */
    private static final int COMPASS = Obsession.ALL.indexOf(Obsession.ALL.stream().filter(o -> o.id().equals("compass")).findFirst().orElseThrow());

    private static void pettingRaisesTheBondFiveTimesADay(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        for (int i = 0; i < 7; i++) bleep.pet();
        helper.assertTrue(bleep.bond() == Bond.PETS_PER_DAY * Bond.PET, "petting counts five times a day, bond is " + bleep.bond());
        helper.succeed();
    }

    private static void treatsAndTheFavouriteRaiseTheBond(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        bleep.setObsession(COMPASS);
        helper.assertTrue(bleep.give(owner, new ItemStack(Items.COOKIE)), "a cookie is a treat");
        helper.assertTrue(bleep.bond() == Bond.GIFT, "a treat is worth 3, bond is " + bleep.bond());
        helper.assertFalse(bleep.give(owner, new ItemStack(Items.DIRT)), "dirt is not a treat");
        helper.assertFalse(bleep.revealed(), "its favourite is a secret until you find it");
        helper.assertTrue(bleep.give(owner, new ItemStack(Items.COMPASS)), "it takes its favourite item");
        helper.assertTrue(bleep.revealed(), "giving it its favourite shows you what it is");
        helper.assertTrue(bleep.bond() == Bond.GIFT + Bond.FAVOURITE, "its favourite is worth 10, bond is " + bleep.bond());
        helper.assertFalse(bleep.give(owner, new ItemStack(Items.COMPASS)), "only one favourite a day counts");
        helper.assertTrue(bleep.bond() == Bond.GIFT + Bond.FAVOURITE, "a second favourite the same day is not worth more");
        helper.succeed();
    }

    private static void hittingItOrSwitchingItOffLowersTheBond(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        bleep.setBond(50);
        bleep.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(owner), 1);
        helper.assertTrue(bleep.bond() == 50 + Bond.HIT_BY_OWNER, "a hit from its owner costs 5, bond is " + bleep.bond());
        bleep.setBattery(0);
        helper.assertTrue(bleep.bond() == 50 + Bond.HIT_BY_OWNER + Bond.SWITCHED_OFF, "switching off costs 3, bond is " + bleep.bond());
        helper.succeed();
    }

    private static void aNewLevelIsCelebrated(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        bleep.setBond(24);
        helper.assertTrue(Bond.level(bleep.bond()) == 0, "24 is still New");
        bleep.pet();
        helper.assertTrue(Bond.level(bleep.bond()) == 1, "25 is Friend");
        helper.succeedWhen(() -> helper.assertTrue(bleep.emotion() == Emotion.EXCITED, "a new level makes it excited"));
    }

    private static void itDawdlesThenDoesItAnyway(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        helper.assertTrue(BleepCommand.obey(owner, bleep, BleepCommand.STAY, 0), "it will do it");
        helper.assertFalse(bleep.isOrderedToSit(), "a new bleep that rolls low dawdles first");
        helper.assertTrue(bleep.dawdling(), "it should be dawdling");
        helper.runAfterDelay(5, () -> helper.assertTrue(bleep.emotion() == Emotion.STUBBORN, "it pulls a stubborn face, it shows " + bleep.emotion()));
        helper.succeedWhen(() -> helper.assertTrue(bleep.isOrderedToSit() && !bleep.dawdling(), "after dawdling it stays anyway"));
    }

    private static void inDangerItAlwaysListens(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        bleep.setBattery(10);
        helper.assertTrue(BleepCommand.obey(owner, bleep, BleepCommand.STAY, 0), "it will do it");
        helper.assertTrue(bleep.isOrderedToSit(), "with a low battery it does not dawdle");
        helper.succeed();
    }

    private static void aNewMobGetsAThought(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        // looked up by name: 26.3 and 26.1 keep their entity types in different classes
        @SuppressWarnings("unchecked")
        EntityType<net.minecraft.world.entity.Mob> cow = (EntityType<net.minecraft.world.entity.Mob>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("cow"));
        helper.spawnWithNoFreeWill(cow, FAR);
        helper.succeedWhen(() -> {
            helper.assertTrue(bleep.seenMobs().contains("minecraft:cow"), "it should have a look at the cow");
            helper.assertTrue(bleep.thought().equals("mob.bleep.cow"), "and think about it, it thinks " + bleep.thought());
        });
    }

    private static void itBringsItsFavouriteToItsOwner(GameTestHelper helper) {
        Player owner = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(0.5, 0, 1.5));
        owner.snapTo(at.x, at.y, at.z);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(owner);
        bleep.setObsession(COMPASS);
        helper.spawnItem(Items.COMPASS, new Vec3(5.5, 0.2, 1.5));
        helper.succeedWhen(() -> {
            helper.assertTrue(owner.getInventory().contains(new ItemStack(Items.COMPASS)), "it should bring the compass to its owner, it holds " + bleep.getMainHandItem());
            helper.assertTrue(bleep.revealed(), "finding it shows you what its favourite is");
        });
    }

    /** As in a real game: its owner holds its favourite (a music disc), it circles, then the owner throws it away. */
    private static void itFetchesAThrownMusicDisc(GameTestHelper helper) {
        Player owner = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(0.5, 0, 1.5));
        owner.snapTo(at.x, at.y, at.z);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MUSIC_DISC_CAT));
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(owner);
        bleep.setObsession(Obsession.ALL.indexOf(Obsession.ALL.stream().filter(o -> o.id().equals("music_disc")).findFirst().orElseThrow()));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(bleep.revealed(), "holding its favourite should make it circle and show what it is");
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            var thrown = helper.spawnItem(Items.MUSIC_DISC_CAT, new Vec3(5.5, 0.2, 1.5));
            thrown.setPickUpDelay(40);
            thrown.setThrower(owner);
        });
        helper.runAfterDelay(41, () -> helper.succeedWhen(() -> helper.assertTrue(owner.getInventory().contains(new ItemStack(Items.MUSIC_DISC_CAT)),
                "it should fetch the thrown disc and bring it back; it holds " + bleep.getMainHandItem() + ", doing " + bleep.activity() + ", at " + helper.relativeVec(bleep.position()))));
    }

    private static void aTorchNeedsAFriend(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        helper.assertFalse(bleep.give(owner, new ItemStack(Items.TORCH)), "a new bleep does not carry a torch yet");
        bleep.setBond(30);
        helper.assertTrue(bleep.give(owner, new ItemStack(Items.TORCH)), "a Friend carries a torch");
        helper.assertTrue(bleep.getOffhandItem().is(Items.TORCH), "in its left hand");
        helper.succeed();
    }

    private static void itsTorchLightsTheWay(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        bleep.setBond(30);
        bleep.give(owner, new ItemStack(Items.TORCH));
        helper.runAfterDelay(5, () -> {
            BlockPos light = bleep.light();
            helper.assertTrue(light != null && helper.getLevel().getBlockState(light).is(Blocks.LIGHT), "its torch should light the block over it");
            helper.assertTrue(bleep.takeTorch(owner), "its owner can take the torch back");
            helper.assertTrue(helper.getLevel().getBlockState(light).isAir(), "without the torch the light goes out");
            helper.succeed();
        });
    }

    /** Its owner goes for a monster: a Buddy joins in. (Hitting back for its owner needs real damage, which the test player cannot take.) */
    private static void itFightsForItsOwner(GameTestHelper helper) {
        Player owner = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 0, 1.5));
        owner.snapTo(at.x, at.y, at.z);
        BleepEntity buddy = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        buddy.tame(owner);
        buddy.setBond(60);
        @SuppressWarnings("unchecked")
        EntityType<net.minecraft.world.entity.Mob> zombieType = (EntityType<net.minecraft.world.entity.Mob>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("zombie"));
        var zombie = helper.spawnWithNoFreeWill(zombieType, FAR);
        owner.tickCount = 100; // the test player does not tick: give its hit a moment of its own
        owner.setLastHurtMob(zombie); // its owner hit the zombie
        helper.succeedWhen(() -> helper.assertTrue(buddy.getTarget() == zombie, "a Buddy should go for the zombie its owner hit"));
    }

    /** From Buddy it brings what lies about; what its owner threw away it leaves. */
    private static void aBuddyBringsWhatItFinds(GameTestHelper helper) {
        Player owner = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(0.5, 0, 1.5));
        owner.snapTo(at.x, at.y, at.z);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(owner);
        bleep.setBond(60);
        bleep.setObsession(COMPASS);
        helper.spawnItem(Items.STRING, new Vec3(5.5, 0.2, 1.5));
        helper.succeedWhen(() -> helper.assertTrue(owner.getInventory().contains(new ItemStack(Items.STRING)), "a Buddy should bring the string to its owner"));
    }

    private static void aBoredBleepStealsACookie(GameTestHelper helper) {
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(player(helper));
        BlockPos chestPos = new BlockPos(6, 0, 1);
        helper.setBlock(chestPos, Blocks.CHEST);
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) helper.getBlockEntity(chestPos, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.COOKIE, 3));
        helper.assertTrue(bleep.beNaughty(NaughtyGoal.Trick.COOKIE), "there is a cookie to steal");
        helper.succeedWhen(() -> helper.assertTrue(chest.getItem(0).getCount() == 2, "it should take one cookie, the chest has " + chest.getItem(0)));
    }

    private static void aBoredBleepMovesATorch(GameTestHelper helper) {
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(player(helper));
        BlockPos torch = new BlockPos(5, 0, 1);
        helper.setBlock(torch, Blocks.TORCH);
        helper.assertTrue(bleep.beNaughty(NaughtyGoal.Trick.TORCH), "there is a torch to move");
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(torch).isAir(), "the torch should be gone from where it stood");
            // it may stand outside the bench, where the tests next door tidy up: look the moment it is put down
            BlockPos moved = bleep.movedTorch();
            helper.assertTrue(moved != null && helper.getLevel().getBlockState(moved).is(Blocks.TORCH), "and stand somewhere else, nothing broken");
        });
    }

    private static void redstoneChargesIt(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        bleep.setBattery(50);
        helper.assertTrue(bleep.give(owner, new ItemStack(Items.REDSTONE)), "redstone charges it");
        helper.assertTrue(Math.abs(bleep.battery() - (50 + BleepEntity.REDSTONE_CHARGE)) < 0.01f, "8% a dust, battery is " + bleep.battery());
        helper.assertTrue(bleep.bond() == Bond.GIFT, "and it is still a treat");
        helper.assertTrue(bleep.give(owner, new ItemStack(Items.REDSTONE_BLOCK)), "a block of redstone charges it more");
        helper.assertTrue(bleep.battery() == BleepEntity.FULL, "up to full, battery is " + bleep.battery());
        helper.assertFalse(bleep.give(owner, new ItemStack(Items.REDSTONE_BLOCK)), "a full bleep takes no more");
        helper.succeed();
    }

    private static void redstoneWakesAnEmptyBleep(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        bleep.setBattery(0);
        bleep.give(owner, new ItemStack(Items.REDSTONE_BLOCK));
        bleep.give(owner, new ItemStack(Items.REDSTONE_BLOCK));
        helper.succeedWhen(() -> helper.assertFalse(bleep.asleep(), "charged full with redstone, it wakes up"));
    }

    private static void aPowerBankTopsItUp(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        bleep.setBattery(30);
        ItemStack bank = new ItemStack(BleepContent.POWER_BANK.get());
        bleep.usePowerBank(owner, bank);
        helper.assertTrue(bleep.battery() == BleepEntity.FULL, "a full bank fills it, battery is " + bleep.battery());
        helper.assertTrue(bank.getDamageValue() == 70, "and has 30 left, it used " + bank.getDamageValue());
        BlockPos home = homebase(helper, HOME);
        helper.getLevel().getBlockState(home).useItemOn(bank, helper.getLevel(), owner, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(home), Direction.UP, home, false));
        helper.assertTrue(bank.getDamageValue() == 0, "a homebase fills the bank again");
        helper.succeed();
    }

    private static void lightningOverchargesIt(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        bleep.setBattery(10);
        @SuppressWarnings("unchecked")
        EntityType<net.minecraft.world.entity.LightningBolt> boltType = (EntityType<net.minecraft.world.entity.LightningBolt>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("lightning_bolt"));
        var bolt = boltType.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        bleep.thunderHit(helper.getLevel(), bolt);
        helper.assertTrue(bleep.battery() == BleepEntity.FULL, "lightning fills it up");
        helper.assertTrue(bleep.isAlive() && bleep.getHealth() == bleep.getMaxHealth(), "and does not hurt it");
        helper.assertTrue(bleep.overcharged(), "it is overcharged");
        helper.succeedWhen(() -> helper.assertTrue(bleep.emotion() == Emotion.GLITCH || bleep.emotion() == Emotion.EXCITED, "hyper and glitchy, it shows " + bleep.emotion()));
    }

    /** A Minecraft item by name: 26.3 and 26.1 keep some (the dyes) in different places. */
    private static ItemStack vanilla(String name) {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(name)));
    }

    private static void dyePaintsIt(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        helper.assertTrue(bleep.give(owner, vanilla("red_dye")), "it takes red dye");
        helper.assertTrue(bleep.color() == (net.minecraft.world.item.DyeColor.RED.getTextureDiffuseColor() & 0xFFFFFF), "and turns red");
        helper.assertTrue(bleep.give(owner, vanilla("white_dye")), "white dye");
        helper.assertTrue(bleep.color() == -1, "washes it back to its own white");
        helper.succeed();
    }

    private static void itWearsAHatAndASticker(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        bleep.give(owner, new ItemStack(BleepContent.ACCESSORIES.get(Accessory.PARTY_HAT).get()));
        helper.assertTrue(bleep.hat() == Accessory.PARTY_HAT, "it wears the party hat");
        bleep.give(owner, new ItemStack(BleepContent.ACCESSORIES.get(Accessory.TOP_HAT).get()));
        helper.assertTrue(bleep.hat() == Accessory.TOP_HAT, "a new hat takes its place");
        helper.assertTrue(owner.getInventory().contains(new ItemStack(BleepContent.ACCESSORIES.get(Accessory.PARTY_HAT).get())), "and the old one comes back");
        bleep.give(owner, new ItemStack(BleepContent.ACCESSORIES.get(Accessory.STICKER_HEART).get()));
        helper.assertTrue(bleep.sticker() == Accessory.STICKER_HEART && bleep.hat() == Accessory.TOP_HAT, "a sticker goes on next to the hat");
        helper.assertTrue(bleep.takeOff(owner), "shears take them off");
        helper.assertTrue(bleep.hat() == null && bleep.sticker() == null, "both");
        helper.assertTrue(owner.getInventory().contains(new ItemStack(BleepContent.ACCESSORIES.get(Accessory.STICKER_HEART).get())), "and give them back");
        helper.succeed();
    }

    private static void itsNameScrollsAcross(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        bleep.setCustomName(Component.literal("Bolt"));
        helper.assertTrue(bleep.display() == Display.NAME, "a new name scrolls across its display");
        helper.succeed();
    }

    private static void anArrowPointsHome(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), FAR);
        bleep.tame(owner);
        BlockPos home = homebase(helper, HOME);
        bleep.setHome(GlobalPos.of(helper.getLevel().dimension(), home));
        bleep.goHome();
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(bleep.display() == Display.HOME, "on its way home its display shows the way, it shows " + bleep.display());
            helper.succeed();
        });
    }

    private static void aNameAlwaysShows(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        bleep.setCustomName(Component.literal("Bolt"));
        helper.assertTrue(bleep.isCustomNameVisible(), "a bleep's name should always show, like a player's");
        helper.succeed();
    }

    /** A test player with a core in their hand. */
    private static Player withCore(GameTestHelper helper) {
        Player player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BleepContent.BLEEP_CORE.get()));
        return player;
    }

    /** Uses the core in the player's hand on the ground in the middle of the bench. */
    private static void useCore(GameTestHelper helper, Player player) {
        BlockPos floor = helper.absolutePos(SPOT.below());
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        BleepContent.BLEEP_CORE.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static void theCoreNeedsItsParts(GameTestHelper helper) {
        Player player = withCore(helper);
        player.getInventory().add(new ItemStack(BleepContent.BLEEP_TRACKS.get()));
        useCore(helper, player);
        helper.assertTrue(player.getMainHandItem().is(BleepContent.BLEEP_CORE.get()), "without a display and arms the core stays in your hand");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(BleepEntity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(SPOT)).inflate(8)).isEmpty(), "no bleep without all its parts");
        helper.assertTrue(player.getInventory().contains(new ItemStack(BleepContent.BLEEP_TRACKS.get())), "the parts you have stay yours");
        helper.succeed();
    }

    private static void theCorePutsABleepTogether(GameTestHelper helper) {
        Player player = withCore(helper);
        for (var part : BleepCoreItem.parts()) player.getInventory().add(new ItemStack(part));
        useCore(helper, player);
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the core should go into the bleep");
        for (var part : BleepCoreItem.parts()) helper.assertFalse(player.getInventory().contains(new ItemStack(part)), "the " + part + " should go into the bleep");
        BleepEntity bleep = helper.findOneEntity(BleepContent.BLEEP.get());
        helper.assertTrue(bleep.isOwnedBy(player), "the bleep should belong to whoever built it");
        helper.assertTrue(bleep.assembly() > 0, "it should be put together part by part");
        helper.runAfterDelay(BleepEntity.ASSEMBLE_TICKS + 5, () -> helper.assertTrue(bleep.emotion() == Emotion.BOOT, "once together its screen starts up, it shows " + bleep.emotion()));
        helper.runAfterDelay(BleepEntity.ASSEMBLE_TICKS + 10, () -> helper.succeedWhen(() -> helper.assertTrue(bleep.emotion() == Emotion.EXCITED, "after starting up it should be excited, it shows " + bleep.emotion())));
    }

    /** Opens a chest of this loot table many times over and counts a part. */
    private static int rolls(GameTestHelper helper, String table, net.minecraft.world.item.Item part, int times) {
        var level = helper.getLevel();
        var loot = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, net.minecraft.resources.Identifier.withDefaultNamespace(table)));
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, helper.absoluteVec(Vec3.ZERO))
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        int found = 0;
        for (int i = 0; i < times; i++) for (ItemStack stack : loot.getRandomItems(params)) if (stack.is(part)) found++;
        return found;
    }

    private static void partsTurnUpInChests(GameTestHelper helper) {
        helper.assertTrue(rolls(helper, "chests/simple_dungeon", BleepContent.BLEEP_CORE.get(), 100) > 0, "a dungeon chest should sometimes hold a core");
        helper.assertTrue(rolls(helper, "chests/abandoned_mineshaft", BleepContent.BLEEP_CORE.get(), 100) > 0, "a mineshaft chest should sometimes hold a core");
        helper.assertTrue(rolls(helper, "chests/village/village_plains_house", BleepContent.BLEEP_CORE.get(), 100) == 0, "a plain house has no core");
        helper.succeed();
    }
}
