package nhcm.bytecodevm.Generator.GlobalClass;

import lombok.Getter;
import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Local;
import nhcm.bytecodevm.Enums.Acc;
import nhcm.bytecodevm.Generator.Abstract.ClassObj;
import nhcm.bytecodevm.Generator.GeneratedMemberNamer;
import nhcm.bytecodevm.Utils.ClassUtils;
import nhcm.bytecodevm.Utils.FieldUtils;
import nhcm.bytecodevm.Utils.MethodUtils;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class VMProgramGenerator extends ClassObj
{
    @Getter
    public final ClassNode classNode;
    @Getter
    public final VMProgramLayout layout;

    public VMProgramGenerator(String className)
    {
        this(className, GeneratedMemberNamer.DISABLED);
    }

    public VMProgramGenerator(String className, GeneratedMemberNamer namer)
    {
        super(className);
        this.layout = new VMProgramLayout(className, namer);
        ClassNode cn = ClassUtils.newClassNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, className);
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.opcodeStreamField.name(), layout.opcodeStreamField.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.operandStreamField.name(), layout.operandStreamField.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.layoutStreamField.name(), layout.layoutStreamField.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.constantsField.name(), layout.constantsField.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.exceptionHandlersField.name(), layout.exceptionHandlersField.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.opcodeMapField.name(), layout.opcodeMapField.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.methodKeyField.name(), layout.methodKeyField.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.maxLocalsField.name(), layout.maxLocalsField.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.maxStackField.name(), layout.maxStackField.descriptor()));
        cn.methods.add(genConstructor());
        cn.methods.add(genObjectGetter(layout.opcodeStream, layout.opcodeStreamField));
        cn.methods.add(genObjectGetter(layout.operandStream, layout.operandStreamField));
        cn.methods.add(genObjectGetter(layout.layoutStream, layout.layoutStreamField));
        cn.methods.add(genObjectGetter(layout.constants, layout.constantsField));
        cn.methods.add(genObjectGetter(layout.exceptionHandlers, layout.exceptionHandlersField));
        cn.methods.add(genObjectGetter(layout.opcodeMap, layout.opcodeMapField));
        cn.methods.add(genIntGetter(layout.methodKey, layout.methodKeyField));
        cn.methods.add(genIntGetter(layout.maxLocals, layout.maxLocalsField));
        cn.methods.add(genIntGetter(layout.maxStack, layout.maxStackField));
        this.classNode = cn;
    }

    private MethodNode genConstructor()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                layout.init.name(), // <init>
                layout.init.descriptor()
        );

        AdvInsnBuilder ib = new AdvInsnBuilder(method);

        Local opcodeStream = ib.getLocal("opcodeStream", "[I", 1);
        Local operandStream = ib.getLocal("operandStream", "[I", 2);
        Local layoutStream = ib.getLocal("layoutStream", "[I", 3);
        Local constants = ib.getLocal("constants", "[Ljava/lang/Object;", 4);
        Local exceptionHandlers = ib.getLocal("exceptionHandlers", "[I", 5);
        Local opcodeMap = ib.getLocal("opcodeMap", "[I", 6);
        Local methodKey = ib.getLocal("methodKey", "I", 7);
        Local maxLocals = ib.getLocal("maxLocals", "I", 8);
        Local maxStack = ib.getLocal("maxStack", "I", 9);

        ib.callNoArgSuperConstructor("java/lang/Object");
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.opcodeStreamField), opcodeStream);
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.operandStreamField), operandStream);
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.layoutStreamField), layoutStream);
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.constantsField), constants);
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.exceptionHandlersField), exceptionHandlers);
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.opcodeMapField), opcodeMap);
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.methodKeyField), methodKey);
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.maxLocalsField), maxLocals);
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.maxStackField), maxStack);
        ib.returnVoid();
        return method;
    }

    private MethodNode genObjectGetter(nhcm.bytecodevm.Utils.Builder.MethodRef getter, nhcm.bytecodevm.Utils.Builder.FieldRef field)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                getter.name(),
                getter.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        ib.returnValue(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), field));
        return method;
    }

    private MethodNode genIntGetter(nhcm.bytecodevm.Utils.Builder.MethodRef getter, nhcm.bytecodevm.Utils.Builder.FieldRef field)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                getter.name(),
                getter.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        ib.returnValue(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), field));
        return method;
    }
}
