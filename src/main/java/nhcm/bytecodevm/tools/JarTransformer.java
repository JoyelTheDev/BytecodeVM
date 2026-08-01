package nhcm.bytecodevm.tools;

import nhcm.bytecodevm.utils.LogColors;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class JarTransformer
{
    private static final Logger logger = LoggerFactory.getLogger(JarTransformer.class);

    public interface Transformer
    {
        void transform(JarContext context);
    }

    public static class JarContext
    {
        public final Map<String, ClassNode> classes = new LinkedHashMap<>();
        public final Map<String, byte[]> resources = new LinkedHashMap<>();

        public ClassNode getClass(String name)
        {
            name = name.replace('.', '/');
            return classes.get(name);
        }

        public boolean hasClass(String name)
        {
            name = name.replace('.', '/');
            return classes.containsKey(name);
        }

        public void addClass(ClassNode cn)
        {
            classes.put(cn.name, cn);
        }

        public void removeClass(String name)
        {
            name = name.replace('.', '/');
            classes.remove(name);
        }

        public void addResource(String name, byte[] bytes)
        {
            resources.put(name, bytes);
        }

        public void removeResource(String name)
        {
            resources.remove(name);
        }
    }

    public static void transformJar(File input, File output, Transformer transformer) throws IOException
    {
        long start = System.nanoTime();
        JarContext context = readJar(input);

        transformer.transform(context);

        writeJar(output, context);
        logger.debug(
                "Transformed {} -> {} in {} ms",
                input.getAbsolutePath(),
                output.getAbsolutePath(),
                (System.nanoTime() - start) / 1_000_000L);
    }

    public static JarContext readJar(File input) throws IOException
    {
        logger.info("{}", LogColors.jarRead("Reading jar: " + LogColors.path(input.getAbsolutePath())));
        JarContext context = new JarContext();

        try (JarFile jarFile = new JarFile(input))
        {
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements())
            {
                JarEntry entry = entries.nextElement();

                if (entry.isDirectory())
                {
                    continue;
                }

                try (InputStream is = jarFile.getInputStream(entry))
                {
                    byte[] bytes = readAllBytes(is);

                    if (entry.getName().endsWith(".class"))
                    {
                        ClassReader cr = new ClassReader(bytes);

                        ClassNode cn = new ClassNode();

                        cr.accept(
                                cn,
                                ClassReader.SKIP_FRAMES
                        );

                        context.classes.put(cn.name, cn);
                    }
                    else
                    {
                        context.resources.put(entry.getName(), bytes);
                    }
                }
            }
        }

        logger.info("{}", LogColors.success(
                "Read jar " +
                        LogColors.path(input.getAbsolutePath()) +
                        ": " +
                        LogColors.strong(context.classes.size()) +
                        " class(es), " +
                        LogColors.strong(context.resources.size()) +
                        " resource(s)"));
        return context;
    }

    public static void writeJar(File output, JarContext context) throws IOException
    {
        logger.info("{}", LogColors.jarWrite("Writing jar: " + LogColors.path(output.getAbsolutePath())));
        int classCount = 0;
        int resourceCount = 0;
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(output)))
        {
            Set<String> written = new HashSet<>();

            for (ClassNode cn : context.classes.values())
            {
                String entryName = cn.name + ".class";

                if (!written.add(entryName))
                {
                    continue;
                }

                jos.putNextEntry(reproducibleEntry(entryName));

                jos.write(toBytes(cn, context));
                jos.closeEntry();
                classCount++;
            }

            for (Map.Entry<String, byte[]> resource : context.resources.entrySet())
            {
                String name = resource.getKey();

                if (!written.add(name))
                {
                    continue;
                }

                jos.putNextEntry(reproducibleEntry(name));
                jos.write(resource.getValue());
                jos.closeEntry();
                resourceCount++;
            }
        }
        logger.info("{}", LogColors.success(
                "Wrote jar " +
                        LogColors.path(output.getAbsolutePath()) +
                        ": " +
                        LogColors.strong(classCount) +
                        " class(es), " +
                        LogColors.strong(resourceCount) +
                        " resource(s)"));
    }

    public static byte[] toBytes(ClassNode classNode, JarContext context)
    {
        ClassWriter cw = new ContextClassWriter(
                ClassWriter.COMPUTE_FRAMES |
                ClassWriter.COMPUTE_MAXS,
                context
        );
        classNode.accept(cw);
        return cw.toByteArray();
    }

    private static byte[] readAllBytes(InputStream is) throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        byte[] buffer = new byte[8192];
        int len;

        while ((len = is.read(buffer)) != -1)
        {
            baos.write(buffer, 0, len);
        }

        return baos.toByteArray();
    }

    private static JarEntry reproducibleEntry(String name)
    {
        JarEntry entry = new JarEntry(name);
        entry.setTime(0L);
        return entry;
    }

    private static class ContextClassWriter extends ClassWriter
    {
        private static final String OBJECT = "java/lang/Object";
        private static final String CLONEABLE = "java/lang/Cloneable";
        private static final String SERIALIZABLE = "java/io/Serializable";

        private final JarContext context;
        private final Map<String, ClassInfo> infoCache = new HashMap<>();

        private ContextClassWriter(int flags, JarContext context)
        {
            super(flags);
            this.context = context;
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2)
        {
            if (type1.equals(type2))
            {
                return type1;
            }
            if (isArray(type1) || isArray(type2))
            {
                return getCommonArraySuperClass(type1, type2);
            }
            if (isAssignableFrom(type1, type2))
            {
                return type1;
            }
            if (isAssignableFrom(type2, type1))
            {
                return type2;
            }
            ClassInfo info1 = getClassInfo(type1);
            ClassInfo info2 = getClassInfo(type2);
            if (info1 == null || info2 == null || info1.isInterface || info2.isInterface)
            {
                return OBJECT;
            }

            String superName = info1.superName;
            while (superName != null)
            {
                if (isAssignableFrom(superName, type2))
                {
                    return superName;
                }
                ClassInfo superInfo = getClassInfo(superName);
                if (superInfo == null)
                {
                    return OBJECT;
                }
                superName = superInfo.superName;
            }
            return OBJECT;
        }

        private String getCommonArraySuperClass(String type1, String type2)
        {
            if (!isArray(type1) || !isArray(type2))
            {
                return OBJECT;
            }
            ArrayType array1 = parseArray(type1);
            ArrayType array2 = parseArray(type2);
            if (!array1.isObjectElement || !array2.isObjectElement)
            {
                return OBJECT;
            }
            if (array1.dimensions != array2.dimensions)
            {
                return OBJECT;
            }

            String elementSuper = getCommonSuperClass(array1.elementType, array2.elementType);
            return "[".repeat(array1.dimensions) + "L" + elementSuper + ";";
        }

        private boolean isAssignableFrom(String target, String source)
        {
            if (target.equals(source) || OBJECT.equals(target))
            {
                return true;
            }
            if (isArray(target) || isArray(source))
            {
                return isArrayAssignableFrom(target, source);
            }

            ClassInfo sourceInfo = getClassInfo(source);
            if (sourceInfo == null)
            {
                return false;
            }
            if (sourceInfo.interfaces.contains(target))
            {
                return true;
            }
            for (String interfaceName : sourceInfo.interfaces)
            {
                if (isAssignableFrom(target, interfaceName))
                {
                    return true;
                }
            }
            String superName = sourceInfo.superName;
            while (superName != null)
            {
                if (target.equals(superName))
                {
                    return true;
                }
                ClassInfo superInfo = getClassInfo(superName);
                if (superInfo == null)
                {
                    return false;
                }
                if (superInfo.interfaces.contains(target))
                {
                    return true;
                }
                for (String interfaceName : superInfo.interfaces)
                {
                    if (isAssignableFrom(target, interfaceName))
                    {
                        return true;
                    }
                }
                superName = superInfo.superName;
            }
            return false;
        }

        private boolean isArrayAssignableFrom(String target, String source)
        {
            if (!isArray(source))
            {
                return false;
            }
            if (OBJECT.equals(target) || CLONEABLE.equals(target) || SERIALIZABLE.equals(target))
            {
                return true;
            }
            if (!isArray(target))
            {
                return false;
            }

            ArrayType targetArray = parseArray(target);
            ArrayType sourceArray = parseArray(source);
            return targetArray.dimensions == sourceArray.dimensions &&
                   targetArray.isObjectElement &&
                   sourceArray.isObjectElement &&
                   isAssignableFrom(targetArray.elementType, sourceArray.elementType);
        }

        private ClassInfo getClassInfo(String name)
        {
            return infoCache.computeIfAbsent(name, this::loadClassInfo);
        }

        private ClassInfo loadClassInfo(String name)
        {
            ClassNode classNode = context.getClass(name);
            if (classNode != null)
            {
                return new ClassInfo(
                        classNode.name,
                        classNode.superName,
                        classNode.interfaces,
                        (classNode.access & Opcodes.ACC_INTERFACE) != 0
                );
            }

            try
            {
                Class<?> clazz = Class.forName(name.replace('/', '.'), false, getClass().getClassLoader());
                Class<?> superClass = clazz.getSuperclass();
                List<String> interfaces = new ArrayList<>();
                for (Class<?> iface : clazz.getInterfaces())
                {
                    interfaces.add(iface.getName().replace('.', '/'));
                }
                return new ClassInfo(
                        name,
                        superClass == null ? null : superClass.getName().replace('.', '/'),
                        interfaces,
                        clazz.isInterface()
                );
            }
            catch (ClassNotFoundException | LinkageError ignored)
            {
                return null;
            }
        }

        private static boolean isArray(String name)
        {
            return name.startsWith("[");
        }

        private static ArrayType parseArray(String name)
        {
            int dimensions = 0;
            while (dimensions < name.length() && name.charAt(dimensions) == '[')
            {
                dimensions++;
            }
            boolean isObjectElement = dimensions < name.length() && name.charAt(dimensions) == 'L';
            String elementType = isObjectElement
                    ? name.substring(dimensions + 1, name.length() - 1)
                    : name.substring(dimensions);
            return new ArrayType(dimensions, elementType, isObjectElement);
        }
    }

    private record ClassInfo(String name, String superName, List<String> interfaces, boolean isInterface)
    {
    }

    private record ArrayType(int dimensions, String elementType, boolean isObjectElement)
    {
    }
}
