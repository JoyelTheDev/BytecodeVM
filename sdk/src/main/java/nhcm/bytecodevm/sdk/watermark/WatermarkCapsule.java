package nhcm.bytecodevm.sdk.watermark;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-GCM capsule embedded directly in a generated method's bytecode. */
public final class WatermarkCapsule
{
    public static final long BYTECODE_MARKER = 0x42564D57434F4445L;
    public static final int KEY_LENGTH = 16;
    public static final int NONCE_LENGTH = 12;
    public static final int HEADER_LENGTH = KEY_LENGTH + NONCE_LENGTH;
    private static final SecureRandom RANDOM = new SecureRandom();

    private WatermarkCapsule()
    {
    }

    public static String encode(byte[] plain) throws IOException
    {
        byte[] key = new byte[KEY_LENGTH];
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(key);
        RANDOM.nextBytes(nonce);
        try
        {
            byte[] cipherText = cipher(Cipher.ENCRYPT_MODE, key, nonce).doFinal(plain);
            byte[] capsule = new byte[HEADER_LENGTH + cipherText.length];
            System.arraycopy(key, 0, capsule, 0, key.length);
            System.arraycopy(nonce, 0, capsule, key.length, nonce.length);
            System.arraycopy(cipherText, 0, capsule, HEADER_LENGTH, cipherText.length);
            return Base64.getEncoder().withoutPadding().encodeToString(capsule);
        }
        catch (GeneralSecurityException exception)
        {
            throw new IOException("Cannot encrypt BytecodeVM watermark", exception);
        }
    }

    public static byte[] decode(String encoded) throws IOException
    {
        final byte[] capsule;
        try
        {
            capsule = Base64.getDecoder().decode(encoded);
        }
        catch (IllegalArgumentException exception)
        {
            throw new IOException("Invalid BytecodeVM watermark capsule", exception);
        }
        if (capsule.length <= HEADER_LENGTH)
        {
            throw new IOException("Truncated BytecodeVM watermark capsule");
        }
        byte[] key = Arrays.copyOfRange(capsule, 0, KEY_LENGTH);
        byte[] nonce = Arrays.copyOfRange(capsule, KEY_LENGTH, HEADER_LENGTH);
        byte[] cipherText = Arrays.copyOfRange(capsule, HEADER_LENGTH, capsule.length);
        try
        {
            return cipher(Cipher.DECRYPT_MODE, key, nonce).doFinal(cipherText);
        }
        catch (GeneralSecurityException exception)
        {
            throw new IOException("BytecodeVM watermark capsule authentication failed", exception);
        }
    }

    private static Cipher cipher(int mode, byte[] key, byte[] nonce)
            throws GeneralSecurityException
    {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                mode,
                new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, nonce));
        return cipher;
    }
}
