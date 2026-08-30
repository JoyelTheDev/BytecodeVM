package nhcm.bytecodevm.sdk.watermark;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Binary codec shared by BytecodeVM, its CLI, and the public SDK. */
public final class WatermarkCodec
{
    private static final int MAGIC = 0x42564D57;
    private static final int VERSION = 1;
    private static final int DIGEST_LENGTH = 32;
    private static final int MAX_ENTRIES = 256;
    private static final int MAX_TEXT_BYTES = 65_536;
    private static final int MAX_PAYLOAD_BYTES = 1_048_576;

    private WatermarkCodec()
    {
    }

    public static byte[] encode(Map<String, String> values) throws IOException
    {
        if (values == null)
        {
            throw new NullPointerException("values");
        }
        if (values.size() > MAX_ENTRIES)
        {
            throw new IOException("Too many watermark entries: " + values.size());
        }

        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        DataOutputStream payload = new DataOutputStream(payloadBytes);
        Map<String, String> sorted = new TreeMap<String, String>(values);
        payload.writeInt(sorted.size());
        for (Map.Entry<String, String> entry : sorted.entrySet())
        {
            writeText(payload, entry.getKey(), "key");
            writeText(payload, entry.getValue(), "value");
        }
        payload.flush();
        byte[] body = payloadBytes.toByteArray();
        if (body.length > MAX_PAYLOAD_BYTES)
        {
            throw new IOException("Watermark payload is too large: " + body.length);
        }

        ByteArrayOutputStream encodedBytes = new ByteArrayOutputStream();
        DataOutputStream encoded = new DataOutputStream(encodedBytes);
        encoded.writeInt(MAGIC);
        encoded.writeShort(VERSION);
        encoded.writeInt(body.length);
        encoded.write(body);
        encoded.write(sha256(body));
        encoded.flush();
        return encodedBytes.toByteArray();
    }

    public static WatermarkInfo decode(byte[] encoded) throws IOException
    {
        if (encoded == null)
        {
            throw new NullPointerException("encoded");
        }
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
        if (input.readInt() != MAGIC)
        {
            throw new IOException("Not a BytecodeVM watermark");
        }
        int version = input.readUnsignedShort();
        if (version != VERSION)
        {
            throw new IOException("Unsupported BytecodeVM watermark version: " + version);
        }
        int payloadLength = input.readInt();
        if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_BYTES)
        {
            throw new IOException("Invalid watermark payload length: " + payloadLength);
        }
        if (input.available() != payloadLength + DIGEST_LENGTH)
        {
            throw new IOException("Truncated or extended watermark data");
        }
        byte[] payload = new byte[payloadLength];
        input.readFully(payload);
        byte[] storedDigest = new byte[DIGEST_LENGTH];
        input.readFully(storedDigest);
        if (!MessageDigest.isEqual(storedDigest, sha256(payload)))
        {
            throw new IOException("BytecodeVM watermark checksum mismatch");
        }

        DataInputStream fields = new DataInputStream(new ByteArrayInputStream(payload));
        int count = fields.readInt();
        if (count < 0 || count > MAX_ENTRIES)
        {
            throw new IOException("Invalid watermark entry count: " + count);
        }
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (int index = 0; index < count; index++)
        {
            String key = readText(fields, "key");
            String value = readText(fields, "value");
            if (values.put(key, value) != null)
            {
                throw new IOException("Duplicate watermark key: " + key);
            }
        }
        if (fields.available() != 0)
        {
            throw new IOException("Unexpected data in watermark payload");
        }
        return new WatermarkInfo(values);
    }

    public static byte[] sha256(byte[] bytes)
    {
        try
        {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void writeText(DataOutputStream output, String text, String label) throws IOException
    {
        if (text == null)
        {
            throw new IOException("Watermark " + label + " cannot be null");
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES)
        {
            throw new IOException("Watermark " + label + " is too long");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readText(DataInputStream input, String label) throws IOException
    {
        int length = input.readInt();
        if (length < 0 || length > MAX_TEXT_BYTES || length > input.available())
        {
            throw new IOException("Invalid watermark " + label + " length: " + length);
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
