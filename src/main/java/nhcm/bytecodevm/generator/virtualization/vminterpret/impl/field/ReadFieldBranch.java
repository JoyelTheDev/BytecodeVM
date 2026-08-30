package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.field;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.advInsn.Condition;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;

import java.util.Set;

public class ReadFieldBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.READ_FIELD.getOpcodes();
    }

    @Override
    public void generate(AdvIBdr ib, InterpretContext context, Opcs opcode)
    {
        Local owner = context.local("fieldOwner", "java/lang/String", InterpretContext.FIELD_OWNER);
        Local name = context.local("fieldName", "java/lang/String", InterpretContext.FIELD_NAME);
        Local descriptor = context.local("fieldDescriptor", "java/lang/String", InterpretContext.FIELD_DESCRIPTOR);
        Local receiver = context.objectLocal("fieldReceiver", InterpretContext.FIELD_RECEIVER);
        Local result = context.objectLocal("fieldResult", InterpretContext.FIELD_RESULT);

        ib.set(owner, readConstantString(ib, context));
        ib.set(name, readConstantString(ib, context));
        ib.set(descriptor, readConstantString(ib, context));

        if (opcode == Opcs.GETSTATIC)
        {
            ib.set(receiver, AdvIBdr.nullValue("java/lang/Object"));
        }
        else
        {
            popObject(ib, context, receiver);
        }

        ib.set(result, AdvIBdr.callStatic(
                context.vm.owner,
                context.vm.getField.name(),
                "java/lang/Object",
                owner,
                name,
                descriptor,
                AdvIBdr.constant(opcode == Opcs.GETSTATIC),
                receiver));

        ib.ifElse(
                isCategory2Descriptor(descriptor),
                b -> pushObjectWithWidth(b, context, result, AdvIBdr.constant(2)),
                b -> pushObject(b, context, result));
    }

    private static Expr readConstantString(AdvIBdr ib, InterpretContext context)
    {
        Local token = context.intLocal("fieldToken", InterpretContext.JUMP_TARGET);
        context.nextOperand(ib, token);
        return context.constantString(token);
    }

    private static Condition isCategory2Descriptor(Local descriptor)
    {
        Expr firstChar = AdvIBdr.callVirtual(
                descriptor,
                "java/lang/String",
                "charAt",
                "C",
                AdvIBdr.constant(0));
        return AdvIBdr.or(
                AdvIBdr.equal(firstChar, AdvIBdr.constant('J')),
                AdvIBdr.equal(firstChar, AdvIBdr.constant('D')));
    }
}
