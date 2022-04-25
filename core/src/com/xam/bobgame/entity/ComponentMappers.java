package com.xam.bobgame.entity;

import com.badlogic.ashley.core.ComponentMapper;
import com.xam.bobgame.components.*;

public class ComponentMappers {

    public static final ComponentMapper<IdentityComponent> identity = ComponentMapper.getFor(IdentityComponent.class);
    public static final ComponentMapper<PhysicsBodyComponent> physicsBody = ComponentMapper.getFor(PhysicsBodyComponent.class);
    public static final ComponentMapper<GraphicsComponent> graphics = ComponentMapper.getFor(GraphicsComponent.class);
}
