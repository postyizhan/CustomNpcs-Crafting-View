package legacyfix;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

/**
 * Java 8-compatible replacement for the pre-Java-8 Collections.sort behavior.
 *
 * <p>Legacy FML sorts LaunchWrapper's actively iterated ArrayList. Java 8's
 * List.sort increments that list's modification count and causes LaunchWrapper
 * to throw ConcurrentModificationException. Sorting a copy and replacing the
 * existing elements through ListIterator.set preserves the old behavior.</p>
 */
public final class Collections {
    private Collections() {
    }

    public static void sort(List list, Comparator comparator) {
        Object[] sorted = list.toArray();
        Arrays.sort(sorted, comparator);

        ListIterator iterator = list.listIterator();
        for (int i = 0; i < sorted.length; i++) {
            iterator.next();
            iterator.set(sorted[i]);
        }
    }
}
