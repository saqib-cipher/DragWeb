package glab.dragweb.logic;

import java.util.ArrayList;

public class BlockPalette {
    private String name;
    private int color;
    private ArrayList<BlockBean> blocks;

    public BlockPalette(String name, int color) {
        this(name, color, new ArrayList<>());
    }

    public BlockPalette(String name, int color, ArrayList<BlockBean> blocks) {
        this.name = name;
        this.color = color;
        this.blocks = blocks != null ? blocks : new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public int getColor() {
        return color;
    }

    public ArrayList<BlockBean> getBlocks() {
        return blocks;
    }

    public ArrayList<BlockBean> blocks() {
        return blocks;
    }
}

