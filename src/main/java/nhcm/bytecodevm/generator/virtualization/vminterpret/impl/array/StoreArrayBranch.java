package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.array;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;
import nhcm.bytecodevm.generator.virtualization.vminterpret.NumericType;

import java.util.Set;

public class StoreArrayBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.STORE_ARRAY.getOpcodes();
    }

    @Override
    public void generate(AdvIBdr ib, InterpretContext context, Opcs opcode)
    {
        Local index = context.middleValue();
        Local array = context.objectLocal("array", InterpretContext.RIGHT_VALUE);

        switch (opcode)
        {
            case IASTORE, CASTORE, SASTORE -> popInt(ib, context, InterpretContext.LEFT_VALUE);
            case LASTORE -> popLong(ib, context, InterpretContext.LEFT_VALUE);
            case FASTORE -> popFloat(ib, context, InterpretContext.LEFT_VALUE);
            case DASTORE -> popDouble(ib, context, InterpretContext.LEFT_VALUE);
            case AASTORE, BASTORE -> popObject(ib, context, InterpretContext.LEFT_VALUE);
            default -> throw new IllegalArgumentException("Unsupported array store opcode: " + opcode);
        }

        popInt(ib, context, InterpretContext.MIDDLE_VALUE);
        popObject(ib, context, InterpretContext.RIGHT_VALUE);

        switch (opcode)
        {
            case IASTORE -> ib.setArray(
                    AdvIBdr.cast(array, "[I"),
                    index,
                    context.leftValue(NumericType.INT));
            case LASTORE -> ib.setArray(
                    AdvIBdr.cast(array, "[J"),
                    index,
                    context.leftValue(NumericType.LONG));
            case FASTORE -> ib.setArray(
                    AdvIBdr.cast(array, "[F"),
                    index,
                    context.leftValue(NumericType.FLOAT));
            case DASTORE -> ib.setArray(
                    AdvIBdr.cast(array, "[D"),
                    index,
                    context.leftValue(NumericType.DOUBLE));
            case AASTORE -> ib.setArray(
                    AdvIBdr.cast(array, "[Ljava/lang/Object;"),
                    index,
                    context.objectLocal("value", InterpretContext.LEFT_VALUE));
            case BASTORE -> generateByteOrBooleanStore(ib, context, array, index);
            case CASTORE -> ib.setArray(
                    AdvIBdr.cast(array, "[C"),
                    index,
                    context.leftValue(NumericType.INT));
            case SASTORE -> ib.setArray(
                    AdvIBdr.cast(array, "[S"),
                    index,
                    context.leftValue(NumericType.INT));
            default -> throw new IllegalArgumentException("Unsupported array store opcode: " + opcode);
        }
    }

    private static void generateByteOrBooleanStore(
            AdvIBdr ib,
            InterpretContext context,
            Expr array,
            Expr index)
    {
        Local value = context.objectLocal("value", InterpretContext.LEFT_VALUE);
        ib.ifElse(
                AdvIBdr.isInstanceOf(array, "[Z"),
                b -> b.setArray(
                        AdvIBdr.cast(array, "[Z"),
                        index,
                        AdvIBdr.unbox(value, "Z")),
                b -> b.setArray(
                        AdvIBdr.cast(array, "[B"),
                        index,
                        AdvIBdr.callVirtual(
                                AdvIBdr.cast(value, "java/lang/Number"),
                                "java/lang/Number",
                                "byteValue",
                                "B")));
    }
}
