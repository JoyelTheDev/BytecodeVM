package nhcm.bytecodevm.generator.integrity;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.data.VMIntegrityPlan;
import nhcm.bytecodevm.enums.Acc;
import nhcm.bytecodevm.generator.GeneratedMemberNamer;
import nhcm.bytecodevm.utils.ClassUtils;
import nhcm.bytecodevm.utils.FieldUtils;
import nhcm.bytecodevm.utils.InsnUtils;
import nhcm.bytecodevm.utils.MethodUtils;
import nhcm.bytecodevm.utils.RandomUtils;
import nhcm.bytecodevm.utils.builder.InsnBuilder;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import java.util.List;
import java.util.Objects;

public class VMIntegrityGenerator
{
    private static final int FNV_OFFSET = 0x811C9DC5;
    private static final int FNV_PRIME = 0x01000193;

    private final ClassNode classNode;
    private final MethodNode deriveMethod;
    private final VMIntegrityPlan plan;
    private final String hashMethodName;
    private final String cacheFieldName;
    private final int cacheEmpty;
    private final int failMix;

    public VMIntegrityGenerator(
            String className,
            List<HashTarget> targets,
            double ratio,
            GeneratedMemberNamer namer)
    {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(namer, "namer");

        this.hashMethodName = namer.method(className, "hashResource", "(Ljava/lang/String;I)I");
        this.cacheFieldName = namer.field(className, "INTEGRITY_CACHE");
        this.cacheEmpty = nonZeroRandom();
        String deriveName = namer.method(className, "deriveIntegrityKey", "()I");
        this.failMix = nonZeroRandom();
        this.classNode = ClassUtils.newClassNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, className);
        InsnUtils.addPrivateInit(classNode);
        classNode.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, cacheFieldName, "I"));
        classNode.methods.add(genClinitMethod(className));
        this.deriveMethod = genDeriveMethod(className, deriveName, targets);
        classNode.methods.add(deriveMethod);
        classNode.methods.add(genHashMethod(className));
        this.plan = new VMIntegrityPlan(className, deriveName, "()I", ratio);
    }

    public ClassNode classNode()
    {
        return classNode;
    }

    public MethodNode deriveMethod()
    {
        return deriveMethod;
    }

    public VMIntegrityPlan plan()
    {
        return plan;
    }

    public static int hashBytes(byte[] bytes, int seed)
    {
        int acc = seed ^ FNV_OFFSET;
        for (byte value : bytes)
        {
            acc = (acc ^ (value & 0xFF)) * FNV_PRIME;
        }
        return acc;
    }

    private MethodNode genDeriveMethod(String owner, String name, List<HashTarget> targets)
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PUBLIC, Acc.STATIC}, name, "()I");
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local cached = ib.var("cached", "I");
        ib.set(cached, AdvInsnBuilder.staticField(owner, cacheFieldName, "I"));
        ib.ifCondition(
                AdvInsnBuilder.notEqual(cached, AdvInsnBuilder.constant(cacheEmpty)),
                hit -> hit.returnValue(cached));

        Local acc = ib.var("acc", "I");
        Local result = ib.var("result", "I");
        ib.set(acc, AdvInsnBuilder.constant(0));
        for (HashTarget target : targets)
        {
            ib.set(acc, AdvInsnBuilder.bitOr(
                    acc,
                    AdvInsnBuilder.bitXor(
                            AdvInsnBuilder.callStatic(
                                    owner,
                                    hashMethodName,
                                    "I",
                                    AdvInsnBuilder.constant(target.resourceName()),
                                    AdvInsnBuilder.constant(target.seed())),
                            AdvInsnBuilder.constant(target.expectedHash()))));
        }
        ib.ifElse(
                AdvInsnBuilder.equal(acc, AdvInsnBuilder.constant(0)),
                ok -> ok.set(result, AdvInsnBuilder.constant(0)),
                fail -> fail.set(result, AdvInsnBuilder.bitXor(
                        AdvInsnBuilder.multiply(acc, AdvInsnBuilder.constant(failMix)),
                        AdvInsnBuilder.unsignedShiftRight(acc, AdvInsnBuilder.constant(13)))));
        ib.set(AdvInsnBuilder.staticField(owner, cacheFieldName, "I"), result);
        ib.returnValue(result);
        return method;
    }

    private MethodNode genClinitMethod(String owner)
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.STATIC}, "<clinit>", "()V");
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        ib.set(AdvInsnBuilder.staticField(owner, cacheFieldName, "I"), AdvInsnBuilder.constant(cacheEmpty));
        ib.returnVoid();
        return method;
    }

    private MethodNode genHashMethod(String owner)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                hashMethodName,
                "(Ljava/lang/String;I)I");
        method.tryCatchBlocks.add(new TryCatchBlockNode(
                new LabelNode(),
                new LabelNode(),
                new LabelNode(),
                "java/lang/Throwable"));

        LabelNode tryStart = method.tryCatchBlocks.getFirst().start;
        LabelNode tryEnd = method.tryCatchBlocks.getFirst().end;
        LabelNode handler = method.tryCatchBlocks.getFirst().handler;
        LabelNode missing = new LabelNode();
        LabelNode readLoop = new LabelNode();
        LabelNode readDone = new LabelNode();
        LabelNode byteLoop = new LabelNode();
        LabelNode byteDone = new LabelNode();
        LabelNode done = new LabelNode();

        InsnBuilder ib = new InsnBuilder(method.instructions);
        ib.label(tryStart);

        ib.pushClass(Type.getObjectType(owner));
        ib.invokeVirtual("java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;");
        ib.aload(0);
        ib.invokeVirtual("java/lang/ClassLoader", "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;");
        ib.astore(2);
        ib.aload(2);
        ib.ifNull(missing);

        ib.pushInt(1024);
        ib.newArray(org.objectweb.asm.Opcodes.T_BYTE);
        ib.astore(3);

        ib.iload(1);
        ib.pushInt(FNV_OFFSET);
        ib.ixor();
        ib.istore(4);

        ib.label(readLoop);
        ib.aload(2);
        ib.aload(3);
        ib.invokeVirtual("java/io/InputStream", "read", "([B)I");
        ib.istore(5);
        ib.iload(5);
        ib.pushInt(-1);
        ib.ifIcmpEq(readDone);

        ib.pushInt(0);
        ib.istore(6);
        ib.label(byteLoop);
        ib.iload(6);
        ib.iload(5);
        ib.ifIcmpGe(byteDone);
        ib.iload(4);
        ib.aload(3);
        ib.iload(6);
        ib.baload();
        ib.pushInt(255);
        ib.iand();
        ib.ixor();
        ib.pushInt(FNV_PRIME);
        ib.imul();
        ib.istore(4);
        ib.iinc(6, 1);
        ib.goto_(byteLoop);

        ib.label(byteDone);
        ib.goto_(readLoop);

        ib.label(readDone);
        ib.aload(2);
        ib.invokeVirtual("java/io/InputStream", "close", "()V");
        ib.iload(4);
        ib.istore(7);
        ib.goto_(done);

        ib.label(missing);
        ib.iload(1);
        ib.pushInt(nonZeroRandom());
        ib.ixor();
        ib.istore(7);
        ib.goto_(done);

        ib.label(done);
        ib.label(tryEnd);
        ib.iload(7);
        ib.ireturn();

        ib.label(handler);
        ib.pop();
        ib.iload(1);
        ib.pushInt(nonZeroRandom());
        ib.ixor();
        ib.ireturn();
        return method;
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

    public record HashTarget(String resourceName, int seed, int expectedHash)
    {
    }
}
