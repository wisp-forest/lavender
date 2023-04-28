package io.wispforest.lavender.parsing;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * nom nom
 */
public class ListNibbler<T> {

    private final List<T> delegate;
    private int pointer = 0;

    public ListNibbler(List<T> delegate) {
        this.delegate = delegate;
    }

    public T nibble() {
        return this.pointer < this.delegate.size()
                ? this.delegate.get(this.pointer++)
                : null;
    }

    public void skip(int elements) {
        this.pointer += elements;
        if (this.pointer > this.delegate.size()) throw new NoSuchElementException();
    }

    public T peek() {
        return this.peek(0);
    }

    public T peek(int offset) {
        int index = this.pointer + offset;
        return index >= 0 && index < this.delegate.size()
                ? this.delegate.get(index)
                : null;
    }

    public boolean hasElements() {
        return this.pointer < this.delegate.size();
    }

    public int pointer() {
        return this.pointer;
    }

    public void setPointer(int pointer) {
        this.pointer = pointer;
    }

}
