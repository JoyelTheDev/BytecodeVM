package nhcm.bytecodevm.Generator;

import lombok.Getter;
import nhcm.bytecodevm.Config.BytecodeVMConfig;
import nhcm.bytecodevm.Data.CompiledMethod;
import nhcm.bytecodevm.Data.VMInsn.VMInstruction;
import nhcm.bytecodevm.Data.VMInsn.VMMethod;
import nhcm.bytecodevm.Data.VirtualizationResult;
import nhcm.bytecodevm.Generator.GlobalClass.MethodFrameGenerator;
import nhcm.bytecodevm.Generator.GlobalClass.VMCodePoolGenerator;
import nhcm.bytecodevm.Generator.GlobalClass.VMProgramGenerator;
import nhcm.bytecodevm.Generator.Virtualization.CodePoolGenerator;
import nhcm.bytecodevm.Generator.Virtualization.VMGenerator;
import nhcm.bytecodevm.Tools.OpcMutator;
import nhcm.bytecodevm.Tools.VMMethodCompiler;
import nhcm.bytecodevm.Utils.MethodUtils;
import nhcm.bytecodevm.Utils.RandomUtils;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class VMSetGenerator
{
    private static final int CODE_POOL_METHOD_SIZE_LIMIT = 55_000;

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
    private final InvocationBridgeGenerator invocationBridgeGenerator = new InvocationBridgeGenerator();
    private final List<CompiledMethod> compiledMethods = new ArrayList<>();
    private final List<CompiledMethod> codePoolMethods = new ArrayList<>();
    @Getter
    private final List<CodePoolGenerator> codePoolGenerators = new ArrayList<>();
    private final BytecodeVMConfig config;

    public VMSetGenerator(
            String name, String location,
            OpcMutator opcMutator,
            MethodFrameGenerator methodFrameGenerator,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config)
    {
        this.vmClassName = qualifyClassName(location, name);
        this.codePoolClassName = vmClassName + "$CodePool";
        this.opcMutator = opcMutator;
        this.methodFrameGenerator = methodFrameGenerator;
        this.vmProgramGenerator = vmProgramGenerator;
        this.vmCodePoolGenerator = vmCodePoolGenerator;
        this.config = config;
        this.compiler = new VMMethodCompiler(opcMutator);
    }

    private static String qualifyClassName(String location, String name)
    {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(name, "name");
        String normalizedLocation = location.replace('.', '/');
        while (normalizedLocation.startsWith("/"))
        {
            normalizedLocation = normalizedLocation.substring(1);
        }
        while (normalizedLocation.endsWith("/"))
        {
            normalizedLocation = normalizedLocation.substring(0, normalizedLocation.length() - 1);
        }
        String normalizedName = name.replace('.', '/');
        while (normalizedName.startsWith("/"))
        {
            normalizedName = normalizedName.substring(1);
        }
        return normalizedLocation.isEmpty()
                ? normalizedName
                : normalizedLocation + '/' + normalizedName;
    }

    public VirtualizationResult compile()
    {
        return compile(null);
    }

    public VirtualizationResult compile(ProgressListener progress)
    {
        compiledMethods.clear();
        codePoolMethods.clear();
        codePoolGenerators.clear();
        int methodCount = methodsToObfuscate.size();
        int totalSteps = methodCount * 2 + 3;
        int completedSteps = 0;
        reportProgress(progress, completedSteps, totalSteps, "Compiling methods");

        for (Map.Entry<MethodNode, ClassNode> entry : methodsToObfuscate.entrySet())
        {
            ClassNode owner = entry.getValue();
            MethodNode method = entry.getKey();

            invocationBridgeGenerator.rewrite(owner, method);
            VMMethod vmMethod = compiler.compile(owner, method);

            int codeId = generateUniqueCodeId();
            CompiledMethod compiledMethod = new CompiledMethod(owner, method, vmMethod, codeId, method.desc, MethodUtils.isStatic(method));
            List<CompiledMethod> codePoolParts = splitForCodePools(compiledMethod);
            codePoolMethods.addAll(codePoolParts);
            if (codePoolParts.size() == 1)
            {
                compiledMethods.add(compiledMethod);
            }
            else
            {
                compiledMethods.add(new CompiledMethod(
                        owner,
                        method,
                        vmMethod,
                        codePoolParts.getFirst().codeId,
                        codePoolParts.stream().map(part -> part.codeId).toList(),
                        method.desc,
                        MethodUtils.isStatic(method),
                        false));
            }
            completedSteps++;
            reportProgress(progress, completedSteps, totalSteps, "Compiling methods");
        }

        totalSteps = methodCount + codePoolMethods.size() + 3;
        reportProgress(progress, completedSteps, totalSteps, "Planning pools");
        completedSteps += createCodePools(progress, completedSteps, totalSteps);
        reportProgress(progress, completedSteps, totalSteps, "Built code pools");

        reportProgress(progress, completedSteps, totalSteps, "Generating VM");
        ClassNode vmClass = new VMGenerator(vmClassName, codePoolGenerators, opcMutator, methodFrameGenerator, vmProgramGenerator, vmCodePoolGenerator, config).getClassNode();
        completedSteps++;
        reportProgress(progress, completedSteps, totalSteps, "Generated VM");

        List<ClassNode> codePoolClasses = new ArrayList<>();
        for (CodePoolGenerator codePoolGenerator : codePoolGenerators)
        {
            codePoolClasses.add(codePoolGenerator.getClassNode());
        }

        reportProgress(progress, completedSteps, totalSteps, "Replacing methods");
        Map<String, ClassNode> transformedTargets = new MethodsReplacer(compiledMethods, vmClassName).transform();
        completedSteps++;
        reportProgress(progress, completedSteps, totalSteps, "Replaced methods");
        return new VirtualizationResult(transformedTargets, vmClass, codePoolClasses);
    }

    private static void reportProgress(ProgressListener progress, int completed, int total, String status)
    {
        if (progress != null)
        {
            progress.update(completed, total, status);
        }
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
            String poolClassName = partitions.size() == 1
                    ? codePoolClassName
                    : codePoolClassName + '$' + index;
            codePoolGenerators.add(new CodePoolGenerator(
                    poolClassName,
                    partitions.get(index),
                    vmProgramGenerator,
                    vmCodePoolGenerator,
                    config,
                    true));
        }
        return plannedSteps + 1;
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
                false);
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
        int startPc = instructions.get(from).programCounter;
        int endPc = instructions.get(to - 1).nextProgramCounter;
        int codeStart = startPc - method.vmMethod.pcBase;
        int codeEnd = endPc - method.vmMethod.pcBase;
        VMMethod segmentMethod = new VMMethod(
                Arrays.copyOfRange(method.vmMethod.code, codeStart, codeEnd),
                method.vmMethod.constants,
                method.vmMethod.exceptionHandlers,
                method.vmMethod.maxLocals,
                method.vmMethod.maxStack,
                method.vmMethod.getOpcMutator(),
                startPc,
                method.vmMethod.methodEndPc);
        int codeId = generateUniqueCodeId();
        return new CompiledMethod(
                method.owner,
                method.source,
                segmentMethod,
                codeId,
                method.descriptor,
                method.isStatic,
                false);
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

    public boolean hasMethods()
    {
        return !methodsToObfuscate.isEmpty();
    }
}
