package nhcm.bytecodevm.generator;

import lombok.Getter;
import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.enums.VMStructure;
import nhcm.bytecodevm.data.CompiledMethod;
import nhcm.bytecodevm.data.VMIntegrityPlan;
import nhcm.bytecodevm.data.vminsn.VMInstruction;
import nhcm.bytecodevm.data.vminsn.VMMethod;
import nhcm.bytecodevm.data.VirtualizationResult;
import nhcm.bytecodevm.generator.integrity.VMIntegrityGenerator;
import nhcm.bytecodevm.generator.integrity.IntegrityEntryTransformer;
import nhcm.bytecodevm.generator.globalclass.MethodFrameGenerator;
import nhcm.bytecodevm.generator.globalclass.VMCodePoolGenerator;
import nhcm.bytecodevm.generator.globalclass.VMProgramGenerator;
import nhcm.bytecodevm.generator.transformer.InvocationBridgeGenerator;
import nhcm.bytecodevm.generator.transformer.MethodsReplacer;
import nhcm.bytecodevm.generator.virtualization.CodePoolGenerator;
import nhcm.bytecodevm.generator.virtualization.superinstruction.SuperInstructionRegistry;
import nhcm.bytecodevm.generator.virtualization.VMGenerator;
import nhcm.bytecodevm.generator.virtualization.VMMethodSegmenter;
import nhcm.bytecodevm.generator.virtualization.VMObfProfile;
import nhcm.bytecodevm.tools.JarTransformer;
import nhcm.bytecodevm.tools.OpcMutator;
import nhcm.bytecodevm.tools.VMMethodCompiler;
import nhcm.bytecodevm.utils.MethodUtils;
import nhcm.bytecodevm.utils.RandomUtils;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class VMSetGenerator
{
    private static final int CODE_POOL_METHOD_SIZE_LIMIT = 32_000;

    public interface ProgressListener
    {
        void update(int completed, int total, String status);
    }

    private final Map<MethodNode, ClassNode> methodsToObfuscate = new LinkedHashMap<>();
    private final Set<Integer> uniqueCodeIds = new LinkedHashSet<>();

    public final String vmClassName;
    public final String codePoolClassName;
    public final OpcMutator opcMutator;

    @Getter
    private final MethodFrameGenerator methodFrameGenerator;
    @Getter
    private final VMProgramGenerator vmProgramGenerator;
    @Getter
    private final VMCodePoolGenerator vmCodePoolGenerator;
    private final VMMethodCompiler compiler;
    private final InvocationBridgeGenerator invocationBridgeGenerator;
    private final List<CompiledMethod> compiledMethods = new ArrayList<>();
    private final List<CompiledMethod> codePoolMethods = new ArrayList<>();
    @Getter
    private final List<CodePoolGenerator> codePoolGenerators = new ArrayList<>();
    private final BytecodeVMConfig config;
    public final VMStructure vmStructure;
    private final GeneratedMemberNamer namer;
    private final VMObfProfile protectionProfile;
    private final SuperInstructionRegistry superInstructions;
    private final int integrityCapability;

    public VMSetGenerator(
            String name, String location,
            OpcMutator opcMutator,
            MethodFrameGenerator methodFrameGenerator,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config)
    {
        this(name, location, opcMutator, methodFrameGenerator, vmProgramGenerator, vmCodePoolGenerator, config, GeneratedMemberNamer.DISABLED);
    }

    public VMSetGenerator(
            String name, String location,
            OpcMutator opcMutator,
            MethodFrameGenerator methodFrameGenerator,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config,
            GeneratedMemberNamer namer)
    {
        this.vmClassName = namer.className(location, name);
        this.codePoolClassName = namer.className(classPackage(vmClassName), classSimpleName(vmClassName) + "$CodePool");
        this.opcMutator = opcMutator;
        this.methodFrameGenerator = methodFrameGenerator;
        this.vmProgramGenerator = vmProgramGenerator;
        this.vmCodePoolGenerator = vmCodePoolGenerator;
        this.config = config.resolveVMStructure();
        this.vmStructure = this.config.vmStructure;
        this.integrityCapability = nonZeroRandom();
        this.namer = namer;
        this.invocationBridgeGenerator = new InvocationBridgeGenerator(namer);
        this.protectionProfile = VMObfProfile.random();
        this.superInstructions = new SuperInstructionRegistry(this.config.superInstructionMaxHandlers);
        this.compiler = new VMMethodCompiler(opcMutator);
    }

    private static String classPackage(String className)
    {
        int slash = className.lastIndexOf('/');
        return slash < 0 ? "" : className.substring(0, slash);
    }

    private static String classSimpleName(String className)
    {
        int slash = className.lastIndexOf('/');
        return slash < 0 ? className : className.substring(slash + 1);
    }

    public VirtualizationResult compile()
    {
        return compile(null, null);
    }

    public VirtualizationResult compile(ProgressListener progress)
    {
        return compile(null, progress);
    }

    public VirtualizationResult compile(JarTransformer.JarContext serializationContext, ProgressListener progress)
    {
        compiledMethods.clear();
        codePoolMethods.clear();
        codePoolGenerators.clear();
        superInstructions.clear();
        int methodCount = methodsToObfuscate.size();
        int totalSteps = methodCount * 2 + 3;
        int completedSteps = 0;
        reportProgress(progress, completedSteps, totalSteps, "Compiling methods");

        List<PendingMethod> invocationBridges = new ArrayList<>();
        for (Map.Entry<MethodNode, ClassNode> entry : methodsToObfuscate.entrySet())
        {
            ClassNode owner = entry.getValue();
            MethodNode method = entry.getKey();

            for (MethodNode bridge : invocationBridgeGenerator.rewrite(owner, method))
            {
                if (config.virtualizeInvocationBridges && InvocationBridgeGenerator.canVirtualizeBridge(bridge))
                {
                    invocationBridges.add(new PendingMethod(owner, bridge));
                }
            }
            compileMethod(owner, method);
            completedSteps++;
            reportProgress(progress, completedSteps, totalSteps, "Compiling methods");
        }

        for (PendingMethod bridge : invocationBridges)
        {
            compileMethod(bridge.owner(), bridge.method());
        }

        totalSteps = methodCount + invocationBridges.size() + codePoolMethods.size() + 3;
        reportProgress(progress, completedSteps, totalSteps, "Planning pools");
        completedSteps += createCodePools(progress, completedSteps, totalSteps);
        reportProgress(progress, completedSteps, totalSteps, "Built code pools");

        reportProgress(progress, completedSteps, totalSteps, "Generating VM");
        VMGenerator vmGenerator = new VMGenerator(
                vmClassName,
                codePoolGenerators,
                opcMutator,
                methodFrameGenerator,
                vmProgramGenerator,
                vmCodePoolGenerator,
                config,
                namer,
                protectionProfile,
                superInstructions,
                integrityCapability);
        ClassNode vmClass = vmGenerator.getClassNode();
        List<ClassNode> vmAuxiliaryClasses = vmGenerator.getAuxiliaryClasses();
        completedSteps++;
        reportProgress(progress, completedSteps, totalSteps, "Generated VM");

        List<ClassNode> codePoolClasses = new ArrayList<>();
        for (CodePoolGenerator codePoolGenerator : codePoolGenerators)
        {
            codePoolClasses.add(codePoolGenerator.getClassNode());
        }

        IntegrityBuild integrityBuild = buildIntegrity(
                serializationContext,
                vmClass,
                codePoolClasses,
                vmAuxiliaryClasses);

        reportProgress(progress, completedSteps, totalSteps, "Replacing methods");
        Set<MethodNode> integrityProtectedMethods = selectIntegrityProtectedMethods(integrityBuild.plan());
        Map<String, ClassNode> transformedTargets = new MethodsReplacer(
                compiledMethods,
                vmClassName,
                integrityBuild.plan(),
                integrityProtectedMethods).transform();
        IntegrityEntryTransformer.Result integrityEntries = prepareIntegrityEntries(
                integrityBuild,
                integrityProtectedMethods);
        VirtualizationResult integrityResult = virtualizeIntegrityDerivation(
                integrityBuild,
                integrityEntries);
        if (integrityResult != null)
        {
            transformedTargets.putAll(integrityResult.transformedTarget);
        }
        completedSteps++;
        reportProgress(progress, completedSteps, totalSteps, "Replaced methods");

        List<ClassNode> generatedClasses = new ArrayList<>(codePoolClasses);
        generatedClasses.add(methodFrameGenerator.getClassNode());
        generatedClasses.add(vmProgramGenerator.getClassNode());
        generatedClasses.add(vmCodePoolGenerator.getClassNode());
        generatedClasses.addAll(vmAuxiliaryClasses);
        if (integrityResult != null)
        {
            generatedClasses.add(integrityResult.vmClass);
            generatedClasses.addAll(integrityResult.codePoolClass);
        }
        return new VirtualizationResult(transformedTargets, vmClass, generatedClasses);
    }

    private IntegrityBuild buildIntegrity(
            JarTransformer.JarContext serializationContext,
            ClassNode vmClass,
            List<ClassNode> codePoolClasses,
            List<ClassNode> vmAuxiliaryClasses)
    {
        boolean hasProtectedMethod = compiledMethods.stream().anyMatch(method ->
                method.config != null &&
                method.config.vmIntegrityCheck &&
                method.config.vmIntegrityCheckRatio > 0.0D);
        if (!hasProtectedMethod || serializationContext == null || compiledMethods.isEmpty())
        {
            return IntegrityBuild.empty();
        }

        Map<String, ClassNode> hashClassesByName = new LinkedHashMap<>();
        addHashClass(hashClassesByName, methodFrameGenerator.getClassNode());
        addHashClass(hashClassesByName, vmProgramGenerator.getClassNode());
        addHashClass(hashClassesByName, vmCodePoolGenerator.getClassNode());
        addHashClass(hashClassesByName, vmClass);
        for (ClassNode auxiliaryClass : vmAuxiliaryClasses)
        {
            addHashClass(hashClassesByName, auxiliaryClass);
        }
        for (ClassNode codePoolClass : codePoolClasses)
        {
            addHashClass(hashClassesByName, codePoolClass);
        }
        List<ClassNode> hashClasses = new ArrayList<>(hashClassesByName.values());

        JarTransformer.JarContext snapshot = new JarTransformer.JarContext();
        snapshot.classes.putAll(serializationContext.classes);
        for (ClassNode hashClass : hashClasses)
        {
            snapshot.addClass(hashClass);
        }
        snapshot.resources.putAll(serializationContext.resources);

        List<VMIntegrityGenerator.HashTarget> targets = new ArrayList<>();
        for (ClassNode hashClass : hashClasses)
        {
            int seed = nonZeroRandom();
            int expected = VMIntegrityGenerator.hashBytes(JarTransformer.toBytes(hashClass, snapshot), seed);
            targets.add(new VMIntegrityGenerator.HashTarget(hashClass.name + ".class", seed, expected));
        }

        String carrierName = namer.className(
                classPackage(vmClassName),
                classSimpleName(vmClassName) + "$Integrity");
        VMIntegrityGenerator generator = new VMIntegrityGenerator(
                carrierName,
                targets,
                integrityCapability,
                config.vmIntegrityCheckRatio,
                config.vmIntegrityRecheckInterval,
                namer);

        return new IntegrityBuild(generator.plan(), generator.classNode(), generator.derivationMethods());
    }

    private Set<MethodNode> selectIntegrityProtectedMethods(VMIntegrityPlan plan)
    {
        Set<MethodNode> selected = Collections.newSetFromMap(new IdentityHashMap<>());
        if (plan == null)
        {
            return selected;
        }
        for (CompiledMethod method : compiledMethods)
        {
            BytecodeVMConfig methodConfig = method.config == null ? config : method.config;
            if (methodConfig.vmIntegrityCheck &&
                RandomUtils.randomDouble() <= methodConfig.vmIntegrityCheckRatio)
            {
                selected.add(method.source);
            }
        }
        return selected;
    }

    private IntegrityEntryTransformer.Result prepareIntegrityEntries(
            IntegrityBuild integrityBuild,
            Set<MethodNode> protectedMethods)
    {
        if (integrityBuild.plan() == null || protectedMethods.isEmpty())
        {
            return IntegrityEntryTransformer.Result.empty();
        }
        return new IntegrityEntryTransformer(
                integrityBuild.carrierClass(),
                integrityBuild.plan(),
                vmClassName,
                namer).transform(compiledMethods, protectedMethods);
    }

    private VirtualizationResult virtualizeIntegrityDerivation(
            IntegrityBuild integrityBuild,
            IntegrityEntryTransformer.Result integrityEntries)
    {
        if (integrityBuild.plan() == null)
        {
            return null;
        }

        VMSetGenerator integrityVm = new VMSetGenerator(
                classSimpleName(integrityBuild.plan().owner()) + "$VM",
                classPackage(integrityBuild.plan().owner()),
                OpcMutator.MutateStrategy.RANDOM_INT.getMutator(),
                methodFrameGenerator,
                vmProgramGenerator,
                vmCodePoolGenerator,
                config.integrityConfig(),
                namer);
        for (IntegrityEntryTransformer.GeneratedColdEntry coldEntry : integrityEntries.coldEntries())
        {
            integrityVm.addMethod(coldEntry.method(), coldEntry.owner());
        }
        for (MethodNode derivationMethod : integrityBuild.derivationMethods())
        {
            integrityVm.addMethod(derivationMethod, integrityBuild.carrierClass());
        }
        return integrityVm.compile();
    }

    private static void reportProgress(ProgressListener progress, int completed, int total, String status)
    {
        if (progress != null)
        {
            progress.update(completed, total, status);
        }
    }

    private static void addHashClass(Map<String, ClassNode> classes, ClassNode classNode)
    {
        classes.putIfAbsent(classNode.name, classNode);
    }

    private void compileMethod(ClassNode owner, MethodNode method)
    {
        VMMethod vmMethod = compiler.compile(owner, method);
        BytecodeVMConfig methodConfig = config.forMethod(owner, method);

        int codeId = generateUniqueCodeId();
        CompiledMethod compiledMethod = new CompiledMethod(
                owner,
                method,
                vmMethod,
                codeId,
                List.of(codeId),
                method.desc,
                MethodUtils.isStatic(method),
                true,
                methodConfig);
        List<CompiledMethod> codePoolParts = splitForCodePools(compiledMethod);
        codePoolMethods.addAll(codePoolParts);
        if (codePoolParts.size() == 1)
        {
            compiledMethods.add(compiledMethod);
            return;
        }

        compiledMethods.add(new CompiledMethod(
                owner,
                method,
                vmMethod,
                codePoolParts.getFirst().codeId,
                codePoolParts.stream().map(part -> part.codeId).toList(),
                method.desc,
                MethodUtils.isStatic(method),
                false,
                methodConfig));
    }

    private int createCodePools(ProgressListener progress, int completedSteps, int totalSteps)
    {
        List<List<CompiledMethod>> partitions = partitionCompiledMethods(progress, completedSteps, totalSteps);
        int plannedSteps = codePoolMethods.size();
        for (int index = 0; index < partitions.size(); index++)
        {
            reportProgress(
                    progress,
                    completedSteps + plannedSteps,
                    totalSteps,
                    "Building pool " + (index + 1) + "/" + partitions.size());
            String poolClassName = poolClassName(index, partitions.size());
            codePoolGenerators.add(new CodePoolGenerator(
                    poolClassName,
                    partitions.get(index),
                    vmProgramGenerator,
                    vmCodePoolGenerator,
                    config,
                    true,
                    namer,
                    protectionProfile,
                    superInstructions));
        }
        return plannedSteps + 1;
    }

    private String poolClassName(int index, int partitionCount)
    {
        if (partitionCount == 1 || !namer.enabled())
        {
            return partitionCount == 1 ? codePoolClassName : codePoolClassName + '$' + index;
        }
        return namer.className(classPackage(vmClassName), classSimpleName(codePoolClassName) + '$' + index);
    }

    private List<List<CompiledMethod>> partitionCompiledMethods(ProgressListener progress, int completedSteps, int totalSteps)
    {
        List<List<CompiledMethod>> partitions = new ArrayList<>();
        List<CompiledMethod> current = new ArrayList<>();

        for (int index = 0; index < codePoolMethods.size(); index++)
        {
            CompiledMethod method = codePoolMethods.get(index);
            reportProgress(
                    progress,
                    completedSteps + index + 1,
                    totalSteps,
                    "Planning " + (index + 1) + "/" + codePoolMethods.size());
            current.add(method);
            if (fitsInCodePool(current))
            {
                continue;
            }

            current.remove(current.size() - 1);
            if (current.isEmpty())
            {
                throw methodTooLarge(method);
            }
            partitions.add(new ArrayList<>(current));
            current.clear();
            current.add(method);
            if (!fitsInCodePool(current))
            {
                throw methodTooLarge(method);
            }
        }

        if (!current.isEmpty())
        {
            partitions.add(new ArrayList<>(current));
        }
        return partitions;
    }

    private boolean fitsInCodePool(List<CompiledMethod> methods)
    {
        CodePoolGenerator candidate = new CodePoolGenerator(
                codePoolClassName,
                methods,
                vmProgramGenerator,
                vmCodePoolGenerator,
                config,
                false,
                GeneratedMemberNamer.DISABLED,
                protectionProfile);
        return candidate.getMaxGeneratedMethodSize() <= CODE_POOL_METHOD_SIZE_LIMIT;
    }

    private List<CompiledMethod> splitForCodePools(CompiledMethod method)
    {
        if (fitsInCodePool(List.of(method)))
        {
            return List.of(method);
        }

        List<VMInstruction> instructions = method.vmMethod.getInstructions();
        if (instructions.size() <= 1)
        {
            throw methodTooLarge(method);
        }
        return splitRange(method, instructions, 0, instructions.size());
    }

    private List<CompiledMethod> splitRange(
            CompiledMethod method,
            List<VMInstruction> instructions,
            int from,
            int to)
    {
        CompiledMethod segment = segment(method, instructions, from, to);
        if (fitsInCodePool(List.of(segment)))
        {
            return List.of(segment);
        }
        if (to - from <= 1)
        {
            throw methodTooLarge(method);
        }

        int mid = from + (to - from) / 2;
        List<CompiledMethod> parts = new ArrayList<>();
        parts.addAll(splitRange(method, instructions, from, mid));
        parts.addAll(splitRange(method, instructions, mid, to));
        return parts;
    }

    private CompiledMethod segment(
            CompiledMethod method,
            List<VMInstruction> instructions,
            int from,
            int to)
    {
        VMMethod segmentMethod = VMMethodSegmenter.segment(
                method.vmMethod,
                instructions,
                from,
                to);
        int codeId = generateUniqueCodeId();
        return new CompiledMethod(
                method.owner,
                method.source,
                segmentMethod,
                codeId,
                List.of(codeId),
                method.descriptor,
                method.isStatic,
                false,
                method.config);
    }

    private static IllegalStateException methodTooLarge(CompiledMethod method)
    {
        return new IllegalStateException(
                "VM method cannot fit in a CodePool: " +
                        method.owner.name + '.' +
                        method.source.name + method.source.desc);
    }

    public void addMethod(MethodNode methodNode, ClassNode classNode)
    {
        methodsToObfuscate.put(methodNode, classNode);
    }

    public int methodCount()
    {
        return methodsToObfuscate.size();
    }

    private int generateUniqueCodeId()
    {
        int codeId;
        do
        {
            codeId = RandomUtils.randomInt();
        } while (uniqueCodeIds.contains(codeId));
        uniqueCodeIds.add(codeId);
        return codeId;
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

    public boolean hasMethods()
    {
        return !methodsToObfuscate.isEmpty();
    }

    private record IntegrityBuild(VMIntegrityPlan plan, ClassNode carrierClass, List<MethodNode> derivationMethods)
    {
        private static IntegrityBuild empty()
        {
            return new IntegrityBuild(null, null, List.of());
        }
    }

    private record PendingMethod(ClassNode owner, MethodNode method)
    {
    }
}
