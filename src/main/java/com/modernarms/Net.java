package com.modernarms;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.Vec3d;

/** All network payloads. */
public final class Net {
	public static final byte EV_FIRE = 0, EV_RELOAD = 1, EV_BOLT = 2;
	public static final byte TRACER_PISTOL = 0, TRACER_SNIPER = 1;

	/** Server -> clients: something happened with a gun carried by an entity (animation + muzzle fx). */
	public record GunEventS2C(int entityId, byte event, byte weapon, int duration) implements CustomPayload {
		public static final Id<GunEventS2C> ID = new Id<>(ModernArms.id("gun_event"));
		public static final PacketCodec<RegistryByteBuf, GunEventS2C> CODEC = PacketCodec.of(GunEventS2C::write, GunEventS2C::new);
		public GunEventS2C(RegistryByteBuf b) { this(b.readVarInt(), b.readByte(), b.readByte(), b.readVarInt()); }
		void write(RegistryByteBuf b) { b.writeVarInt(entityId); b.writeByte(event); b.writeByte(weapon); b.writeVarInt(duration); }
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	/** Server -> clients: draw a bullet tracer between two points. */
	public record TracerS2C(Vec3d from, Vec3d to, byte kind) implements CustomPayload {
		public static final Id<TracerS2C> ID = new Id<>(ModernArms.id("tracer"));
		public static final PacketCodec<RegistryByteBuf, TracerS2C> CODEC = PacketCodec.of(TracerS2C::write, TracerS2C::new);
		public TracerS2C(RegistryByteBuf b) {
			this(new Vec3d(b.readDouble(), b.readDouble(), b.readDouble()), new Vec3d(b.readDouble(), b.readDouble(), b.readDouble()), b.readByte());
		}
		void write(RegistryByteBuf b) {
			b.writeDouble(from.x); b.writeDouble(from.y); b.writeDouble(from.z);
			b.writeDouble(to.x); b.writeDouble(to.y); b.writeDouble(to.z); b.writeByte(kind);
		}
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	/** Server -> clients: rocket explosion visuals/echo/screen shake. */
	public record ExplosionS2C(double x, double y, double z, int blockStateId, float power) implements CustomPayload {
		public static final Id<ExplosionS2C> ID = new Id<>(ModernArms.id("explosion"));
		public static final PacketCodec<RegistryByteBuf, ExplosionS2C> CODEC = PacketCodec.of(ExplosionS2C::write, ExplosionS2C::new);
		public ExplosionS2C(RegistryByteBuf b) { this(b.readDouble(), b.readDouble(), b.readDouble(), b.readVarInt(), b.readFloat()); }
		void write(RegistryByteBuf b) { b.writeDouble(x); b.writeDouble(y); b.writeDouble(z); b.writeVarInt(blockStateId); b.writeFloat(power); }
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	/** Client -> server: fire button pressed (sniper/RPG/pistol left click). */
	public record FireC2S(boolean scoped) implements CustomPayload {
		public static final Id<FireC2S> ID = new Id<>(ModernArms.id("fire"));
		public static final PacketCodec<RegistryByteBuf, FireC2S> CODEC = PacketCodec.of(FireC2S::write, FireC2S::new);
		public FireC2S(RegistryByteBuf b) { this(b.readBoolean()); }
		void write(RegistryByteBuf b) { b.writeBoolean(scoped); }
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	/** Client -> server: reload key pressed. */
	public record ReloadC2S() implements CustomPayload {
		public static final Id<ReloadC2S> ID = new Id<>(ModernArms.id("reload"));
		public static final PacketCodec<RegistryByteBuf, ReloadC2S> CODEC = PacketCodec.unit(new ReloadC2S());
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	public static void init() {
		PayloadTypeRegistry.playS2C().register(GunEventS2C.ID, GunEventS2C.CODEC);
		PayloadTypeRegistry.playS2C().register(TracerS2C.ID, TracerS2C.CODEC);
		PayloadTypeRegistry.playS2C().register(ExplosionS2C.ID, ExplosionS2C.CODEC);
		PayloadTypeRegistry.playC2S().register(FireC2S.ID, FireC2S.CODEC);
		PayloadTypeRegistry.playC2S().register(ReloadC2S.ID, ReloadC2S.CODEC);
	}
}
