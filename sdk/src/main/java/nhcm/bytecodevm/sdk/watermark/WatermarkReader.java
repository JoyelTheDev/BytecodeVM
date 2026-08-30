package nhcm.bytecodevm.sdk.watermark;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Statically reads embedded watermarks without loading or executing target classes. */
public final class WatermarkReader
{
    private static final int CLASS_MAGIC = 0xCAFEBABE;

    private WatermarkReader()
    {
    }

    public static WatermarkInfo read(Path jar) throws IOException
    {
        return read(jar.toFile());
    }

    public static boolean isWatermarked(Path jar)
    {
        try
        {
            read(jar);
            return true;
        }
        catch (IOException ignored)
        {
            return false;
        }
    }

    public static WatermarkInfo read(File jar) throws IOException
    {
        JarFile file = new JarFile(jar);
        try
        {
            WatermarkInfo found = null;
            java.util.Enumeration<JarEntry> entries = file.entries();
            while (entries.hasMoreElements())
            {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class"))
                {
                    continue;
                }
                InputStream input = file.getInputStream(entry);
                WatermarkInfo candidate;
                try
                {
                    candidate = readClass(input);
                }
                finally
                {
                    input.close();
                }
                if (candidate == null)
                {
                    continue;
                }
                if (found != null && !found.values().equals(candidate.values()))
                {
                    throw new IOException("Conflicting BytecodeVM watermarks were found");
                }
                found = candidate;
            }
            if (found == null)
            {
                throw new IOException("BytecodeVM watermark is missing");
            }
            return found;
        }
        finally
        {
            file.close();
        }
    }

    public static WatermarkInfo readClass(InputStream classFile) throws IOException
    {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(readAll(classFile)));
        if (input.readInt() != CLASS_MAGIC)
        {
            throw new IOException("Invalid class file");
        }
        input.readUnsignedShort();
        input.readUnsignedShort();
        int count = input.readUnsignedShort();
        Object[] constants = new Object[count];
        int[] stringIndexes = new int[count];
        boolean marker = false;
        for (int index = 1; index < count; index++)
        {
            int tag = input.readUnsignedByte();
            switch (tag)
            {
                case 1:
                {
                    int length = input.readUnsignedShort();
                    byte[] text = new byte[length];
                    input.readFully(text);
                    constants[index] = new String(text, StandardCharsets.UTF_8);
                    break;
                }
                case 3:
                case 4:
                    input.skipBytes(4);
                    break;
                case 5:
                case 6:
                {
                    long value = input.readLong();
                    marker |= tag == 5 && value == WatermarkCapsule.BYTECODE_MARKER;
                    index++;
                    break;
                }
                case 7:
                case 16:
                case 19:
                case 20:
                    input.skipBytes(2);
                    break;
                case 8:
                    stringIndexes[index] = input.readUnsignedShort();
                    break;
                case 9:
                case 10:
                case 11:
                case 12:
                case 17:
                case 18:
                    input.skipBytes(4);
                    break;
                case 15:
                    input.skipBytes(3);
                    break;
                default:
                    throw new IOException("Unsupported class constant tag: " + tag);
            }
        }
        if (!marker)
        {
            return null;
        }

        List<String> strings = new ArrayList<String>();
        for (int stringIndex : stringIndexes)
        {
            if (stringIndex > 0 && stringIndex < constants.length && constants[stringIndex] instanceof String)
            {
                strings.add((String) constants[stringIndex]);
            }
        }
        for (String string : strings)
        {
            try
            {
                return WatermarkCodec.decode(WatermarkCapsule.decode(string));
            }
            catch (IOException ignored)
            {
                // Most strings in the carrier are unrelated to the capsule.
            }
        }
        throw new IOException("BytecodeVM watermark carrier is damaged");
    }

    private static byte[] readAll(InputStream input) throws IOException
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1)
        {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
