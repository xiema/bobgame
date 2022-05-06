package com.xam.bobgame.net;

import com.badlogic.ashley.core.Engine;
import com.xam.bobgame.utils.BitPacker;

public interface NetSerializable {

    int read(BitPacker packer, Engine engine);
}
