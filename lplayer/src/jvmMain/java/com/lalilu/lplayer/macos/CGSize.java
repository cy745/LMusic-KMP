package com.lalilu.lplayer.macos;

import com.sun.jna.Structure;
import org.rococoa.cocoa.CGFloat;

import java.util.Arrays;
import java.util.List;

public class CGSize extends Structure implements Structure.ByValue {
    public CGFloat width;
    public CGFloat height;

    public CGSize() {
    }

    public CGSize(final CGFloat width, final CGFloat height) {
        this.width = width;
        this.height = height;
    }

    public CGFloat getWidth() {
        return width;
    }

    public CGFloat getHeight() {
        return height;
    }

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("width", "height");
    }
}
