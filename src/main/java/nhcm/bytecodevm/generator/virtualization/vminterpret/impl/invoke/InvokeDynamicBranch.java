package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.invoke;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.advInsn.Condition;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;

import java.util.Set;

public class InvokeDynamicBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.INVOKE_DYNAMIC.getOpcodes();
    }

    @Override
    public void generate(AdvIBdr ib, InterpretContext context, Opcs opcode)
    {
        Local nameToken = context.intLocal("dynNameToken", InterpretContext.SWITCH_KEY);
        Local descToken = context.intLocal("dynDescToken", InterpretContext.SWITCH_MIN);
        Local bsmToken = context.intLocal("dynBsmToken", InterpretContext.SWITCH_COUNT);
        Local bsmArgCountToken = context.intLocal("dynBsmArgCount", InterpretContext.SWITCH_INDEX);
        Local bsmArgToken = context.intLocal("dynBsmArgToken", InterpretContext.SWITCH_CANDIDATE);

        Local name = context.local("dynName", "java/lang/String", InterpretContext.INVOKE_NAME);
        Local descriptor = context.local("dynDescriptor", "java/lang/String", InterpretContext.INVOKE_OWNER);
        Local bootstrapHandle = context.local("dynBootstrap", "java/lang/invoke/MethodHandle", InterpretContext.INVOKE_TYPE);
        Local bootstrapArgCount = context.intLocal("dynBsmArgIndex", InterpretContext.INVOKE_INDEX);
        Local bootstrapArgs = context.local("dynBsmArgs", "[Ljava/lang/Object;", InterpretContext.INVOKE_ARGUMENTS);
        Local bootstrapLoopIndex = context.intLocal("dynBsmLoopIdx", InterpretContext.INVOKE_RECEIVER);
        Local callType = context.local("dynCallType", "java/lang/invoke/MethodType", InterpretContext.INVOKE_RETURN_TYPE);
        Local callArguments = context.local("dynCallArgs", "[Ljava/lang/Object;", InterpretContext.MIDDLE_VALUE);
        Local callArgIndex = context.intLocal("dynCallArgIdx", InterpretContext.JUMP_TARGET);
        Local result = context.objectLocal("dynResult", InterpretContext.INVOKE_RESULT);
        Local returnType = context.local("dynReturnType", "java/lang/Class", InterpretContext.FIELD_RESULT);

        context.nextOperand(ib, nameToken);
        ib.set(name, context.constantString(nameToken));

        context.nextOperand(ib, descToken);
        ib.set(descriptor, context.constantString(descToken));

        context.nextOperand(ib, bsmToken);
        ib.set(bootstrapHandle, AdvIBdr.cast(
                resolveConstant(context, bsmToken),
                "java/lang/invoke/MethodHandle"));

        context.nextOperand(ib, bsmArgCountToken);
        ib.set(bootstrapArgCount, bsmArgCountToken);

        ib.set(bootstrapArgs, AdvIBdr.newArray("java/lang/Object", bootstrapArgCount));
        ib.set(bootstrapLoopIndex, AdvIBdr.constant(0));
        ib.whileLoop(
                AdvIBdr.lessThan(bootstrapLoopIndex, bootstrapArgCount),
                b -> {
                    context.nextOperand(b, bsmArgToken);
                    b.setArray(bootstrapArgs, bootstrapLoopIndex, resolveConstant(context, bsmArgToken));
                    b.increment(bootstrapLoopIndex, 1);
                });

        ib.set(callType, AdvIBdr.callStatic(
                context.vm.owner,
                context.vm.methodType.name(),
                "java/lang/invoke/MethodType",
                descriptor));

        ib.set(callArguments, AdvIBdr.newArray(
                "java/lang/Object",
                AdvIBdr.callVirtual(callType, "java/lang/invoke/MethodType", "parameterCount", "I")));

        ib.set(callArgIndex, AdvIBdr.minus(AdvIBdr.arrayLength(callArguments), AdvIBdr.constant(1)));
        ib.whileLoop(
                AdvIBdr.greaterOrEqual(callArgIndex, AdvIBdr.constant(0)),
                b -> {
                    popObject(b, context);
                    b.setArray(callArguments, callArgIndex, context.stackObject());
                    b.increment(callArgIndex, -1);
                });

        ib.set(result, AdvIBdr.callStatic(
                context.vm.owner,
                context.vm.invokeDynamic.name(),
                "java/lang/Object",
                name,
                descriptor,
                bootstrapHandle,
                bootstrapArgs,
                callArguments));

        ib.set(returnType, AdvIBdr.callVirtual(
                callType,
                "java/lang/invoke/MethodType",
                "returnType",
                "java/lang/Class"));

        ib.ifCondition(
                AdvIBdr.not(AdvIBdr.equal(returnType, AdvIBdr.staticField("java/lang/Void", "TYPE", "java/lang/Class"))),
                b -> b.ifElse(
                        isCategory2Return(returnType),
                        category2 -> pushObjectWithWidth(category2, context, result, AdvIBdr.constant(2)),
                        category1 -> pushObject(category1, context, result)));
    }

    private static Expr resolveConstant(InterpretContext context, Local token)
    {
        return AdvIBdr.callStatic(
                context.vm.owner,
                context.vm.resolveConstant.name(),
                "java/lang/Object",
                context.program(),
                AdvIBdr.arrayAt(context.constants(), token),
                context.frame(),
                context.instructionIndex(),
                context.opcode());
    }

    private static Condition isCategory2Return(Local returnType)
    {
        return AdvIBdr.or(
                AdvIBdr.equal(returnType, AdvIBdr.staticField("java/lang/Long", "TYPE", "java/lang/Class")),
                AdvIBdr.equal(returnType, AdvIBdr.staticField("java/lang/Double", "TYPE", "java/lang/Class")));
    }
}
