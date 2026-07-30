package nhcm.bytecodevm;

import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.generator.Obfuscator;
import nhcm.bytecodevm.utils.LogColors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public class BytecodeVM
{
    private static final Logger logger = LoggerFactory.getLogger(BytecodeVM.class);
    private static final AtomicBoolean terminating = new AtomicBoolean(false);

    private static final String version = "1.4.0";

    private static final String defaultConfig = """
            {
              "input": "./input.jar",
              "output": "./output.jar",
              "createMode": "ONE_FOR_ALL", // ONE_FOR_ALL, PER_METHOD, PER_CLASS, PER_PACKAGE
              "location": "ONE_PACKAGE", // SAME_PACKAGE_AS_TARGET, NEW_PACKAGE, ONE_PACKAGE
              "mutateMode": "ALL_RANDOM_INT", // ALL_RANDOM_INT, ALL_RESORT, ALL_AUTO_CHOOSE, NO_CHANGE
              "renameMode": "DISABLE", // ENABLE, DISABLE
              "interpretMode": "SAVE_ONLY_REQUIRED_INSTRUCTION", // SAVE_ALL_INSTRUCTION, SAVE_ONLY_REQUIRED_INSTRUCTION
              "protectCodePool": true,
              "virtualizeInstructionAddresses": true,
              "encryptOperands": true,
              "perMethodOpcodeMap": true,
              "shuffleConstants": true,
              "bindConstantsToOperands": true,
              "splitCodeStreams": true,
              "shuffleInstructionBlocks": true,
              "obfuscateDispatch": true,
              "dynamicCodePoolBuild": true,
              "dynamicStateKey": true,
              "virtualControlFlowGraph": true,
              "constantFix": true,
              "includeMethodsCalledWithin": false,
              "excludeMethodsCalledWithin": false,
              "virtualizeInvocationBridges": true,
              "vmIntegrityCheck": true,
              "vmIntegrityCheckRatio": 1.0,
              "superInstruction": true,
              "superInstructionCombineRange": [2, 5],
              "superInstructionMode": "HYBRID", // RANDOM, PATTERN, HYBRID
              "superInstructionMaxHandlers": 128,
              "superInstructionMinFrequency": 2,
              "vmCount": 4,
              "includes": {
                "all": ["*", "* *(*)*"]
              },
              "exclusions": {
                "all": ["* <init>(*)V"]
              }
            }
            """;

    private static final String usage = """
            Usage:
            java -jar BytecodeVM.jar --config <config file>
            java -jar BytecodeVM.jar --defaultconfig
            java -jar BytecodeVM.jar --defaultrun <input jar>
            """;

    private static final String asciiArt = """
            ██████╗ ██╗   ██╗████████╗███████╗ ██████╗ ██████╗ ██████╗ ███████╗██╗   ██╗███╗   ███╗
            ██╔══██╗╚██╗ ██╔╝╚══██╔══╝██╔════╝██╔════╝██╔═══██╗██╔══██╗██╔════╝██║   ██║████╗ ████║
            ██████╔╝ ╚████╔╝    ██║   █████╗  ██║     ██║   ██║██║  ██║█████╗  ██║   ██║██╔████╔██║
            ██╔══██╗  ╚██╔╝     ██║   ██╔══╝  ██║     ██║   ██║██║  ██║██╔══╝  ╚██╗ ██╔╝██║╚██╔╝██║
            ██████╔╝   ██║      ██║   ███████╗╚██████╗╚██████╔╝██████╔╝███████╗ ╚████╔╝ ██║ ╚═╝ ██║
            ╚═════╝    ╚═╝      ╚═╝   ╚══════╝ ╚═════╝ ╚═════╝ ╚═════╝ ╚══════╝  ╚═══╝  ╚═╝     ╚═╝
            
            By NHCM, Version %s
            
            MUST READ:
            This obfuscator is intended for demonstration purposes only and is not suitable for production use.
            It can make your program hundreds of times slower, while the quality of its protection is not guaranteed.
            This obfuscator may not even provide protection comparable to existing virtualization tools such as V*P or The*ida, or even basic bytecode-to-native obfuscation tools such as JN*C.
            Its purpose is to demonstrate the concept of bytecode virtualization.
            """.formatted(version);

    public static void main(String[] args) throws InterruptedException
    {
        installTerminationHandlers();
        System.out.println(asciiArt);
        Thread.sleep(1000);
        int exitCode = run(args);
        if(exitCode != 0)
        {
            System.exit(exitCode);
        }
    }

    private static int run(String[] args)
    {
        try
        {
            if(args.length == 1)
            {
                if(args[0].equals("--defaultconfig"))
                {
                    Files.writeString(Path.of("defaultconfig.json"), defaultConfig);
                    logger.info("{}", LogColors.success("Default config saved to ./defaultconfig.json"));
                    return 0;
                } else
                {
                    logger.info("{}", usage);
                    return 1;
                }
            } else if(args.length == 2)
            {
                if(args[0].equals("--config"))
                {
                    Path configFile = Path.of(args[1]);
                    if(!Files.exists(configFile))
                    {
                        logger.error("{}", LogColors.error("Config file does not exist: " + LogColors.path(configFile.toAbsolutePath())));
                        return 1;
                    }
                    logger.info("{}", LogColors.lifecycle("Starting BytecodeVM with config " + LogColors.path(configFile.toAbsolutePath())));
                    Obfuscator obfuscator = new Obfuscator(BytecodeVMConfig.parse(configFile));
                    obfuscator.obfuscate();
                    logger.info("{}", LogColors.success("Program exiting"));
                    return 0;
                } else if(args[0].equals("--defaultrun"))
                {
                    logger.info("{}", LogColors.lifecycle("Starting BytecodeVM with default config"));
                    String inputFile = args[1];
                    String outputFile = inputFile.replaceAll("\\.jar$", "") + "-bytecodevm.jar";
                    Obfuscator obfuscator = new Obfuscator(BytecodeVMConfig.parse(defaultConfig, inputFile, outputFile));
                    obfuscator.obfuscate();
                    logger.info("{}", LogColors.success("Program exiting"));
                    return 0;
                } else
                {
                    logger.info("{}", usage);
                    return 1;
                }
            } else
            {
                logger.info("{}", usage);
                return 1;
            }
        }
        catch (Exception e)
        {
            logger.error(LogColors.error("Program failed"), e);
            return 1;
        }
    }

    private static void installTerminationHandlers()
    {
        registerSignalHandler("INT", 130, "Ctrl+C");
        registerSignalHandler("TERM", 143, "termination signal");
    }

    private static void registerSignalHandler(String signalName, int exitCode, String reason)
    {
        try
        {
            Class<?> signalClass = Class.forName("sun.misc.Signal");
            Class<?> signalHandlerClass = Class.forName("sun.misc.SignalHandler");
            Object signal = signalClass.getConstructor(String.class).newInstance(signalName);
            InvocationHandler handler = (proxy, method, args) ->
            {
                if ("handle".equals(method.getName()))
                {
                    terminate(exitCode, reason);
                    return null;
                }
                if ("toString".equals(method.getName()))
                {
                    return "BytecodeVM " + signalName + " handler";
                }
                if ("hashCode".equals(method.getName()))
                {
                    return System.identityHashCode(proxy);
                }
                if ("equals".equals(method.getName()))
                {
                    return proxy == args[0];
                }
                return null;
            };
            Object signalHandler = Proxy.newProxyInstance(
                    BytecodeVM.class.getClassLoader(),
                    new Class<?>[]{signalHandlerClass},
                    handler);
            signalClass
                    .getMethod("handle", signalClass, signalHandlerClass)
                    .invoke(null, signal, signalHandler);
        }
        catch (ReflectiveOperationException | LinkageError | SecurityException ignored)
        {
            // If sun.misc.Signal is unavailable, the JVM default signal behavior still exits.
        }
    }

    private static void terminate(int exitCode, String reason)
    {
        if (terminating.compareAndSet(false, true))
        {
            System.err.print("\r\u001B[2K\r");
            System.err.println("BytecodeVM interrupted by " + reason + ", exiting.");
            System.err.flush();
        }
        Runtime.getRuntime().halt(exitCode);
    }
}
