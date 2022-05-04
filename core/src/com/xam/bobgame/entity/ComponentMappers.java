package com.xam.bobgame.entity;

import com.badlogic.ashley.core.ComponentMapper;
import com.xam.bobgame.components.*;

public class ComponentMappers {

    public static final ComponentMapper<IdentityComponent> identity = ComponentMapper.getFor(IdentityComponent.class);
    public static final ComponentMapper<PhysicsBodyComponent> physicsBody = ComponentMapper.getFor(PhysicsBodyComponent.class);
    public static final ComponentMapper<GravitationalFieldComponent> gravFields = ComponentMapper.getFor(GravitationalFieldComponent.class);
    public static final ComponentMapper<GraphicsComponent> graphics = ComponentMapper.getFor(GraphicsComponent.class);
    public static final ComponentMapper<HazardComponent> hazards = ComponentMapper.getFor(HazardComponent.class);
    public static final ComponentMapper<SteerableComponent> steerables = ComponentMapper.getFor(SteerableComponent.class);
    public static final ComponentMapper<AIComponent> ai = ComponentMapper.getFor(AIComponent.class);
}
