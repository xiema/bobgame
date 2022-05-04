package com.xam.bobgame.utils;

import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;

public class SuffixFileHandleResolver implements FileHandleResolver {
    private String suffix;
    private FileHandleResolver baseResolver;

    public SuffixFileHandleResolver(FileHandleResolver baseResolver, String suffix) {
        this.baseResolver = baseResolver;
        this.suffix = suffix;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public FileHandleResolver getBaseResolver() {
        return baseResolver;
    }

    public void setBaseResolver(FileHandleResolver baseResolver) {
        this.baseResolver = baseResolver;
    }

    @Override
    public FileHandle resolve(String fileName) {
        return baseResolver.resolve(fileName + suffix);
    }
}
