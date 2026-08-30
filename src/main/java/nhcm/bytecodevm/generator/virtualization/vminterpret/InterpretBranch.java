package nhcm.bytecodevm.generator.virtualization.vminterpret;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.enums.Opcs;
import org.objectweb.asm.tree.LabelNode;

import java.util.Set;

public abstract class InterpretBranch
{
    public abstract Set<Opcs> opcodes();

    public abstract void generate(AdvIBdr ib, InterpretContext context, Opcs opcode);

    public boolean term(Opcs opcode)
    {
        return false;
    }

    protected static void popObject(AdvIBdr ib, InterpretContext context)
    {
        popObject(ib, context, context.stackObject());
    }

    protected static void popObject(AdvIBdr ib, InterpretContext context, int local)
    {
        popObject(ib, context, context.localForSlot("object" + local, local));
    }

    protected static void popObject(AdvIBdr ib, InterpretContext context, Local target)
    {
        popIndexAndType(ib, context);
        loadAndClearBoxedStackValue(ib, context);

        ib.ifElse(
                AdvIBdr.equal(context.stackType(), AdvIBdr.constant(typeTag(NumericType.INT))),
                b -> b.set(context.stackObject(), NumericType.INT.box(wordAs(NumericType.INT, stackWord(context)))),
                b -> b.ifElse(
                        AdvIBdr.equal(context.stackType(), AdvIBdr.constant(typeTag(NumericType.LONG))),
                        bb -> bb.set(context.stackObject(), NumericType.LONG.box(wordAs(NumericType.LONG, stackWord(context)))),
                        bb -> bb.ifElse(
                                AdvIBdr.equal(context.stackType(), AdvIBdr.constant(typeTag(NumericType.FLOAT))),
                                bbb -> bbb.set(context.stackObject(), NumericType.FLOAT.box(wordAs(NumericType.FLOAT, stackWord(context)))),
                                bbb -> bbb.ifCondition(
                                        AdvIBdr.equal(context.stackType(), AdvIBdr.constant(typeTag(NumericType.DOUBLE))),
                                        number -> number.set(context.stackObject(), NumericType.DOUBLE.box(wordAs(NumericType.DOUBLE, stackWord(context))))))));

        ib.set(target, context.stackObject());
    }

    protected static void popObjectAndWidth(AdvIBdr ib, InterpretContext context, int valueLocal, int widthLocal)
    {
        ib.set(
                context.intLocal("width" + widthLocal, widthLocal),
                AdvIBdr.arrayAt(
                        context.stackWidths(),
                        AdvIBdr.minus(context.frameField(context.frame.stackPointer), AdvIBdr.constant(1))));
        popObject(ib, context, valueLocal);
    }

    protected static void popNumber(AdvIBdr ib, InterpretContext context, NumericType type)
    {
        popNumber(ib, context, type, context.rightValue(type));
    }

    protected static void popNumber(AdvIBdr ib, InterpretContext context, NumericType type, int local)
    {
        popNumber(ib, context, type, context.local("number" + local, type.descriptor(), local));
    }

    protected static void popNumber(AdvIBdr ib, InterpretContext context, NumericType type, Local target)
    {
        popIndexAndType(ib, context);
        ib.ifElse(
                AdvIBdr.equal(context.stackType(), AdvIBdr.constant(typeTag(type))),
                direct -> direct.set(target, wordAs(type, stackWord(context))),
                boxed -> {
                    loadAndClearBoxedStackValue(boxed, context);
                    boxed.set(target, type.unbox(context.stackObject()));
                });
    }

    protected static void popInt(AdvIBdr ib, InterpretContext context)
    {
        popNumber(ib, context, NumericType.INT);
    }

    protected static void popInt(AdvIBdr ib, InterpretContext context, int local)
    {
        popNumber(ib, context, NumericType.INT, local);
    }

    protected static void popLong(AdvIBdr ib, InterpretContext context, int local)
    {
        popNumber(ib, context, NumericType.LONG, local);
    }

    protected static void popFloat(AdvIBdr ib, InterpretContext context, int local)
    {
        popNumber(ib, context, NumericType.FLOAT, local);
    }

    protected static void popDouble(AdvIBdr ib, InterpretContext context, int local)
    {
        popNumber(ib, context, NumericType.DOUBLE, local);
    }

    protected static void pushObject(AdvIBdr ib, InterpretContext context)
    {
        pushObject(ib, context, context.stackObject());
    }

    protected static void pushObject(AdvIBdr ib, InterpretContext context, int local)
    {
        pushObject(ib, context, context.localForSlot("object" + local, local));
    }

    protected static void pushObject(AdvIBdr ib, InterpretContext context, Expr value)
    {
        pushObjectWithWidth(ib, context, value, AdvIBdr.constant(1));
    }

    protected static void pushObjectWithWidth(AdvIBdr ib, InterpretContext context, int local, int widthLocal)
    {
        pushObjectWithWidth(
                ib,
                context,
                context.localForSlot("object" + local, local),
                context.intLocal("width" + widthLocal, widthLocal));
    }

    protected static void pushObjectWithWidth(AdvIBdr ib, InterpretContext context, int width)
    {
        pushObjectWithWidth(ib, context, context.stackObject(), AdvIBdr.constant(width));
    }

    protected static void pushObjectWithWidth(AdvIBdr ib, InterpretContext context, Expr value, Expr width)
    {
        ib.setArray(context.stack(), context.frameField(context.frame.stackPointer), value);
        ib.setArray(context.stackTypes(), context.frameField(context.frame.stackPointer), AdvIBdr.constant(0));
        ib.setArray(context.stackWidths(), context.frameField(context.frame.stackPointer), width);
        incrementStackPointer(ib, context);
    }

    protected static void pushNumber(AdvIBdr ib, InterpretContext context, NumericType type)
    {
        pushNumber(ib, context, type, context.rightValue(type));
    }

    protected static void pushNumber(AdvIBdr ib, InterpretContext context, NumericType type, int local)
    {
        pushNumber(ib, context, type, context.local("number" + local, type.descriptor(), local));
    }

    protected static void pushNumber(AdvIBdr ib, InterpretContext context, NumericType type, Expr value)
    {
        ib.setArray(context.stack(), context.frameField(context.frame.stackPointer), AdvIBdr.nullValue("java/lang/Object"));
        ib.setArray(context.stackWords(), context.frameField(context.frame.stackPointer), wordFrom(type, value));
        ib.setArray(context.stackTypes(), context.frameField(context.frame.stackPointer), AdvIBdr.constant(typeTag(type)));
        ib.setArray(context.stackWidths(), context.frameField(context.frame.stackPointer), AdvIBdr.constant(type.stackWidth()));
        incrementStackPointer(ib, context);
    }

    protected static void pushInt(AdvIBdr ib, InterpretContext context)
    {
        pushNumber(ib, context, NumericType.INT);
    }

    protected static void pushInt(AdvIBdr ib, InterpretContext context, int local)
    {
        pushNumber(ib, context, NumericType.INT, local);
    }

    protected static void pushInt(AdvIBdr ib, InterpretContext context, Expr value)
    {
        pushNumber(ib, context, NumericType.INT, value);
    }

    protected static void pushLong(AdvIBdr ib, InterpretContext context, int local)
    {
        pushNumber(ib, context, NumericType.LONG, local);
    }

    protected static void pushLong(AdvIBdr ib, InterpretContext context, Expr value)
    {
        pushNumber(ib, context, NumericType.LONG, value);
    }

    protected static void pushFloat(AdvIBdr ib, InterpretContext context, int local)
    {
        pushNumber(ib, context, NumericType.FLOAT, local);
    }

    protected static void pushFloat(AdvIBdr ib, InterpretContext context, Expr value)
    {
        pushNumber(ib, context, NumericType.FLOAT, value);
    }

    protected static void pushDouble(AdvIBdr ib, InterpretContext context, int local)
    {
        pushNumber(ib, context, NumericType.DOUBLE, local);
    }

    protected static void pushDouble(AdvIBdr ib, InterpretContext context, Expr value)
    {
        pushNumber(ib, context, NumericType.DOUBLE, value);
    }

    protected static void jumpIfCategory2(AdvIBdr ib, Expr width, LabelNode target)
    {
        ib.jumpIf(AdvIBdr.equal(width, AdvIBdr.constant(2)), target);
    }

    protected static int typeTag(NumericType type)
    {
        return switch (type)
        {
            case INT -> 1;
            case LONG -> 2;
            case FLOAT -> 3;
            case DOUBLE -> 4;
        };
    }

    private static void popIndexAndType(AdvIBdr ib, InterpretContext context)
    {
        ib.set(context.stackIndex(), AdvIBdr.minus(context.frameField(context.frame.stackPointer), AdvIBdr.constant(1)));
        ib.set(context.frameField(context.frame.stackPointer), context.stackIndex());
        ib.set(context.stackType(), AdvIBdr.arrayAt(context.stackTypes(), context.stackIndex()));
        ib.setArray(context.stackTypes(), context.stackIndex(), AdvIBdr.constant(0));
        ib.setArray(context.stackWidths(), context.stackIndex(), AdvIBdr.constant(0));
    }

    private static void loadAndClearBoxedStackValue(AdvIBdr ib, InterpretContext context)
    {
        ib.set(context.stackObject(), AdvIBdr.arrayAt(context.stack(), context.stackIndex()));
        ib.setArray(context.stack(), context.stackIndex(), AdvIBdr.nullValue("java/lang/Object"));
    }

    private static Expr stackWord(InterpretContext context)
    {
        return AdvIBdr.arrayAt(context.stackWords(), context.stackIndex());
    }

    private static Expr wordAs(NumericType type, Expr word)
    {
        return switch (type)
        {
            case INT -> AdvIBdr.cast(word, "I");
            case LONG -> word;
            case FLOAT -> AdvIBdr.callStatic("java/lang/Float", "intBitsToFloat", "F", AdvIBdr.cast(word, "I"));
            case DOUBLE -> AdvIBdr.callStatic("java/lang/Double", "longBitsToDouble", "D", word);
        };
    }

    private static Expr wordFrom(NumericType type, Expr value)
    {
        return switch (type)
        {
            case INT -> AdvIBdr.cast(value, "J");
            case LONG -> value;
            case FLOAT -> AdvIBdr.cast(AdvIBdr.callStatic("java/lang/Float", "floatToRawIntBits", "I", value), "J");
            case DOUBLE -> AdvIBdr.callStatic("java/lang/Double", "doubleToRawLongBits", "J", value);
        };
    }

    private static void incrementStackPointer(AdvIBdr ib, InterpretContext context)
    {
        ib.set(context.frameField(context.frame.stackPointer), AdvIBdr.plus(context.frameField(context.frame.stackPointer), AdvIBdr.constant(1)));
    }
}
