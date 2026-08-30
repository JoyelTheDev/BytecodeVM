package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.array;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;
import nhcm.bytecodevm.generator.virtualization.vminterpret.NumericType;

import java.util.Set;

public class LoadArrayBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.LOAD_ARRAY.getOpcodes();
    }

    @Override
    public void generate(AdvIBdr ib, InterpretContext context, Opcs opcode)
    {
        popInt(ib, context, InterpretContext.MIDDLE_VALUE);
        popObject(ib, context, InterpretContext.RIGHT_VALUE);

        switch (opcode)
        {
            case IALOAD -> pushNumber(ib, context, NumericType.INT, AdvIBdr.arrayAt(
                    AdvIBdr.cast(context.objectLocal("array", InterpretContext.RIGHT_VALUE), "[I"),
                    context.middleValue()));
            case LALOAD -> pushNumber(ib, context, NumericType.LONG, AdvIBdr.arrayAt(
                    AdvIBdr.cast(context.objectLocal("array", InterpretContext.RIGHT_VALUE), "[J"),
                    context.middleValue()));
            case FALOAD -> pushNumber(ib, context, NumericType.FLOAT, AdvIBdr.arrayAt(
                    AdvIBdr.cast(context.objectLocal("array", InterpretContext.RIGHT_VALUE), "[F"),
                    context.middleValue()));
            case DALOAD -> pushNumber(ib, context, NumericType.DOUBLE, AdvIBdr.arrayAt(
                    AdvIBdr.cast(context.objectLocal("array", InterpretContext.RIGHT_VALUE), "[D"),
                    context.middleValue()));
            case AALOAD -> pushObject(ib, context, AdvIBdr.arrayAt(
                    AdvIBdr.cast(context.objectLocal("array", InterpretContext.RIGHT_VALUE), "[Ljava/lang/Object;"),
                    context.middleValue()));
            case BALOAD -> generateByteOrBooleanLoad(ib, context);
            case CALOAD -> pushNumber(ib, context, NumericType.INT, AdvIBdr.arrayAt(
                    AdvIBdr.cast(context.objectLocal("array", InterpretContext.RIGHT_VALUE), "[C"),
                    context.middleValue()));
            case SALOAD -> pushNumber(ib, context, NumericType.INT, AdvIBdr.arrayAt(
                    AdvIBdr.cast(context.objectLocal("array", InterpretContext.RIGHT_VALUE), "[S"),
                    context.middleValue()));
            default -> throw new IllegalArgumentException("Unsupported array load opcode: " + opcode);
        }
    }

    private static void generateByteOrBooleanLoad(AdvIBdr ib, InterpretContext context)
    {
        var array = context.objectLocal("array", InterpretContext.RIGHT_VALUE);
        ib.ifElse(
                AdvIBdr.isInstanceOf(array, "[Z"),
                b -> pushInt(b, context, AdvIBdr.arrayAt(
                        AdvIBdr.cast(array, "[Z"),
                        context.middleValue())),
                b -> pushInt(b, context, AdvIBdr.arrayAt(
                        AdvIBdr.cast(array, "[B"),
                        context.middleValue())));
    }
}
