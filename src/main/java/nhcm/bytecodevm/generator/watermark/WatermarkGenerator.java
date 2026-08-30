package nhcm.bytecodevm.generator.watermark;

import nhcm.bytecodevm.BuildInfo;
import nhcm.bytecodevm.enums.Acc;
import nhcm.bytecodevm.generator.GeneratedMemberNamer;
import nhcm.bytecodevm.sdk.watermark.WatermarkCapsule;
import nhcm.bytecodevm.sdk.watermark.WatermarkCodec;
import nhcm.bytecodevm.utils.ClassUtils;
import nhcm.bytecodevm.utils.FieldUtils;
import nhcm.bytecodevm.utils.InsnUtils;
import nhcm.bytecodevm.utils.MethodUtils;
import nhcm.bytecodevm.utils.RandomUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class WatermarkGenerator
{
    private WatermarkGenerator()
    {
    }

    public static WatermarkPlan generate(
            String className,
            Map<String, String> customValues,
            String inputSha256,
            GeneratedMemberNamer namer) throws IOException
    {
        Map<String, String> metadata = new TreeMap<>();
        metadata.put("bytecodevm.artifactId", UUID.randomUUID().toString());
        metadata.put("bytecodevm.inputSha256", inputSha256);
        metadata.put("bytecodevm.protectedAt", Instant.now().toString());
        metadata.put("bytecodevm.tool", "BytecodeVM");
        metadata.put("bytecodevm.version", BuildInfo.VERSION);
        if (customValues.isEmpty())
        {
            metadata.put("user.label", "BytecodeVM protected artifact");
        }
        else
        {
            customValues.forEach((key, value) -> metadata.put("user." + key, value));
        }

        byte[] watermark = WatermarkCodec.encode(metadata);
        String capsule = WatermarkCapsule.encode(watermark);
        if (capsule.length() > 60_000)
        {
            throw new IOException("Watermark is too large to embed in a class file");
        }
        byte[] digest = WatermarkCodec.sha256(watermark);
        String tokenField = namer.field(className, "watermarkToken");
        String guardMethod = namer.method(className, "watermarkGuard", "()I");
        String capsuleMethod = namer.method(className, "watermarkCapsule", "()Ljava/lang/String;");
        int expectedToken = nonZeroRandom();
        int failureToken;
        do
        {
            failureToken = nonZeroRandom();
        } while (failureToken == expectedToken);

        ClassNode runtime = ClassUtils.newClassNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, className);
        InsnUtils.addPrivateInit(runtime);
        runtime.fields.add(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                tokenField,
                "I"));
        runtime.methods.add(capsule(capsuleMethod, capsule));
        runtime.methods.add(clinit(
                className,
                tokenField,
                capsuleMethod,
                digest,
                expectedToken,
                failureToken));
        runtime.methods.add(guard(className, tokenField, guardMethod, expectedToken));
        return new WatermarkPlan(
                className,
                guardMethod,
                capsule,
                new LinkedHashMap<>(metadata),
                runtime);
    }

    private static MethodNode capsule(String methodName, String encoded)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.SYNTHETIC},
                methodName,
                "()Ljava/lang/String;");
        method.instructions.add(new LdcInsnNode(WatermarkCapsule.BYTECODE_MARKER));
        method.instructions.add(new InsnNode(Opcodes.POP2));
        method.instructions.add(new LdcInsnNode(encoded));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        return method;
    }

    private static MethodNode clinit(
            String owner,
            String tokenField,
            String capsuleMethod,
            byte[] expectedDigest,
            int expectedToken,
            int failureToken)
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.STATIC}, "<clinit>", "()V");
        InsnList instructions = method.instructions;
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        LabelNode invalid = new LabelNode();
        LabelNode complete = new LabelNode();
        instructions.add(start);

        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                owner,
                capsuleMethod,
                "()Ljava/lang/String;",
                false));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/util/Base64",
                "getDecoder",
                "()Ljava/util/Base64$Decoder;",
                false));
        instructions.add(new InsnNode(Opcodes.SWAP));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/util/Base64$Decoder",
                "decode",
                "(Ljava/lang/String;)[B",
                false));
        instructions.add(new VarInsnNode(Opcodes.ASTORE, 0));

        copyRange(instructions, 0, 0, WatermarkCapsule.KEY_LENGTH, 1);
        copyRange(instructions, 0, WatermarkCapsule.KEY_LENGTH, WatermarkCapsule.HEADER_LENGTH, 2);
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        pushInt(instructions, WatermarkCapsule.HEADER_LENGTH);
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new InsnNode(Opcodes.ARRAYLENGTH));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/util/Arrays",
                "copyOfRange",
                "([BII)[B",
                false));
        instructions.add(new VarInsnNode(Opcodes.ASTORE, 3));

        instructions.add(new TypeInsnNode(Opcodes.NEW, "javax/crypto/spec/SecretKeySpec"));
        instructions.add(new InsnNode(Opcodes.DUP));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new LdcInsnNode("AES"));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                "javax/crypto/spec/SecretKeySpec",
                "<init>",
                "([BLjava/lang/String;)V",
                false));
        instructions.add(new VarInsnNode(Opcodes.ASTORE, 4));
        instructions.add(new TypeInsnNode(Opcodes.NEW, "javax/crypto/spec/GCMParameterSpec"));
        instructions.add(new InsnNode(Opcodes.DUP));
        pushInt(instructions, 128);
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                "javax/crypto/spec/GCMParameterSpec",
                "<init>",
                "(I[B)V",
                false));
        instructions.add(new VarInsnNode(Opcodes.ASTORE, 5));
        instructions.add(new LdcInsnNode("AES/GCM/NoPadding"));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "javax/crypto/Cipher",
                "getInstance",
                "(Ljava/lang/String;)Ljavax/crypto/Cipher;",
                false));
        instructions.add(new VarInsnNode(Opcodes.ASTORE, 6));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 6));
        pushInt(instructions, 2);
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 5));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "javax/crypto/Cipher",
                "init",
                "(ILjava/security/Key;Ljava/security/spec/AlgorithmParameterSpec;)V",
                false));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 6));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "javax/crypto/Cipher",
                "doFinal",
                "([B)[B",
                false));
        instructions.add(new VarInsnNode(Opcodes.ASTORE, 7));

        instructions.add(new LdcInsnNode("SHA-256"));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/security/MessageDigest",
                "getInstance",
                "(Ljava/lang/String;)Ljava/security/MessageDigest;",
                false));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 7));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/security/MessageDigest",
                "digest",
                "([B)[B",
                false));
        emitByteArray(instructions, expectedDigest);
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/security/MessageDigest",
                "isEqual",
                "([B[B)Z",
                false));
        instructions.add(new JumpInsnNode(Opcodes.IFEQ, invalid));
        setToken(instructions, owner, tokenField, expectedToken);
        instructions.add(new JumpInsnNode(Opcodes.GOTO, complete));
        instructions.add(invalid);
        setToken(instructions, owner, tokenField, failureToken);
        instructions.add(complete);
        instructions.add(end);
        instructions.add(new InsnNode(Opcodes.RETURN));
        instructions.add(handler);
        instructions.add(new InsnNode(Opcodes.POP));
        setToken(instructions, owner, tokenField, failureToken);
        instructions.add(new InsnNode(Opcodes.RETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, "java/lang/Throwable"));
        return method;
    }

    private static MethodNode guard(
            String owner,
            String tokenField,
            String methodName,
            int expectedToken)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC, Acc.STATIC},
                methodName,
                "()I");
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, owner, tokenField, "I"));
        method.instructions.add(new LdcInsnNode(expectedToken));
        method.instructions.add(new InsnNode(Opcodes.IXOR));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        LabelNode valid = new LabelNode();
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, valid));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, "java/lang/SecurityException"));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new LdcInsnNode("VM state authentication failed"));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                "java/lang/SecurityException",
                "<init>",
                "(Ljava/lang/String;)V",
                false));
        method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.instructions.add(valid);
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        return method;
    }

    private static void copyRange(
            InsnList instructions,
            int sourceLocal,
            int from,
            int to,
            int targetLocal)
    {
        instructions.add(new VarInsnNode(Opcodes.ALOAD, sourceLocal));
        pushInt(instructions, from);
        pushInt(instructions, to);
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/util/Arrays",
                "copyOfRange",
                "([BII)[B",
                false));
        instructions.add(new VarInsnNode(Opcodes.ASTORE, targetLocal));
    }

    private static void setToken(
            InsnList instructions,
            String owner,
            String tokenField,
            int value)
    {
        instructions.add(new LdcInsnNode(value));
        instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, owner, tokenField, "I"));
    }

    private static void emitByteArray(InsnList instructions, byte[] bytes)
    {
        pushInt(instructions, bytes.length);
        instructions.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_BYTE));
        for (int index = 0; index < bytes.length; index++)
        {
            instructions.add(new InsnNode(Opcodes.DUP));
            pushInt(instructions, index);
            pushInt(instructions, bytes[index]);
            instructions.add(new InsnNode(Opcodes.BASTORE));
        }
    }

    private static void pushInt(InsnList instructions, int value)
    {
        instructions.add(new LdcInsnNode(value));
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
