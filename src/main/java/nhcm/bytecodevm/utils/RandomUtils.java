package nhcm.bytecodevm.utils;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

public class RandomUtils
{
    private static final AtomicReference<Random> seededRandom = new AtomicReference<>(null);

    private RandomUtils()
    {
    }

    /** Uses a deterministic source for reproducible debugging builds. */
    public static void useSeed(long seed)
    {
        seededRandom.set(new Random(seed));
    }

    /** Restores non-deterministic generation for normal protection builds. */
    public static void useSecureRandom()
    {
        seededRandom.set(null);
    }

    public static int randomInt()
    {
        Random seeded = seededRandom.get();
        return seeded != null ? seeded.nextInt() : ThreadLocalRandom.current().nextInt();
    }

    public static int randomInt(int bound)
    {
        if (bound <= 0)
        {
            throw new IllegalArgumentException("bound must be positive");
        }

        Random seeded = seededRandom.get();
        return seeded != null ? seeded.nextInt(bound) : ThreadLocalRandom.current().nextInt(bound);
    }

    public static int randomInt(int min, int max)
    {
        if (min > max)
        {
            throw new IllegalArgumentException("min > max");
        }

        if (min == Integer.MIN_VALUE && max == Integer.MAX_VALUE)
        {
            return randomInt();
        }

        Random seeded = seededRandom.get();
        return seeded != null
                ? seeded.nextInt(max - min + 1) + min
                : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public static boolean randomBoolean()
    {
        Random seeded = seededRandom.get();
        return seeded != null ? seeded.nextBoolean() : ThreadLocalRandom.current().nextBoolean();
    }

    public static double randomDouble()
    {
        Random seeded = seededRandom.get();
        return seeded != null ? seeded.nextDouble() : ThreadLocalRandom.current().nextDouble();
    }

    public static <T> void shuffle(List<T> list)
    {
        Random seeded = seededRandom.get();
        if (seeded != null)
        {
            Collections.shuffle(list, seeded);
        }
        else
        {
            Collections.shuffle(list, ThreadLocalRandom.current());
        }
    }
}
