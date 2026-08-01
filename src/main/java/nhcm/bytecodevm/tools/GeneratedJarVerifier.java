package nhcm.bytecodevm.tools;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class GeneratedJarVerifier
{
    private GeneratedJarVerifier()
    {
    }

    public static void main(String[] arguments) throws Exception
    {
        if (arguments.length != 1)
        {
            throw new IllegalArgumentException("Usage: GeneratedJarVerifier <jar>");
        }
        verify(Path.of(arguments[0]));
    }

    public static void verify(Path jarPath) throws Exception
    {
        List<String> failures = new ArrayList<>();
        URL jarUrl = jarPath.toAbsolutePath().toUri().toURL();
        try (JarFile jar = new JarFile(jarPath.toFile());
             URLClassLoader loader = new URLClassLoader(
                     new URL[]{jarUrl},
                     GeneratedJarVerifier.class.getClassLoader()))
        {
            var entries = jar.entries();
            while (entries.hasMoreElements())
            {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class"))
                {
                    continue;
                }
                StringWriter diagnostics = new StringWriter();
                try (PrintWriter output = new PrintWriter(diagnostics))
                {
                    CheckClassAdapter.verify(
                            new ClassReader(jar.getInputStream(entry)),
                            loader,
                            false,
                            output);
                }
                if (!diagnostics.toString().isBlank())
                {
                    failures.add(entry.getName() + System.lineSeparator() + diagnostics);
                }
            }
        }
        if (!failures.isEmpty())
        {
            throw new IllegalStateException(
                    "ASM verification failed for " + jarPath + System.lineSeparator() +
                    String.join(System.lineSeparator(), failures));
        }
    }
}
