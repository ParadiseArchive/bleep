package io.hookdev.bleep.client;

import io.hookdev.bleep.Bleep;
import io.hookdev.bleep.entity.BleepEntity;
import io.hookdev.bleep.entity.Emotion;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Draws a bleep: the white body, and on its display the face for how it feels, lit up so it shows in the dark. Every
 * few seconds it blinks; the start-up screen runs through its frames.
 */
public class BleepRenderer extends MobRenderer<BleepEntity, BleepRenderState, BleepModel> {
    private static final Identifier TEXTURE = Bleep.id("textures/entity/bleep/bleep.png");
    /** Ticks between blinks, and how long a blink lasts. */
    private static final int BLINK_EVERY = 90, BLINK = 3;
    /** A screen that is still off, while it is being put together. */
    private static final Identifier OFF = Bleep.id("textures/entity/bleep/face/off.png");

    public BleepRenderer(EntityRendererProvider.Context context) {
        super(context, new BleepModel(context.bakeLayer(BleepModel.LAYER)), 0.45f);
        this.addLayer(new LivingEntityEmissiveLayer<>(this, state -> state.assembly > 0 || state.abandoned ? OFF : DisplayTextures.shows(state.display) ? DisplayTextures.texture(state) : face(state.emotion, state.ageInTicks, state.id), (state, ageInTicks) -> 1.0f,
                new BleepModel(context.bakeLayer(BleepModel.LAYER)), RenderTypes::eyes, false));
        this.addLayer(new ItemInHandLayer<>(this));
        this.addLayer(new AccessoryLayer(this, new AccessoryModel(context.bakeLayer(AccessoryModel.LAYER))));
    }

    /**
     * The face on the display: textures/entity/bleep/face/<emotion>.png, with _blink while it blinks. Also drawn on
     * the info screen.
     */
    public static Identifier face(Emotion emotion, float ageInTicks, int id) {
        if (emotion == Emotion.BOOT) return Bleep.id("textures/entity/bleep/face/boot_" + ((int) (ageInTicks / 8) % 3) + ".png");
        if (emotion == Emotion.GLITCH) return Bleep.id("textures/entity/bleep/face/glitch_" + ((int) (ageInTicks / 2) % 2) + ".png");
        boolean blink = emotion != Emotion.ASLEEP && emotion != Emotion.SURPRISED && emotion != Emotion.EXCITED
                && (int) (ageInTicks + id * 37) % BLINK_EVERY < BLINK;
        return Bleep.id("textures/entity/bleep/face/" + emotion.texture() + (blink ? "_blink" : "") + ".png");
    }

    /** Dye paints the whole bleep; its white shell shows the colour, its dark parts stay dark. */
    @Override
    protected int getModelTint(BleepRenderState state) {
        return state.color;
    }

    @Override
    public Identifier getTextureLocation(BleepRenderState state) {
        return TEXTURE;
    }

    @Override
    public BleepRenderState createRenderState() {
        return new BleepRenderState();
    }

    @Override
    public void extractRenderState(BleepEntity bleep, BleepRenderState state, float partialTicks) {
        super.extractRenderState(bleep, state, partialTicks);
        state.emotion = bleep.emotion();
        state.id = bleep.getId();
        ArmedEntityRenderState.extractArmedEntityRenderState(bleep, state, this.itemModelResolver, partialTicks);
        state.carrying = !bleep.getMainHandItem().isEmpty();
        state.assembly = bleep.assembly();
        state.color = bleep.color() == -1 ? -1 : 0xFF000000 | bleep.color();
        state.hat = bleep.hat() != null ? bleep.hat() : bleep.party() ? io.hookdev.bleep.entity.Accessory.PARTY_HAT : null; // its birthday
        state.sticker = bleep.sticker();
        state.display = bleep.display();
        state.dancing = bleep.activity() == io.hookdev.bleep.entity.Activity.DANCING;
        state.abandoned = bleep.activity() == io.hookdev.bleep.entity.Activity.ABANDONED;
        state.name = bleep.hasCustomName() ? bleep.getCustomName().getString() : "BLEEP";
        var home = bleep.home();
        state.homeAngle = home != null && home.dimension().equals(bleep.level().dimension())
                ? DisplayTextures.homeAngle(bleep.yBodyRot, home.pos().getX() + 0.5 - bleep.getX(), home.pos().getZ() + 0.5 - bleep.getZ()) : Float.NaN;
        state.dream = bleep.dream();
        state.dreamName = state.dream.startsWith("mob:") ? net.minecraft.network.chat.Component.translatable(state.dream.substring(4)).getString() : "";
        var treasure = bleep.treasure();
        var player = net.minecraft.client.Minecraft.getInstance().player;
        state.treasureAngle = treasure != null ? DisplayTextures.homeAngle(bleep.yBodyRot, treasure.getX() + 0.5 - bleep.getX(), treasure.getZ() + 0.5 - bleep.getZ()) : Float.NaN;
        state.treasureDistance = treasure != null && player != null ? (float) Math.sqrt(player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(treasure))) : 99;
    }
}
