package nhcm.bytecodevm.generator.integrity;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.data.VMIntegrityPlan;
import nhcm.bytecodevm.utils.RandomUtils;

/** Emits the authenticated, reversible integrity-state envelope. */
final class IntegrityCacheCodec
{
    private IntegrityCacheCodec()
    {
    }

    static VMIntegrityPlan.CacheLayout randomLayout()
    {
        int multiplier = nonZeroRandom() | 1;
        return new VMIntegrityPlan.CacheLayout(
                nonZeroRandom(),
                5 + RandomUtils.randomInt(22),
                multiplier,
                inverseOdd(multiplier),
                nonZeroRandom(),
                nonZeroRandom(),
                5 + RandomUtils.randomInt(22),
                nonZeroRandom() | 1,
                nonZeroRandom());
    }

    static Local emitEncode(
            AdvIBdr ib,
            Expr capability,
            VMIntegrityPlan.CacheLayout layout,
            String prefix)
    {
        Local encoded = ib.var(prefix + "Encoded", "I");
        Local tag = ib.var(prefix + "Tag", "I");
        Local envelope = ib.var(prefix + "Envelope", "J");
        ib.set(encoded, AdvIBdr.plus(
                AdvIBdr.multiply(
                        rotateLeft(
                                AdvIBdr.bitXor(
                                        capability,
                                        AdvIBdr.constant(layout.xorMask())),
                                layout.rotation()),
                        AdvIBdr.constant(layout.multiplier())),
                AdvIBdr.constant(layout.addend())));
        ib.set(tag, tag(encoded, capability, layout));
        ib.set(envelope, AdvIBdr.bitOr(
                AdvIBdr.shiftLeft(
                        AdvIBdr.cast(encoded, "J"),
                        AdvIBdr.constant(32)),
                AdvIBdr.bitAnd(
                        AdvIBdr.cast(
                                AdvIBdr.bitOr(
                                        AdvIBdr.shiftLeft(tag, AdvIBdr.constant(1)),
                                        AdvIBdr.constant(1)),
                                "J"),
                        AdvIBdr.constant(0xFFFF_FFFFL))));
        return envelope;
    }

    static Local emitDecode(
            AdvIBdr ib,
            Expr envelope,
            VMIntegrityPlan.CacheLayout layout,
            String prefix)
    {
        Local encoded = ib.var(prefix + "Encoded", "I");
        Local metadata = ib.var(prefix + "Metadata", "I");
        Local capability = ib.var(prefix + "Capability", "I");
        Local expectedTag = ib.var(prefix + "ExpectedTag", "I");
        Local mismatch = ib.var(prefix + "Mismatch", "I");

        ib.set(encoded, AdvIBdr.cast(
                AdvIBdr.unsignedShiftRight(envelope, AdvIBdr.constant(32)),
                "I"));
        ib.set(metadata, AdvIBdr.cast(envelope, "I"));
        Expr unscaled = AdvIBdr.multiply(
                AdvIBdr.minus(encoded, AdvIBdr.constant(layout.addend())),
                AdvIBdr.constant(layout.inverseMultiplier()));
        ib.set(capability, AdvIBdr.bitXor(
                rotateRight(unscaled, layout.rotation()),
                AdvIBdr.constant(layout.xorMask())));
        ib.set(expectedTag, tag(encoded, capability, layout));
        ib.set(mismatch, AdvIBdr.bitOr(
                AdvIBdr.bitXor(
                        AdvIBdr.unsignedShiftRight(metadata, AdvIBdr.constant(1)),
                        expectedTag),
                AdvIBdr.bitXor(
                        AdvIBdr.bitAnd(metadata, AdvIBdr.constant(1)),
                        AdvIBdr.constant(1))));
        Expr nonZero = AdvIBdr.unsignedShiftRight(
                AdvIBdr.bitOr(mismatch, AdvIBdr.negative(mismatch)),
                AdvIBdr.constant(31));
        ib.set(capability, AdvIBdr.bitXor(
                capability,
                AdvIBdr.multiply(nonZero, AdvIBdr.constant(layout.failMix()))));
        return capability;
    }

    private static Expr tag(
            Expr encoded,
            Expr capability,
            VMIntegrityPlan.CacheLayout layout)
    {
        Expr mixed = AdvIBdr.bitXor(
                encoded,
                rotateLeft(
                        AdvIBdr.plus(capability, AdvIBdr.constant(layout.tagSalt())),
                        layout.tagRotation()));
        mixed = AdvIBdr.bitXor(
                mixed,
                AdvIBdr.unsignedShiftRight(mixed, AdvIBdr.constant(16)));
        mixed = AdvIBdr.multiply(mixed, AdvIBdr.constant(layout.tagMultiplier()));
        mixed = AdvIBdr.bitXor(
                mixed,
                AdvIBdr.unsignedShiftRight(mixed, AdvIBdr.constant(13)));
        return AdvIBdr.bitAnd(mixed, AdvIBdr.constant(Integer.MAX_VALUE));
    }

    private static Expr rotateLeft(Expr value, int distance)
    {
        return AdvIBdr.bitOr(
                AdvIBdr.shiftLeft(value, AdvIBdr.constant(distance)),
                AdvIBdr.unsignedShiftRight(value, AdvIBdr.constant(32 - distance)));
    }

    private static Expr rotateRight(Expr value, int distance)
    {
        return AdvIBdr.bitOr(
                AdvIBdr.unsignedShiftRight(value, AdvIBdr.constant(distance)),
                AdvIBdr.shiftLeft(value, AdvIBdr.constant(32 - distance)));
    }

    private static int inverseOdd(int value)
    {
        int inverse = value;
        inverse *= 2 - value * inverse;
        inverse *= 2 - value * inverse;
        inverse *= 2 - value * inverse;
        inverse *= 2 - value * inverse;
        inverse *= 2 - value * inverse;
        return inverse;
    }

    private static int nonZeroRandom()
    {
        int value;
        do
        {
            value = RandomUtils.randomInt();
        } while (value == 0);
        return value;
    }
}
