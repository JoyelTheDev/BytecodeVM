package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.array;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;
import nhcm.bytecodevm.generator.virtualization.vminterpret.NumericType;
import org.objectweb.asm.Opcodes;

import java.util.Set;

public class NewArrayBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.NEW_ARRAY.getOpcodes();
    }

    @Override
    public void generate(AdvIBdr ib, InterpretContext context, Opcs opcode)
    {
        switch (opcode)
        {
            case NEWARRAY -> generatePrimitiveArray(ib, context);
            case ANEWARRAY -> generateReferenceArray(ib, context);
            case MULTIANEWARRAY -> generateMultiArray(ib, context);
            default -> throw new IllegalArgumentException("Unsupported new array opcode: " + opcode);
        }
    }

    private static void generatePrimitiveArray(AdvIBdr ib, InterpretContext context)
    {
        Local atype = context.intLocal("arrayAType", InterpretContext.ARRAY_ATYPE);
        Local component = context.local("arrayComponent", "java/lang/Class", InterpretContext.ARRAY_COMPONENT);

        context.nextOperand(ib, atype);
        ib.switchLookup(
                atype,
                b -> b.throwValue(AdvIBdr.newObject(
                        "java/lang/IllegalArgumentException",
                        AdvIBdr.constant("Unknown NEWARRAY atype"))),
                AdvIBdr.switchCase(Opcodes.T_BOOLEAN, b -> b.set(component, primitiveType("java/lang/Boolean"))),
                AdvIBdr.switchCase(Opcodes.T_CHAR, b -> b.set(component, primitiveType("java/lang/Character"))),
                AdvIBdr.switchCase(Opcodes.T_FLOAT, b -> b.set(component, primitiveType("java/lang/Float"))),
                AdvIBdr.switchCase(Opcodes.T_DOUBLE, b -> b.set(component, primitiveType("java/lang/Double"))),
                AdvIBdr.switchCase(Opcodes.T_BYTE, b -> b.set(component, primitiveType("java/lang/Byte"))),
                AdvIBdr.switchCase(Opcodes.T_SHORT, b -> b.set(component, primitiveType("java/lang/Short"))),
                AdvIBdr.switchCase(Opcodes.T_INT, b -> b.set(component, primitiveType("java/lang/Integer"))),
                AdvIBdr.switchCase(Opcodes.T_LONG, b -> b.set(component, primitiveType("java/lang/Long"))));

        createSingleArray(ib, context, component);
    }

    private static void generateReferenceArray(AdvIBdr ib, InterpretContext context)
    {
        Local classIndex = context.intLocal("arrayClassIndex", InterpretContext.ARRAY_ATYPE);
        Local component = context.local("arrayComponent", "java/lang/Class", InterpretContext.ARRAY_COMPONENT);

        context.nextOperand(ib, classIndex);
        ib.set(component, context.loadClass(context.constantString(classIndex)));
        createSingleArray(ib, context, component);
    }

    private static void generateMultiArray(AdvIBdr ib, InterpretContext context)
    {
        Local classIndex = context.intLocal("arrayClassIndex", InterpretContext.ARRAY_ATYPE);
        Local component = context.local("arrayComponent", "java/lang/Class", InterpretContext.ARRAY_COMPONENT);
        Local dimensions = context.intLocal("arrayDimensions", InterpretContext.ARRAY_DIMENSIONS);
        Local lengths = context.local("arrayLengths", "[I", InterpretContext.ARRAY_LENGTHS);
        Local index = context.intLocal("arrayIndex", InterpretContext.ARRAY_INDEX);

        context.nextOperand(ib, classIndex);
        ib.set(component, context.loadClass(context.constantString(classIndex)));

        context.nextOperand(ib, dimensions);
        ib.set(lengths, AdvIBdr.newArray("I", dimensions));
        ib.set(index, AdvIBdr.minus(dimensions, AdvIBdr.constant(1)));
        ib.whileLoop(
                AdvIBdr.greaterOrEqual(index, AdvIBdr.constant(0)),
                b -> {
                    popInt(b, context, InterpretContext.RIGHT_VALUE);
                    b.setArray(lengths, index, context.rightValue(NumericType.INT));
                    b.increment(index, -1);
                });

        ib.set(index, AdvIBdr.constant(0));
        ib.whileLoop(
                AdvIBdr.lessThan(index, dimensions),
                b -> {
                    b.set(component, AdvIBdr.callVirtual(
                            component,
                            "java/lang/Class",
                            "getComponentType",
                            "java/lang/Class"));
                    b.increment(index, 1);
                });

        pushObject(ib, context, AdvIBdr.callStatic(
                "java/lang/reflect/Array",
                "newInstance",
                "java/lang/Object",
                component,
                lengths));
    }

    private static void createSingleArray(AdvIBdr ib, InterpretContext context, Expr component)
    {
        popInt(ib, context, InterpretContext.RIGHT_VALUE);
        pushObject(ib, context, AdvIBdr.callStatic(
                "java/lang/reflect/Array",
                "newInstance",
                "java/lang/Object",
                component,
                context.rightValue(NumericType.INT)));
    }

    private static Expr primitiveType(String boxedOwner)
    {
        return AdvIBdr.staticField(boxedOwner, "TYPE", "java/lang/Class");
    }
}
