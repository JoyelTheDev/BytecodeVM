package nhcm.bytecodevm.generator;

import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.config.TargetMatcher;
import nhcm.bytecodevm.config.sdk.SdkAnnotationOptions.SdkCallPolicy;
import nhcm.bytecodevm.config.sdk.SdkAnnotationReader;
import nhcm.bytecodevm.config.sdk.SdkAnnotationRemover;
import nhcm.bytecodevm.data.VirtualizationResult;
import nhcm.bytecodevm.enums.VMStructure;
import nhcm.bytecodevm.generator.transformer.ConstantFixTransformer;
import nhcm.bytecodevm.generator.globalclass.MethodFrameGenerator;
import nhcm.bytecodevm.generator.globalclass.VMCodePoolGenerator;
import nhcm.bytecodevm.generator.globalclass.VMProgramGenerator;
import nhcm.bytecodevm.tools.JarTransformer;
import nhcm.bytecodevm.tools.OpcMutator;
import nhcm.bytecodevm.utils.ClassUtils;
import nhcm.bytecodevm.utils.LogColors;
import nhcm.bytecodevm.utils.MethodUtils;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class Obfuscator
{
    private static final Logger logger = LoggerFactory.getLogger(Obfuscator.class);

    private final BytecodeVMConfig config;
    private final TargetMatcher targetInclude;
    private final TargetMatcher targetExclude;

    private final List<VMSetGenerator> VMSetGenerators = new ArrayList<>();
    private final GeneratedMemberNamer namer;

    public Obfuscator(BytecodeVMConfig config)
    {
        this.config = config;
        this.namer = new GeneratedMemberNamer(config);
        this.targetExclude = new TargetMatcher();
        for(String exclusion : config.exclusions)
        {
            targetExclude.add(exclusion);
        }
        this.targetInclude = new TargetMatcher();
        for(String include : config.includes)
        {
            targetInclude.add(include);
        }
    }

    public void obfuscate()
    {
        if(!Files.exists(config.inputFile))
        {
            logger.error("{}", LogColors.error("Input file does not exist: " + LogColors.path(config.inputFile.toAbsolutePath())));
            return;
        }
        logger.info("{}", LogColors.lifecycle(
                "Obfuscating " +
                        LogColors.path(config.inputFile.toAbsolutePath()) +
                        " -> " +
                        LogColors.path(config.outputFile.toAbsolutePath())));
        try
        {
            JarTransformer.transformJar(config.inputFile.toFile(), config.outputFile.toFile(), this::obfuscateProcess);
        } catch (IOException e)
        {
            logger.error(LogColors.error("Failed obfuscating while reading or writing jar"), e);
        }
    }

    private void obfuscateProcess(JarTransformer.JarContext context)
    {
        namer.reserveClassNames(context.classes.keySet());
        processJar(context);
        logger.info("{}", LogColors.lifecycle("Adding required VM support classes"));
        logger.debug("{}", LogColors.success("VM support classes are isolated per VM set"));
        List<VMSetGenerator> generators = new ArrayList<>(this.VMSetGenerators);
        for(VMSetGenerator generator : generators)
        {
            logger.info("{}", LogColors.virtualize(
                    "Virtualizing VM: " +
                            LogColors.strong(generator.vmClassName) +
                            " [" + generator.vmStructure + "]" +
                            " (" + generator.methodCount() + " method(s))"));
            VirtualizationResult result;
            try (CliProgress progress = new CliProgress(generator.vmClassName))
            {
                result = generator.compile(context, progress::update);
            }
            context.classes.putAll(result.transformedTarget);
            context.addClass(result.vmClass);
            for(ClassNode codePoolClass : result.codePoolClass)
            {
                context.addClass(codePoolClass);
            }
            logger.info("{}", LogColors.success("Done virtualizing VM: " + LogColors.strong(generator.vmClassName)));
        }
        logger.info("{}", LogColors.success("Done virtualizing all classes"));
        if (config.removeAnnotations)
        {
            int removed = SdkAnnotationRemover.remove(context.classes.values());
            logger.debug("Removed {} BytecodeVM SDK annotation(s)", removed);
        }
    }

    private void processJar(JarTransformer.JarContext context)
    {
        logger.info("{}", LogColors.scan("Scanning input file for methods to obfuscate"));
        int fixedConstants = new ConstantFixTransformer(config).transform(context.classes.values());
        if (fixedConstants != 0)
        {
            logger.info("{}", LogColors.scan("Moved " + LogColors.strong(fixedConstants) + " static final constant(s) into <clinit>"));
        }

        String globalLocation = "BytecodeVM";

        Map<GeneratorProfile, List<VMSetGenerator>> allInOneVms = new LinkedHashMap<>();
        List<VMSetGenerator> perClasses = new ArrayList<>();
        List<VMSetGenerator> perMethods = new ArrayList<>();
        Map<String, Map<GeneratorProfile, List<VMSetGenerator>>> perPackage = new LinkedHashMap<>();
        Map<GeneratorGroupKey, Integer> generatorOrdinals = new HashMap<>();

        Set<String> securityManagerClasses = securityManagerClasses(context.classes.values());
        List<MethodCandidate> candidates = collectMethodCandidates(context.classes.values(), securityManagerClasses);
        Map<MethodId, MethodCandidate> candidateById = indexCandidates(candidates);
        Map<MethodId, Set<MethodId>> callsByMethod = collectInternalCalls(candidates, candidateById);
        Set<MethodId> rootMethods = rootMethods(candidates);
        Set<MethodId> includeRoots = rootsWithPolicy(rootMethods, candidateById, SdkCallPolicy.INCLUDE);
        Set<MethodId> excludeRoots = rootsWithPolicy(rootMethods, candidateById, SdkCallPolicy.EXCLUDE);
        Set<MethodId> includedCalls = collectCalledWithin(includeRoots, callsByMethod, candidateById);
        Set<MethodId> excludedCalls = collectCalledWithin(excludeRoots, callsByMethod, candidateById);

        int matchedMethods = 0;
        int calledMethodsIncluded = 0;
        int calledMethodsExcluded = 0;

        for(ClassNode classNode : context.classes.values())
        {
            String classPackage = ClassUtils.getPackageName(classNode);
            String vmLocation = getVMLocation(globalLocation, classPackage, classNode);

            Map<GeneratorProfile, List<VMSetGenerator>> perClass = null;

            if(config.createMode == BytecodeVMConfig.VMCreateMode.PER_CLASS)
            {
                perClass = new LinkedHashMap<>();
            }

            for(MethodNode methodNode : classNode.methods)
            {
                MethodCandidate candidate = candidateById.get(MethodId.of(classNode, methodNode));
                if(candidate == null)
                {
                    continue;
                }
                boolean includedByCall = includedCalls.contains(candidate.id) && !candidate.explicitIncluded;
                boolean excludedByCall = excludedCalls.contains(candidate.id) && !rootMethods.contains(candidate.id);
                if(!selected(candidate, includedByCall, excludedByCall))
                {
                    if (excludedByCall && candidate.eligible && !candidate.explicitExcluded)
                    {
                        calledMethodsExcluded++;
                    }
                    continue;
                }
                if (includedByCall)
                {
                    calledMethodsIncluded++;
                }

                matchedMethods++;
                GeneratorProfile profile = GeneratorProfile.of(candidate.methodConfig);

                switch(config.createMode)
                {
                    case PER_CLASS ->
                    {
                        List<VMSetGenerator> generators = perClass.computeIfAbsent(
                                profile,
                                ignored -> newVMSetGenerators(
                                        profileName(ClassUtils.getSimpleName(classNode) + "$VM", profile),
                                        vmLocation,
                                        profile.apply(config)));
                        GeneratorGroupKey key = new GeneratorGroupKey(classNode.name, profile);
                        int ordinal = generatorOrdinals.merge(key, 1, Integer::sum) - 1;
                        pickGenerator(generators, ordinal).addMethod(methodNode, classNode);
                    }

                    case PER_METHOD ->
                    {
                        VMSetGenerator perMethod = newVMSetGenerator(
                                ClassUtils.getSimpleName(classNode) + "$" + methodNode.name + "$VM",
                                vmLocation,
                                profile.apply(config)
                        );

                        perMethod.addMethod(methodNode, classNode);
                        perMethods.add(perMethod);
                    }

                    case PER_PACKAGE ->
                    {
                        Map<GeneratorProfile, List<VMSetGenerator>> packageProfiles =
                                perPackage.computeIfAbsent(classPackage, ignored -> new LinkedHashMap<>());
                        List<VMSetGenerator> generators = packageProfiles.computeIfAbsent(
                                profile,
                                ignored -> newVMSetGenerators(
                                        profileName(classPackage + "$VM", profile),
                                        classPackage,
                                        profile.apply(config)));
                        GeneratorGroupKey key = new GeneratorGroupKey(classPackage, profile);
                        int ordinal = generatorOrdinals.merge(key, 1, Integer::sum) - 1;
                        pickGenerator(generators, ordinal).addMethod(methodNode, classNode);
                    }

                    case ONE_FOR_ALL ->
                    {
                        List<VMSetGenerator> generators = allInOneVms.computeIfAbsent(
                                profile,
                                ignored -> newVMSetGenerators(
                                        profileName("BytecodeVM", profile),
                                        "BytecodeVM",
                                        profile.apply(config)));
                        GeneratorGroupKey key = new GeneratorGroupKey("", profile);
                        int ordinal = generatorOrdinals.merge(key, 1, Integer::sum) - 1;
                        pickGenerator(generators, ordinal).addMethod(methodNode, classNode);
                    }
                }
            }

            if(config.createMode == BytecodeVMConfig.VMCreateMode.PER_CLASS && perClass != null)
            {
                perClass.values().forEach(generators -> addNonEmpty(perClasses, generators));
            }
        }

        switch(config.createMode)
        {
            case PER_CLASS -> VMSetGenerators.addAll(perClasses);
            case PER_METHOD -> VMSetGenerators.addAll(perMethods);
            case PER_PACKAGE -> perPackage.values().forEach(profiles ->
                    profiles.values().forEach(generators -> addNonEmpty(VMSetGenerators, generators)));
            case ONE_FOR_ALL ->
            {
                allInOneVms.values().forEach(generators -> addNonEmpty(VMSetGenerators, generators));
            }
        }

        logger.info("{}", LogColors.scan(
                "Scanned input file, found " +
                LogColors.strong(matchedMethods) +
                " method(s) across " +
                LogColors.strong(VMSetGenerators.size()) +
                " VM set(s)"
        ));
        if (!includeRoots.isEmpty() || !excludeRoots.isEmpty())
        {
            logger.info("{}", LogColors.scan(
                    "Call expansion included " +
                    LogColors.strong(calledMethodsIncluded) +
                    " and excluded " +
                    LogColors.strong(calledMethodsExcluded) +
                    " target method(s)"));
        }
    }

    private VMSetGenerator newVMSetGenerator(
            String name,
            String location,
            BytecodeVMConfig generatorConfig)
    {
        BytecodeVMConfig resolvedConfig = generatorConfig.resolveVMStructure();
        MethodFrameGenerator methodFrameGenerator = new MethodFrameGenerator(
                namer.className(location, name + "$Frame"),
                namer,
                resolvedConfig.vmStructure);
        VMProgramGenerator vmProgramGenerator = new VMProgramGenerator(
                namer.className(location, name + "$Program"),
                namer);
        VMCodePoolGenerator vmCodePoolGenerator = new VMCodePoolGenerator(
                namer.className(location, name + "$PoolRecord"),
                vmProgramGenerator,
                namer);
        return new VMSetGenerator(
                name,
                location,
                OpcMutator.MutateStrategy.RANDOM_INT.getMutator(),
                methodFrameGenerator,
                vmProgramGenerator,
                vmCodePoolGenerator,
                resolvedConfig,
                namer);
    }

    private List<VMSetGenerator> newVMSetGenerators(
            String name,
            String location,
            BytecodeVMConfig generatorConfig)
    {
        int count = generatorConfig.createMode == BytecodeVMConfig.VMCreateMode.PER_METHOD
                ? 1
                : generatorConfig.vmCount;
        List<VMSetGenerator> generators = new ArrayList<>(count);
        for (int index = 0; index < count; index++)
        {
            String vmName = count == 1 ? name : name + "$" + index;
            generators.add(newVMSetGenerator(vmName, location, generatorConfig));
        }
        return generators;
    }

    private static VMSetGenerator pickGenerator(List<VMSetGenerator> generators, int ordinal)
    {
        if (generators.isEmpty())
        {
            throw new IllegalArgumentException("No VM generator is available");
        }
        return generators.get(Math.floorMod(ordinal, generators.size()));
    }

    private static String profileName(String base, GeneratorProfile profile)
    {
        return base + "$P" + Integer.toUnsignedString(profile.hashCode(), 36);
    }

    private static void addNonEmpty(List<VMSetGenerator> target, List<VMSetGenerator> candidates)
    {
        for (VMSetGenerator generator : candidates)
        {
            if (generator.hasMethods())
            {
                target.add(generator);
            }
        }
    }

    private List<MethodCandidate> collectMethodCandidates(
            Collection<ClassNode> classes,
            Set<String> securityManagerClasses)
    {
        List<MethodCandidate> candidates = new ArrayList<>();
        for (ClassNode classNode : classes)
        {
            Set<String> stackTraceSensitiveMethods = stackTraceSensitiveMethods(classNode);
            SdkAnnotationReader.ClassDirectives classDirectives =
                    SdkAnnotationReader.classDirectives(classNode);
            boolean classIncluded = targetInclude.isClassMatched(classNode) || classDirectives.included();
            boolean classExcluded = targetExclude.isClassMatched(classNode) || classDirectives.excluded();
            boolean securityManagerClass = securityManagerClasses.contains(classNode.name);
            for (MethodNode methodNode : classNode.methods)
            {
                SdkAnnotationReader.MethodDirectives sdkMethod =
                        SdkAnnotationReader.methodDirectives(classNode, methodNode);
                boolean ignored = securityManagerClass ||
                                  shouldIgnoreMethod(methodNode) ||
                                  stackTraceSensitiveMethods.contains(methodKey(methodNode));
                boolean explicitIncluded = sdkMethod.selected() ||
                        (classIncluded && targetInclude.isMethodMatched(classNode, methodNode));
                boolean explicitExcluded = classExcluded ||
                        sdkMethod.excluded() ||
                        targetExclude.isMethodMatched(classNode, methodNode);
                BytecodeVMConfig methodConfig = config.forMethod(classNode, methodNode);
                candidates.add(new MethodCandidate(
                        classNode,
                        methodNode,
                        MethodId.of(classNode, methodNode),
                        !ignored,
                        explicitIncluded,
                        explicitExcluded,
                        methodConfig,
                        callPolicy(methodConfig)));
            }
        }
        return candidates;
    }

    private static Map<MethodId, MethodCandidate> indexCandidates(List<MethodCandidate> candidates)
    {
        Map<MethodId, MethodCandidate> byId = new LinkedHashMap<>();
        for (MethodCandidate candidate : candidates)
        {
            byId.put(candidate.id, candidate);
        }
        return byId;
    }

    private static Set<MethodId> rootMethods(List<MethodCandidate> candidates)
    {
        Set<MethodId> roots = new LinkedHashSet<>();
        for (MethodCandidate candidate : candidates)
        {
            if (candidate.eligible && candidate.explicitIncluded && !candidate.explicitExcluded)
            {
                roots.add(candidate.id);
            }
        }
        return roots;
    }

    private static Set<MethodId> rootsWithPolicy(
            Set<MethodId> roots,
            Map<MethodId, MethodCandidate> candidates,
            SdkCallPolicy policy)
    {
        Set<MethodId> selected = new LinkedHashSet<>();
        for (MethodId root : roots)
        {
            MethodCandidate candidate = candidates.get(root);
            if (candidate != null && candidate.callPolicy == policy)
            {
                selected.add(root);
            }
        }
        return Set.copyOf(selected);
    }

    private static SdkCallPolicy callPolicy(BytecodeVMConfig methodConfig)
    {
        if (methodConfig.excludeMethodsCalledWithin)
        {
            return SdkCallPolicy.EXCLUDE;
        }
        if (methodConfig.includeMethodsCalledWithin)
        {
            return SdkCallPolicy.INCLUDE;
        }
        return SdkCallPolicy.NONE;
    }

    private static Map<MethodId, Set<MethodId>> collectInternalCalls(
            List<MethodCandidate> candidates,
            Map<MethodId, MethodCandidate> candidateById)
    {
        Map<MethodId, Set<MethodId>> calls = new LinkedHashMap<>();
        for (MethodCandidate candidate : candidates)
        {
            Set<MethodId> targets = new LinkedHashSet<>();
            for (AbstractInsnNode instruction : candidate.method.instructions)
            {
                if (!(instruction instanceof MethodInsnNode methodInsn))
                {
                    continue;
                }
                MethodId target = new MethodId(methodInsn.owner, methodInsn.name, methodInsn.desc);
                MethodCandidate targetCandidate = candidateById.get(target);
                if (targetCandidate != null && targetCandidate.eligible)
                {
                    targets.add(target);
                }
            }
            calls.put(candidate.id, Set.copyOf(targets));
        }
        return Map.copyOf(calls);
    }

    private static Set<MethodId> collectCalledWithin(
            Set<MethodId> roots,
            Map<MethodId, Set<MethodId>> callsByMethod,
            Map<MethodId, MethodCandidate> candidateById)
    {
        Set<MethodId> called = new LinkedHashSet<>();
        Deque<MethodId> work = new ArrayDeque<>(roots);
        while (!work.isEmpty())
        {
            MethodId current = work.removeFirst();
            for (MethodId target : callsByMethod.getOrDefault(current, Set.of()))
            {
                if (roots.contains(target) || !called.add(target))
                {
                    continue;
                }
                MethodCandidate candidate = candidateById.get(target);
                if (candidate != null && candidate.eligible && !candidate.explicitExcluded)
                {
                    work.addLast(target);
                }
            }
        }
        return Set.copyOf(called);
    }

    private boolean selected(MethodCandidate candidate, boolean includedByCall, boolean excludedByCall)
    {
        return candidate.eligible &&
               (candidate.explicitIncluded || includedByCall) &&
               !candidate.explicitExcluded &&
               !excludedByCall;
    }

    private String getVMLocation(String globalLocation, String classPackage, ClassNode classNode)
    {
        return switch(config.location)
        {
            case ONE_PACKAGE -> globalLocation;
            case NEW_PACKAGE -> classPackage + "/" + classNode.name + "VM";
            case SAME_PACKAGE_AS_TARGET -> classPackage;
        };
    }

    private boolean shouldSkipMethod(
            ClassNode classNode,
            MethodNode methodNode,
            Set<String> securityManagerClasses,
            Set<String> stackTraceSensitiveMethods)
    {
        return securityManagerClasses.contains(classNode.name) ||
               shouldIgnoreMethod(methodNode) ||
               stackTraceSensitiveMethods.contains(methodKey(methodNode)) ||
               !targetInclude.isMethodMatched(classNode, methodNode) ||
               targetExclude.isMethodMatched(classNode, methodNode);
    }

    private static boolean shouldIgnoreMethod(MethodNode methodNode)
    {
        return "<init>".equals(methodNode.name) ||
               MethodUtils.isAbstract(methodNode) ||
               MethodUtils.isNative(methodNode) ||
               usesStackTraceIntrospection(methodNode);
    }

    private static String methodKey(MethodNode methodNode)
    {
        return methodNode.name + methodNode.desc;
    }

    private static Set<String> stackTraceSensitiveMethods(ClassNode classNode)
    {
        Set<String> sensitiveMethods = new HashSet<>();
        for (MethodNode methodNode : classNode.methods)
        {
            if (usesStackTraceIntrospection(methodNode))
            {
                sensitiveMethods.add(methodKey(methodNode));
            }
        }

        boolean changed;
        do
        {
            changed = false;
            for (MethodNode methodNode : classNode.methods)
            {
                String key = methodKey(methodNode);
                if (sensitiveMethods.contains(key))
                {
                    continue;
                }
                if (callsSensitiveMethod(classNode, methodNode, sensitiveMethods))
                {
                    sensitiveMethods.add(key);
                    changed = true;
                }
            }
        } while (changed);

        return sensitiveMethods;
    }

    private static boolean callsSensitiveMethod(ClassNode classNode, MethodNode methodNode, Set<String> sensitiveMethods)
    {
        for (AbstractInsnNode insn : methodNode.instructions)
        {
            if (!(insn instanceof MethodInsnNode methodInsn))
            {
                continue;
            }
            if (classNode.name.equals(methodInsn.owner) &&
                sensitiveMethods.contains(methodInsn.name + methodInsn.desc))
            {
                return true;
            }
        }
        return false;
    }

    private static Set<String> securityManagerClasses(Collection<ClassNode> classNodes)
    {
        Map<String, String> superNames = new HashMap<>();
        for (ClassNode classNode : classNodes)
        {
            superNames.put(classNode.name, classNode.superName);
        }

        Set<String> securityManagers = new HashSet<>();
        boolean changed;
        do
        {
            changed = false;
            for (ClassNode classNode : classNodes)
            {
                if (securityManagers.contains(classNode.name))
                {
                    continue;
                }
                String superName = classNode.superName;
                if ("java/lang/SecurityManager".equals(superName) || securityManagers.contains(superName))
                {
                    securityManagers.add(classNode.name);
                    changed = true;
                    continue;
                }
                while (superNames.containsKey(superName))
                {
                    superName = superNames.get(superName);
                    if ("java/lang/SecurityManager".equals(superName) || securityManagers.contains(superName))
                    {
                        securityManagers.add(classNode.name);
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);

        return securityManagers;
    }

    private static boolean usesStackTraceIntrospection(MethodNode methodNode)
    {
        for (AbstractInsnNode insn : methodNode.instructions)
        {
            if (!(insn instanceof MethodInsnNode methodInsn))
            {
                continue;
            }
            if ("java/lang/Throwable".equals(methodInsn.owner) &&
                "getStackTrace".equals(methodInsn.name) &&
                "()[Ljava/lang/StackTraceElement;".equals(methodInsn.desc))
            {
                return true;
            }
            if ("java/lang/Thread".equals(methodInsn.owner) &&
                "getStackTrace".equals(methodInsn.name) &&
                "()[Ljava/lang/StackTraceElement;".equals(methodInsn.desc))
            {
                return true;
            }
            if (methodInsn.owner.startsWith("java/lang/StackWalker"))
            {
                return true;
            }
        }
        return false;
    }

    private record MethodId(String owner, String name, String desc)
    {
        private static MethodId of(ClassNode owner, MethodNode method)
        {
            return new MethodId(owner.name, method.name, method.desc);
        }
    }

    private record MethodCandidate(
            ClassNode owner,
            MethodNode method,
            MethodId id,
            boolean eligible,
            boolean explicitIncluded,
            boolean explicitExcluded,
            BytecodeVMConfig methodConfig,
            SdkCallPolicy callPolicy)
    {
    }

    private record GeneratorProfile(
            VMStructure structure,
            int superInstructionMaxHandlers,
            int superInstructionMinFrequency)
    {
        private static GeneratorProfile of(BytecodeVMConfig methodConfig)
        {
            return new GeneratorProfile(
                    methodConfig.vmStructure,
                    methodConfig.superInstructionMaxHandlers,
                    methodConfig.superInstructionMinFrequency);
        }

        private BytecodeVMConfig apply(BytecodeVMConfig base)
        {
            return base.toBuilder()
                    .vmStructure(structure)
                    .superInstructionMaxHandlers(superInstructionMaxHandlers)
                    .superInstructionMinFrequency(superInstructionMinFrequency)
                    .build();
        }
    }

    private record GeneratorGroupKey(String scope, GeneratorProfile profile)
    {
    }

    private static class CliProgress implements AutoCloseable
    {
        private static final String CLEAR_LINE = "\u001B[2K";
        private static final int BAR_WIDTH = 22;
        private static final int MAX_TITLE_LENGTH = 28;
        private static final int MAX_STATUS_LENGTH = 18;

        private final String title;
        private final boolean enabled;
        private long lastRenderNanos;
        private boolean closed;

        private CliProgress(String title)
        {
            this.title = title;
            this.enabled = System.console() != null;
        }

        private void update(int completed, int total, String status)
        {
            if (!enabled || closed)
            {
                return;
            }

            int safeTotal = Math.max(total, 1);
            int safeCompleted = Math.max(0, Math.min(completed, safeTotal));
            long now = System.nanoTime();
            boolean edgeUpdate = safeCompleted == 0 || safeCompleted == safeTotal;
            if (!edgeUpdate && now - lastRenderNanos < 50_000_000L)
            {
                return;
            }

            int filled = (int) ((safeCompleted * (long) BAR_WIDTH) / safeTotal);
            int percent = (int) ((safeCompleted * 100L) / safeTotal);

            StringBuilder bar = new StringBuilder(BAR_WIDTH);
            for (int i = 0; i < BAR_WIDTH; i++)
            {
                bar.append(i < filled ? '=' : ' ');
            }

            String line = String.format(
                    Locale.ROOT,
                    "Virtualizing VM %s [%s] %3d%% %d/%d %s",
                    truncate(title, MAX_TITLE_LENGTH),
                    bar,
                    percent,
                    safeCompleted,
                    safeTotal,
                    truncate(status, MAX_STATUS_LENGTH));

            System.out.print("\r" + CLEAR_LINE + line);
            System.out.flush();
            lastRenderNanos = now;
        }

        @Override
        public void close()
        {
            if (!closed)
            {
                if (enabled)
                {
                    System.out.print("\r" + CLEAR_LINE + "\r");
                    System.out.flush();
                }
                closed = true;
            }
        }

        private static String truncate(String value, int maxLength)
        {
            if (value == null)
            {
                return "";
            }
            if (value.length() <= maxLength)
            {
                return value;
            }
            if (maxLength <= 3)
            {
                return value.substring(0, maxLength);
            }
            return value.substring(0, maxLength - 3) + "...";
        }
    }
}
