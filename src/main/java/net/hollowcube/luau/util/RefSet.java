package net.hollowcube.luau.util;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

public class RefSet {
    private final BitSet mark = new BitSet(32);
    private final List<Object> refs = new ArrayList<>(32);

    public int ref(@NotNull Object object) {
        int index = mark.nextClearBit(0);
        mark.set(index);
        refs.add(index, object);
        return index;
    }

    public void unref(int index) {
        mark.clear(index);
        refs.set(index, null);
    }

    public @NotNull Object get(int index) {
        return Objects.requireNonNull(refs.get(index));
    }

}
