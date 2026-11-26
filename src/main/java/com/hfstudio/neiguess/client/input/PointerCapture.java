package com.hfstudio.neiguess.client.input;

import java.util.BitSet;
import java.util.Objects;

public class PointerCapture<T> {

    private final BitSet buttons = new BitSet();
    private T owner;

    public void press(T owner, int button) {
        if (button < 0) return;
        this.owner = Objects.requireNonNull(owner, "owner");
        buttons.set(button);
    }

    public boolean release(int button) {
        if (button < 0 || !buttons.get(button)) return false;
        buttons.clear(button);
        if (buttons.isEmpty()) owner = null;
        return true;
    }

    public boolean ownedBy(T owner) {
        return this.owner == owner && !buttons.isEmpty();
    }
}
