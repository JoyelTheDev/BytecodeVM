package nhcm.bytecodevm.generator.integrity;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.advInsn.Expr;
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
import java.util.ArrayList;
import java.util.Objects;

public class VMIntegrityGenerator
{
    private static final int FNV_OFFSET = 0x811C9DC5;
    private static final int FNV_PRIME = 0x01000193;
    private static final int DERIVE_CHUNK_TARGETS = 32;

    private final ClassNode classNode;
    private final MethodNode deriveMethod;
    private final List<MethodNode> derivationMethods;
    private final VMIntegrityPlan plan;
    private final String hashMethodName;
    private final String stateFieldName;
    private final VMIntegrityPlan.CacheLayout cacheLayout;
    private final String probeMethodName;
    private final String probeBudgetFieldName;
    private final String probeCursorFieldName;
    private final int recheckInterval;
    private final int failMix;

    public VMIntegrityGenerator(
            String className,
            List<HashTarget> targets,
            int expectedCapability,
            double ratio,
            int recheckInterval,
            GeneratedMemberNamer namer)
    {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(namer, "namer");
        if (expectedCapability == 0)
        {
            throw new IllegalArgumentException("expectedCapability must be non-zero");
        }

        this.hashMethodName = namer.method(className, "hashResource", "(Ljava/lang/String;I)I");
        this.stateFieldName = namer.field(className, "STATE_WORD");
        this.cacheLayout = IntegrityCacheCodec.randomLayout();
        this.recheckInterval = targets.isEmpty() ? 0 : recheckInterval;
        this.probeMethodName = this.recheckInterval == 0
                ? null
                : namer.method(className, "pulseState", "()V");
        this.probeBudgetFieldName = this.recheckInterval == 0
                ? null
                : namer.field(className, "STATE_TICKET");
        this.probeCursorFieldName = this.recheckInterval == 0
                ? null
                : namer.field(className, "STATE_LANE");
        String deriveName = namer.method(className, "deriveIntegrityKey", "()I");
        String gatewayName = namer.method(className, "resolveState", "()I");
        String initializeName = namer.method(className, "initializeState", "()I");
        this.failMix = nonZeroRandom();
        this.classNode = ClassUtils.newClassNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, className);
        InsnUtils.addPrivateInit(classNode);
        classNode.fields.add(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.VOLATILE},
                stateFieldName,
                "J"));
        if (this.recheckInterval != 0)
        {
            classNode.fields.add(FieldUtils.newFieldNode(
                    new Acc[]{Acc.PRIVATE, Acc.STATIC},
                    probeBudgetFieldName,
                    "I"));
            classNode.fields.add(FieldUtils.newFieldNode(
                    new Acc[]{Acc.PRIVATE, Acc.STATIC},
                    probeCursorFieldName,
                    "I"));
        }
        classNode.methods.add(genClinitMethod(className));
        List<MethodNode> chunks = genDeriveChunks(className, deriveName, targets, namer);
        this.deriveMethod = genDeriveMethod(className, deriveName, chunks, expectedCapability);
        this.derivationMethods = new ArrayList<>(chunks.size() + 1);
        this.derivationMethods.add(deriveMethod);
        this.derivationMethods.addAll(chunks);
        classNode.methods.add(deriveMethod);
        classNode.methods.addAll(chunks);
        classNode.methods.add(genGatewayMethod(className, gatewayName, initializeName));
        classNode.methods.add(genInitializeMethod(className, initializeName, deriveName));
        if (this.recheckInterval != 0)
        {
            classNode.methods.add(genRuntimeProbeMethod(className, chunks));
        }
        classNode.methods.add(genHashMethod(className));
        this.plan = new VMIntegrityPlan(
                className,
                gatewayName,
                "()I",
                stateFieldName,
                cacheLayout,
                probeMethodName,
                expectedCapability,
                ratio);
    }

    public ClassNode classNode()
    {
        return classNode;
    }

    public MethodNode deriveMethod()
    {
        return deriveMethod;
    }

    public List<MethodNode> derivationMethods()
    {
        return List.copyOf(derivationMethods);
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

    private MethodNode genDeriveMethod(
            String owner,
            String name,
            List<MethodNode> chunks,
            int expectedCapability)
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PUBLIC, Acc.STATIC}, name, "()I");
        AdvIBdr ib = new AdvIBdr(method);
        Local acc = ib.var("acc", "I");
        Local result = ib.var("result", "I");
        ib.set(acc, AdvIBdr.constant(0));
        for (MethodNode chunk : chunks)
        {
            ib.set(acc, AdvIBdr.callStatic(owner, chunk.name, "I", acc));
        }
        ib.ifElse(
                AdvIBdr.equal(acc, AdvIBdr.constant(0)),
                ok -> ok.set(result, AdvIBdr.constant(expectedCapability)),
                fail -> fail.set(result, AdvIBdr.bitXor(
                        AdvIBdr.constant(expectedCapability),
                        AdvIBdr.bitOr(
                                AdvIBdr.bitXor(
                                        AdvIBdr.multiply(acc, AdvIBdr.constant(failMix)),
                                        AdvIBdr.unsignedShiftRight(acc, AdvIBdr.constant(13))),
                                AdvIBdr.constant(1)))));
        ib.returnValue(result);
        return method;
    }

    private MethodNode genGatewayMethod(String owner, String name, String initializeName)
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PUBLIC, Acc.STATIC}, name, "()I");
        AdvIBdr ib = new AdvIBdr(method);
        Local envelope = ib.var("stateEnvelope", "J");
        ib.set(envelope, AdvIBdr.staticField(owner, stateFieldName, "J"));
        ib.ifCondition(
                AdvIBdr.notEqual(envelope, AdvIBdr.constant(0L)),
                hit -> hit.returnValue(IntegrityCacheCodec.emitDecode(
                        hit,
                        envelope,
                        cacheLayout,
                        "gateway")));
        ib.returnValue(AdvIBdr.callStatic(owner, initializeName, "I"));
        return method;
    }

    private MethodNode genInitializeMethod(String owner, String name, String deriveName)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.SYNCHRONIZED},
                name,
                "()I");
        AdvIBdr ib = new AdvIBdr(method);
        Local envelope = ib.var("stateEnvelope", "J");
        ib.set(envelope, AdvIBdr.staticField(owner, stateFieldName, "J"));
        ib.ifCondition(
                AdvIBdr.notEqual(envelope, AdvIBdr.constant(0L)),
                hit -> hit.returnValue(IntegrityCacheCodec.emitDecode(
                        hit,
                        envelope,
                        cacheLayout,
                        "locked")));
        Local result = ib.var("integrityKey", "I");
        ib.set(result, AdvIBdr.callStatic(owner, deriveName, "I"));
        Local encodedEnvelope = IntegrityCacheCodec.emitEncode(
                ib,
                result,
                cacheLayout,
                "published");
        ib.set(AdvIBdr.staticField(owner, stateFieldName, "J"), encodedEnvelope);
        ib.returnValue(result);
        return method;
    }

    private List<MethodNode> genDeriveChunks(
            String owner,
            String deriveName,
            List<HashTarget> targets,
            GeneratedMemberNamer namer)
    {
        List<MethodNode> chunks = new ArrayList<>();
        for (int from = 0, index = 0; from < targets.size(); from += DERIVE_CHUNK_TARGETS, index++)
        {
            int to = Math.min(targets.size(), from + DERIVE_CHUNK_TARGETS);
            String descriptor = "(I)I";
            String name = namer.method(owner, "deriveIntegrityChunk$" + index, descriptor);
            MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, name, descriptor);
            AdvIBdr ib = new AdvIBdr(method);
            Local acc = ib.getLocal("acc", "I", 0);
            for (int targetIndex = from; targetIndex < to; targetIndex++)
            {
                HashTarget target = targets.get(targetIndex);
                ib.set(acc, AdvIBdr.bitOr(
                        acc,
                        AdvIBdr.bitXor(
                                AdvIBdr.callStatic(
                                        owner,
                                        hashMethodName,
                                        "I",
                                        AdvIBdr.constant(target.resourceName()),
                                        AdvIBdr.constant(target.seed())),
                                AdvIBdr.constant(target.expectedHash()))));
            }
            ib.returnValue(acc);
            chunks.add(method);
        }
        if (chunks.isEmpty())
        {
            String descriptor = "(I)I";
            String name = namer.method(owner, "deriveIntegrityChunk$0", descriptor);
            MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, name, descriptor);
            AdvIBdr ib = new AdvIBdr(method);
            Local acc = ib.getLocal("acc", "I", 0);
            ib.returnValue(acc);
            chunks.add(method);
        }
        return List.copyOf(chunks);
    }

    private MethodNode genRuntimeProbeMethod(String owner, List<MethodNode> chunks)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                probeMethodName,
                "()V");
        AdvIBdr ib = new AdvIBdr(method);
        Local budget = ib.var("stateTicket", "I");
        ib.set(budget, AdvIBdr.minus(
                AdvIBdr.staticField(owner, probeBudgetFieldName, "I"),
                AdvIBdr.constant(1)));
        ib.set(AdvIBdr.staticField(owner, probeBudgetFieldName, "I"), budget);
        ib.ifCondition(
                AdvIBdr.greaterThan(budget, AdvIBdr.constant(0)),
                hit -> hit.returnVoid());

        Local cursor = ib.var("stateLane", "I");
        Local nextCursor = ib.var("nextLane", "I");
        Local mismatch = ib.var("stateMismatch", "I");
        ib.set(cursor, AdvIBdr.staticField(owner, probeCursorFieldName, "I"));
        ib.set(nextCursor, AdvIBdr.plus(cursor, AdvIBdr.constant(1)));
        ib.ifCondition(
                AdvIBdr.not(AdvIBdr.lessThan(
                        nextCursor,
                        AdvIBdr.constant(chunks.size()))),
                wrap -> wrap.set(nextCursor, AdvIBdr.constant(0)));
        ib.set(AdvIBdr.staticField(owner, probeCursorFieldName, "I"), nextCursor);

        int jitterRange = Integer.highestOneBit(Math.max(1, recheckInterval / 4));
        int jitterMask = jitterRange - 1;
        int jitterMultiplier = nonZeroRandom() | 1;
        int jitterSalt = nonZeroRandom();
        Expr jitter = AdvIBdr.bitAnd(
                AdvIBdr.bitXor(
                        AdvIBdr.multiply(cursor, AdvIBdr.constant(jitterMultiplier)),
                        AdvIBdr.constant(jitterSalt)),
                AdvIBdr.constant(jitterMask));
        ib.set(
                AdvIBdr.staticField(owner, probeBudgetFieldName, "I"),
                AdvIBdr.minus(AdvIBdr.constant(recheckInterval), jitter));

        ib.set(mismatch, AdvIBdr.constant(0));
        for (int index = 0; index < chunks.size(); index++)
        {
            MethodNode chunk = chunks.get(index);
            ib.ifCondition(
                    AdvIBdr.equal(cursor, AdvIBdr.constant(index)),
                    selected -> selected.set(mismatch, AdvIBdr.callStatic(
                            owner,
                            chunk.name,
                            "I",
                            AdvIBdr.constant(0))));
        }

        int poisonMultiplier = nonZeroRandom() | 1;
        int poisonSalt = nonZeroRandom();
        ib.ifCondition(
                AdvIBdr.notEqual(mismatch, AdvIBdr.constant(0)),
                failed -> {
                    Local envelope = failed.var("failedEnvelope", "J");
                    failed.set(envelope, AdvIBdr.staticField(owner, stateFieldName, "J"));
                    Expr poison = AdvIBdr.bitOr(
                            AdvIBdr.shiftLeft(
                                    AdvIBdr.cast(mismatch, "J"),
                                    AdvIBdr.constant(32)),
                            AdvIBdr.bitAnd(
                                    AdvIBdr.cast(
                                            AdvIBdr.bitXor(
                                                    AdvIBdr.multiply(
                                                            mismatch,
                                                            AdvIBdr.constant(poisonMultiplier)),
                                                    AdvIBdr.constant(poisonSalt)),
                                            "J"),
                                    AdvIBdr.constant(0xFFFF_FFFFL)));
                    failed.set(
                            AdvIBdr.staticField(owner, stateFieldName, "J"),
                            AdvIBdr.bitXor(envelope, poison));
                });
        ib.returnVoid();
        return method;
    }

    private MethodNode genClinitMethod(String owner)
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.STATIC}, "<clinit>", "()V");
        AdvIBdr ib = new AdvIBdr(method);
        ib.set(AdvIBdr.staticField(owner, stateFieldName, "J"), AdvIBdr.constant(0L));
        if (recheckInterval != 0)
        {
            ib.set(
                    AdvIBdr.staticField(owner, probeBudgetFieldName, "I"),
                    AdvIBdr.constant(Math.max(1, recheckInterval / 2)));
            ib.set(
                    AdvIBdr.staticField(owner, probeCursorFieldName, "I"),
                    AdvIBdr.constant(0));
        }
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
