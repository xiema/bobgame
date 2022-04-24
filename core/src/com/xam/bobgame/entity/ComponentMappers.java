package com.xam.bobgame.entity;

import com.badlogic.ashley.core.ComponentMapper;
import com.xam.bobgame.components.GraphicsComponent;
import com.xam.bobgame.components.IdentityComponent;
import com.xam.bobgame.components.PositionComponent;
import com.xam.bobgame.components.VelocityComponent;

public class ComponentMappers {

    public static final ComponentMapper<IdentityComponent> identity = ComponentMapper.getFor(IdentityComponent.class);
    public static final ComponentMapper<PositionComponent> position = ComponentMapper.getFor(PositionComponent.class);
    public static final ComponentMapper<VelocityComponent> velocity = ComponentMapper.getFor(VelocityComponent.class);
    public static final ComponentMapper<GraphicsComponent> graphics = ComponentMapper.getFor(GraphicsComponent.class);
}
