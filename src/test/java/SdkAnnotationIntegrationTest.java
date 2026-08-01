import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.config.sdk.SdkAnnotationReader;
import nhcm.bytecodevm.config.sdk.SdkAnnotationRemover;
import nhcm.bytecodevm.enums.VMStructure;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Integration contract for the dependency-free SDK annotation reader. */
public final class SdkAnnotationIntegrationTest
{
    private static final String TOGGLE = "Lnhcm/bytecodevm/sdk/enums/Toggle;";
    private static final String CALL_POLICY = "Lnhcm/bytecodevm/sdk/enums/CallPolicy;";
    private static final String SDK_VM_STRUCTURE = "Lnhcm/bytecodevm/sdk/enums/VMStructure;";
    private static final String VM_OPTIONS =
            "Lnhcm/bytecodevm/sdk/annotation/config/VMOptions;";

    private SdkAnnotationIntegrationTest()
    {
    }

    public static void main(String[] args)
    {
        verifyAutomaticStructures();

        ClassNode owner = fixtureClass();
        MethodNode protectedMethod = owner.methods.get(0);
        MethodNode excludedMethod = owner.methods.get(1);

        SdkAnnotationReader.ClassDirectives classDirectives =
                SdkAnnotationReader.classDirectives(owner);
        require(classDirectives.protectClass(), "@ProtectClass was not detected");
        require(Boolean.TRUE.equals(classDirectives.constantFix()),
                "@ProtectClass.constantFix was not decoded");

        SdkAnnotationReader.MethodDirectives protectedDirectives =
                SdkAnnotationReader.methodDirectives(owner, protectedMethod);
        require(protectedDirectives.selected(), "@Virtualize did not select its method");
        require(!protectedDirectives.excluded(), "@Virtualize method was unexpectedly excluded");

        BytecodeVMConfig methodConfig = baseConfig().forMethod(owner, protectedMethod);
        require(methodConfig.vmStructure == VMStructure.DATA_FLOW,
                "Method VM structure did not override YAML");
        require(methodConfig.protectCodePool,
                "Disabling encryption unexpectedly disabled the CodePool");
        require(!methodConfig.virtualizeInstructionAddresses &&
                        !methodConfig.encryptOperands &&
                        !methodConfig.perMethodOpcodeMap &&
                        !methodConfig.bindConstantsToOperands &&
                        !methodConfig.dynamicStateKey,
                "The encrypt SDK group did not disable every mapped option");
        require(methodConfig.shuffleConstants &&
                        methodConfig.splitCodeStreams &&
                        methodConfig.shuffleInstructionBlocks &&
                        methodConfig.virtualControlFlowGraph,
                "The encrypt SDK group changed the independent shuffle group");
        require(methodConfig.obfuscateDispatch && methodConfig.dynamicCodePoolBuild,
                "The encrypt SDK group changed the independent obfuscate group");
        require(methodConfig.includeMethodsCalledWithin,
                "CallPolicy.INCLUDE was not applied");
        require(!methodConfig.excludeMethodsCalledWithin,
                "CallPolicy.INCLUDE enabled the exclude policy");

        SdkAnnotationReader.MethodDirectives excludedDirectives =
                SdkAnnotationReader.methodDirectives(owner, excludedMethod);
        require(excludedDirectives.excluded(), "@DoNotVirtualize was not applied");

        int removed = SdkAnnotationRemover.remove(List.of(owner));
        require(removed == 3, "Unexpected SDK annotation removal count: " + removed);
        require(owner.invisibleAnnotations.size() == 1 &&
                        "Lexample/Keep;".equals(owner.invisibleAnnotations.getFirst().desc),
                "Annotation cleanup removed a non-SDK annotation");
        require(protectedMethod.invisibleAnnotations.isEmpty(),
                "@Virtualize remained after cleanup");
        require(excludedMethod.invisibleAnnotations.isEmpty(),
                "@DoNotVirtualize remained after cleanup");
    }

    private static void verifyAutomaticStructures()
    {
        verifyAutomaticTier(VMStructure.LOW, EnumSet.of(
                VMStructure.SIMPLE_DISPATCH,
                VMStructure.DISTRIBUTED_DISPATCH,
                VMStructure.MULTIPLE_DISPATCH,
                VMStructure.THREADED_DIRECT,
                VMStructure.THREADED_INDIRECT));
        verifyAutomaticTier(VMStructure.MEDIUM, EnumSet.of(
                VMStructure.CALL_THREADED,
                VMStructure.RECURSIVE,
                VMStructure.CONTINUATION_PASSING,
                VMStructure.OBJECT,
                VMStructure.SELF_MODIFYING,
                VMStructure.EVENT,
                VMStructure.COROUTINE));
        verifyAutomaticTier(VMStructure.HIGH, EnumSet.of(
                VMStructure.DATA_FLOW,
                VMStructure.POLYMORPHIC,
                VMStructure.GRAPH,
                VMStructure.FSM,
                VMStructure.REGISTER_BASED));
    }

    private static void verifyAutomaticTier(VMStructure automatic, Set<VMStructure> expected)
    {
        Set<VMStructure> actual = EnumSet.noneOf(VMStructure.class);
        for (int index = 0; index < expected.size(); index++)
        {
            actual.add(automatic.resolveAuto());
        }
        require(actual.equals(expected), automatic + " candidate pool mismatch: " + actual);
    }

    private static ClassNode fixtureClass()
    {
        ClassNode owner = new ClassNode();
        owner.version = Opcodes.V17;
        owner.access = Opcodes.ACC_PUBLIC;
        owner.name = "example/SdkFixture";
        owner.superName = "java/lang/Object";
        owner.invisibleAnnotations = new ArrayList<>();

        AnnotationNode protectClass = new AnnotationNode(SdkAnnotationReader.PROTECT_CLASS);
        protectClass.values = List.of("constantFix", enumValue(TOGGLE, "ENABLED"));
        owner.invisibleAnnotations.add(protectClass);
        owner.invisibleAnnotations.add(new AnnotationNode("Lexample/Keep;"));

        MethodNode protectedMethod = method("protectedMethod");
        AnnotationNode vmOptions = new AnnotationNode(VM_OPTIONS);
        vmOptions.values = List.of(
                "structure", enumValue(SDK_VM_STRUCTURE, "DATA_FLOW"),
                "encrypt", enumValue(TOGGLE, "DISABLED"));
        AnnotationNode virtualize = new AnnotationNode(SdkAnnotationReader.VIRTUALIZE);
        virtualize.values = List.of(
                "vm", vmOptions,
                "calls", enumValue(CALL_POLICY, "INCLUDE"));
        protectedMethod.invisibleAnnotations = new ArrayList<>(List.of(virtualize));
        owner.methods.add(protectedMethod);

        MethodNode excludedMethod = method("excludedMethod");
        excludedMethod.invisibleAnnotations = new ArrayList<>(List.of(
                new AnnotationNode(SdkAnnotationReader.DO_NOT_VIRTUALIZE)));
        owner.methods.add(excludedMethod);
        return owner;
    }

    private static MethodNode method(String name)
    {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                name,
                "()V",
                null,
                null);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        return method;
    }

    private static BytecodeVMConfig baseConfig()
    {
        return BytecodeVMConfig.parse("""
                input: input.jar
                output: output.jar
                createMode: ONE_FOR_ALL
                location: ONE_PACKAGE
                renameMode: DISABLE
                interpretMode: SAVE_ONLY_REQUIRED_INSTRUCTION
                vmStructure: SIMPLE_DISPATCH
                encryptOperands: true
                includes: []
                exclusions: []
                """);
    }

    private static String[] enumValue(String descriptor, String value)
    {
        return new String[]{descriptor, value};
    }

    private static void require(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }
}
