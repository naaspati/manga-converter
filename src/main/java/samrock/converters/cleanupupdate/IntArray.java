package samrock.converters.cleanupupdate;

import java.util.Arrays;
import java.util.function.IntConsumer;

public class IntArray {
    private final int[] array;

    /**
     * array must be sorted
     * @param array
     * @param length
     */
    public IntArray(int[] array, int length) {
        this.array = Arrays.copyOf(array, length);
    }
    /**
     * array must be sorted
     * @param array
     * @param length
     */
    public IntArray(int[] array) {
        this.array = array;
    }
    public int length() {
        return array.length;
    }
    public int at(int index) {
        return array[index];
    }
    public boolean contains(int value) {
        return array.length != 0 && Arrays.binarySearch(array, value) >= value;
    }
    public int[] toArray() {
        return Arrays.copyOf(array, array.length);
    }
    public void forEach(IntConsumer consumer) {
        for (int i : array) consumer.accept(i);
    }
}
