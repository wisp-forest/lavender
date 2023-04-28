package io.wispforest.lavender.parsing;

import java.util.List;

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

    public T peek() {
        return this.pointer < this.delegate.size()
                ? this.delegate.get(this.pointer)
                : null;
    }

    public boolean hasElements() {
        return this.pointer >= this.delegate.size();
    }

    public int pointer() {
        return this.pointer;
    }

    public void setPointer(int pointer) {
        this.pointer = pointer;
    }

}
