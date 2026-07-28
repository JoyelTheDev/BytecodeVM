package nhcm.bytecodevm.Generator;

import nhcm.bytecodevm.Config.BytecodeVMConfig;
import nhcm.bytecodevm.Config.TargetMatcher;
import nhcm.bytecodevm.Data.VirtualizationResult;
import nhcm.bytecodevm.Generator.GlobalClass.MethodFrameGenerator;
import nhcm.bytecodevm.Generator.GlobalClass.VMCodePoolGenerator;
import nhcm.bytecodevm.Generator.GlobalClass.VMProgramGenerator;
import nhcm.bytecodevm.Tools.JarTransformer;
import nhcm.bytecodevm.Tools.OpcMutator;
import nhcm.bytecodevm.Utils.ClassUtils;
import nhcm.bytecodevm.Utils.LogColors;
import nhcm.bytecodevm.Utils.MethodUtils;
import nhcm.bytecodevm.Utils.RandomUtils;
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

    private final MethodFrameGenerator methodFrameGenerator;
    private final ClassNode methodFrameClassNode;
    private final VMProgramGenerator vmProgramGenerator;
    private final ClassNode vmProgramClassNode;
    private final VMCodePoolGenerator vmCodePoolGenerator;
    private final ClassNode vmCodePoolClassNode;

    public Obfuscator(BytecodeVMConfig config)
    {
        this.config = config;
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
        this.methodFrameGenerator = new MethodFrameGenerator("BytecodeVM/MethodFrame");
        this.methodFrameClassNode = methodFrameGenerator.getClassNode();
        this.vmProgramGenerator = new VMProgramGenerator("BytecodeVM/VMProgram");
        this.vmProgramClassNode = vmProgramGenerator.getClassNode();
        this.vmCodePoolGenerator = new VMCodePoolGenerator(
                "BytecodeVM/VMCodePool",
                vmProgramGenerator);
        this.vmCodePoolClassNode = vmCodePoolGenerator.getClassNode();
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
        processJar(context);
        logger.info("{}", LogColors.lifecycle("Adding required VM support classes"));
        context.addClass(methodFrameClassNode);
        context.addClass(vmProgramClassNode);
        context.addClass(vmCodePoolClassNode);
        logger.debug("{}", LogColors.success("Added required VM support classes"));
        List<VMSetGenerator> generators = new ArrayList<>(this.VMSetGenerators);
        for(VMSetGenerator generator : generators)
        {
            logger.info("{}", LogColors.virtualize(
                    "Virtualizing VM: " +
                            LogColors.strong(generator.vmClassName) +
                            " (" + generator.methodCount() + " method(s))"));
            VirtualizationResult result;
            try (CliProgress progress = new CliProgress(generator.vmClassName))
            {
                result = generator.compile(progress::update);
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
    }

    private void processJar(JarTransformer.JarContext context)
    {
        logger.info("{}", LogColors.scan("Scanning input file for methods to obfuscate"));

        String globalLocation = "BytecodeVM";

        VMSetGenerator allInOneVm = newVMSetGenerator("BytecodeVM", "BytecodeVM");
        List<VMSetGenerator> perClasses = new ArrayList<>();
        List<VMSetGenerator> perMethods = new ArrayList<>();
        Map<String, VMSetGenerator> perPackage = new LinkedHashMap<>();

        Set<String> securityManagerClasses = securityManagerClasses(context.classes.values());

        int matchedMethods = 0;

        for(ClassNode classNode : context.classes.values())
        {
            if(!targetInclude.isClassMatched(classNode) || targetExclude.isClassMatched(classNode))
            {
                continue;
            }

            String classPackage = ClassUtils.getPackageName(classNode);
            String vmLocation = getVMLocation(globalLocation, classPackage, classNode);

            VMSetGenerator perClass = null;

            if(config.createMode == BytecodeVMConfig.VMCreateMode.PER_CLASS)
            {
                perClass = newVMSetGenerator(
                        ClassUtils.getSimpleName(classNode) + "$VM",
                        vmLocation
                );
            }

            Set<String> stackTraceSensitiveMethods = stackTraceSensitiveMethods(classNode);

            for(MethodNode methodNode : classNode.methods)
            {
                if(shouldSkipMethod(
                        classNode,
                        methodNode,
                        securityManagerClasses,
                        stackTraceSensitiveMethods))
                {
                    continue;
                }

                matchedMethods++;

                switch(config.createMode)
                {
                    case PER_CLASS ->
                    {
                        perClass.addMethod(methodNode, classNode);
                    }

                    case PER_METHOD ->
                    {
                        VMSetGenerator perMethod = newVMSetGenerator(
                                ClassUtils.getSimpleName(classNode) + "$" + methodNode.name + "$VM",
                                vmLocation
                        );

                        perMethod.addMethod(methodNode, classNode);
                        perMethods.add(perMethod);
                    }

                    case PER_PACKAGE ->
                    {
                        VMSetGenerator generator = perPackage.computeIfAbsent(
                                classPackage,
                                ignored -> newVMSetGenerator(classPackage + "$VM", classPackage)
                        );

                        generator.addMethod(methodNode, classNode);
                    }

                    case ONE_FOR_ALL ->
                    {
                        allInOneVm.addMethod(methodNode, classNode);
                    }
                }
            }

            if(config.createMode == BytecodeVMConfig.VMCreateMode.PER_CLASS && perClass != null && perClass.hasMethods())
            {
                perClasses.add(perClass);
            }
        }

        switch(config.createMode)
        {
            case PER_CLASS -> VMSetGenerators.addAll(perClasses);
            case PER_METHOD -> VMSetGenerators.addAll(perMethods);
            case PER_PACKAGE -> VMSetGenerators.addAll(perPackage.values());
            case ONE_FOR_ALL ->
            {
                if(allInOneVm.hasMethods())
                {
                    VMSetGenerators.add(allInOneVm);
                }
            }
        }

        logger.info("{}", LogColors.scan(
                "Scanned input file, found " +
                LogColors.strong(matchedMethods) +
                " method(s) across " +
                LogColors.strong(VMSetGenerators.size()) +
                " VM set(s)"
        ));
    }

    private OpcMutator chooseMutator()
    {
        switch (config.mutateMode)
        {
            case ALL_RANDOM_INT:
            {
                return OpcMutator.MutateStrategy.RANDOM_INT.getMutator();
            }
            case ALL_RESORT:
            {
                return OpcMutator.MutateStrategy.RESORT.getMutator();
            }
            case ALL_AUTO_CHOOSE:
            {
                return OpcMutator.fromStrategy(RandomUtils.randomBoolean() ? OpcMutator.MutateStrategy.RANDOM_INT : OpcMutator.MutateStrategy.RESORT);
            }
            default:
            {
                return OpcMutator.MutateStrategy.NONE.getMutator();
            }
        }
    }

    private VMSetGenerator newVMSetGenerator(String name, String location)
    {
        return new VMSetGenerator(
                name,
                location,
                chooseMutator(),
                methodFrameGenerator,
                vmProgramGenerator,
                vmCodePoolGenerator,
                config);
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
